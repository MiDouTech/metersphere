package io.metersphere.functional.service;

import io.metersphere.functional.dto.response.FunctionalCaseImportResponse;
import io.metersphere.functional.request.FunctionalCaseImportRequest;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.zip.ZipInputStream;

/** Tracks the retained synchronous Excel endpoint independently of its business transaction. */
@Service
public class FunctionalLegacyImportService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final String instance=UUID.randomUUID().toString();
    private final Set<String> active=ConcurrentHashMap.newKeySet();
    private final Semaphore capacity=new Semaphore(4);
    private static final ThreadLocal<java.util.function.BiConsumer<String,Integer>> PROGRESS=new ThreadLocal<>();
    public FunctionalLegacyImportService(JdbcTemplate jdbc,PlatformTransactionManager manager) {
        this.jdbc=jdbc;transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(10);
    }
    public static void stage(String stage,int rows) {
        var callback=PROGRESS.get();if(callback!=null)callback.accept(stage,rows);
    }
    public FunctionalCaseImportResponse run(FunctionalCaseImportRequest request,String user,MultipartFile file,Supplier<FunctionalCaseImportResponse> business) {
        if(file==null || file.isEmpty() || file.getSize()>50L*1024*1024)throw new MSException("请选择不超过50MiB的导入文件");
        if(!capacity.tryAcquire())throw new MSException("导入任务较多，请稍后重试，勿重复提交");
        String id=UUID.randomUUID().toString(),trace=org.slf4j.MDC.get("traceId");
        if(trace==null)trace=id;
        String finalTrace=trace;
        try {
            String hash;
            try(var input=file.getInputStream()){hash=DigestUtils.sha256Hex(input);}
            String name=Objects.toString(file.getOriginalFilename(),"import.xlsx").replaceAll("[\\\\/\\p{Cntrl}]","_");
            String finalName=name.substring(0,Math.min(255,name.length()));
            transaction.executeWithoutResult(status->{
                jdbc.queryForList("SELECT id FROM project WHERE id=? FOR UPDATE",String.class,request.getProjectId());
                Long count=jdbc.queryForObject("SELECT COUNT(*) FROM functional_legacy_import_job WHERE project_id=? AND state='RUNNING'",Long.class,request.getProjectId());
                if(count!=null && count>0)throw new MSException("该项目已有旧版导入正在处理，请先查看任务记录");
                long now=System.currentTimeMillis();
                jdbc.update("INSERT INTO functional_legacy_import_job(id,project_id,user_id,instance_id,file_name,file_size,source_hash,state,stage,trace_id,create_time,update_time,heartbeat_at) VALUES (?,?,?,?,?,?,?,'RUNNING','VALIDATING',?,?,?,?)",id,request.getProjectId(),user,instance,finalName,file.getSize(),hash,finalTrace,now,now,now);
            });
            active.add(id);
            PROGRESS.set((phase,rows)->transaction.executeWithoutResult(status->jdbc.update("UPDATE functional_legacy_import_job SET stage=?,processed_rows=?,update_time=?,heartbeat_at=? WHERE id=? AND state='RUNNING'",phase,rows,System.currentTimeMillis(),System.currentTimeMillis(),id)));
            try {
                validateArchive(file);
                stage("PARSING",0);
                var response=business.get();
                // The service proxy's business transaction has committed before returning here.
                try {
                    transaction.executeWithoutResult(status->jdbc.update("UPDATE functional_legacy_import_job SET state=?,stage='FINISHED',success_count=?,fail_count=?,processed_rows=?,finish_time=?,update_time=? WHERE id=?",
                            response.getFailCount()>0?"PARTIAL_FAILED":"SUCCEEDED",response.getSuccessCount(),response.getFailCount(),response.getSuccessCount()+response.getFailCount(),System.currentTimeMillis(),System.currentTimeMillis(),id));
                } catch(Exception trackingFailure) {
                    LoggerFactory.getLogger(getClass()).error("Import committed but final tracking update failed job={},traceId={}",id,finalTrace,trackingFailure);
                }
                response.setJobId(id);return response;
            } catch(Exception failure) {
                try { transaction.executeWithoutResult(status->jdbc.update("UPDATE functional_legacy_import_job SET state='FAILED',error_code='IMPORT_FAILED',finish_time=?,update_time=? WHERE id=?",System.currentTimeMillis(),System.currentTimeMillis(),id)); }
                catch(Exception trackingFailure){failure.addSuppressed(trackingFailure);}
                LoggerFactory.getLogger(getClass()).error("Legacy import failed job={},traceId={}",id,finalTrace,failure);
                throw new MSException("导入未完成，请查看导入记录并使用 traceId 排查："+finalTrace);
            }
        } catch(MSException failure){throw failure;}
        catch(Exception failure){throw new MSException("无法创建导入任务，请稍后重试");}
        finally {PROGRESS.remove();active.remove(id);capacity.release();}
    }
    static void validateArchive(MultipartFile file)throws java.io.IOException {
        String name=Objects.toString(file.getOriginalFilename(),"").toLowerCase(Locale.ROOT);
        if(!name.endsWith(".xlsx"))return;
        try(var zip=new ZipInputStream(file.getInputStream())) {
            int entries=0;long bytes=0;byte[] buffer=new byte[8192];
            while(zip.getNextEntry()!=null) {
                if(++entries>3000)throw new MSException("导入压缩内容条目过多，请拆分文件");
                int n;while((n=zip.read(buffer))!=-1){bytes+=n;if(bytes>200L*1024*1024)throw new MSException("导入文件解压后超过200MiB，请拆分文件");}
            }
        }
    }
    @Scheduled(fixedDelay=30000,initialDelay=30000)
    public void heartbeat() {
        long now=System.currentTimeMillis();
        for(String id:active)jdbc.update("UPDATE functional_legacy_import_job SET heartbeat_at=? WHERE id=? AND instance_id=? AND state='RUNNING'",now,id,instance);
        jdbc.update("UPDATE functional_legacy_import_job SET state='INTERRUPTED',stage='NEEDS_RECONCILIATION',error_code='IMPORT_INTERRUPTED',update_time=? WHERE state='RUNNING' AND heartbeat_at<?",now,now-900000);
    }
    public List<Map<String,Object>> recent(String project,String user) {
        return jdbc.queryForList("SELECT id,file_name AS fileName,state,stage,processed_rows AS processedRows,success_count AS successCount,fail_count AS failCount,trace_id AS traceId,error_code AS errorCode,create_time AS createTime,finish_time AS finishTime FROM functional_legacy_import_job WHERE project_id=? AND user_id=? ORDER BY create_time DESC LIMIT 50",project,user);
    }
}
