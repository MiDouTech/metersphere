package io.metersphere.agent.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.enums.CellExtraTypeEnum;
import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.functional.excel.domain.*;
import io.metersphere.functional.excel.listener.*;
import io.metersphere.functional.request.FunctionalCaseImportRequest;
import io.metersphere.functional.service.*;
import io.metersphere.functional.xmind.parser.XMindCaseParser;
import io.metersphere.project.mapper.ExtBaseProjectVersionMapper;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.domain.AgentToken;
import io.metersphere.system.dto.sdk.SessionUser;
import io.metersphere.system.service.UserLoginService;
import jakarta.annotation.Resource;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/** Frozen precheck and resumable per-logical-case imports shared by web and MCP. */
@Service
public class AgentCaseImportService {
    @Resource private JdbcTemplate jdbc;
    @Resource private AgentBusinessAccess access;
    @Resource private FunctionalCaseFileService files;
    @Resource private FunctionalCaseService cases;
    @Resource private FunctionalCaseModuleService modules;
    @Resource private ExtBaseProjectVersionMapper versions;
    @Resource private UserLoginService users;
    @Resource private PlatformTransactionManager transactionManager;
    private static final String PERMISSION="FUNCTIONAL_CASE:READ+IMPORT";
    private static final int MAX_ITEMS=10000;
    private record Item(String action,FunctionalCaseExcelData data,String target,Long version,String message) { }

    private final java.util.concurrent.Semaphore precheckCapacity=new java.util.concurrent.Semaphore(4);
    public Map<String,Object> precheck(FunctionalCaseImportRequest request,MultipartFile file,String requestId,String user,String tokenId) {
        if(!precheckCapacity.tryAcquire())throw new MSException("IMPORT_RATE_LIMIT");
        try{return precheckInternal(request,file,requestId,user,tokenId);}
        finally{precheckCapacity.release();}
    }
    private Map<String,Object> precheckInternal(FunctionalCaseImportRequest request,MultipartFile file,String requestId,String user,String tokenId) {
        authorize(request.getProjectId(),user,tokenId);
        if(requestId==null||!requestId.matches("[A-Za-z0-9_-]{1,64}"))throw new MSException("导入请求标识无效");
        if(file==null||file.isEmpty()||file.getSize()>50L*1024*1024)throw new MSException("请选择不超过50MiB的导入文件");
        String name=Objects.toString(file.getOriginalFilename(),"");
        if(name.length()>255||!name.toLowerCase(Locale.ROOT).matches(".*\\.(xlsx|xls|xmind)"))throw new MSException("仅支持Excel或XMind文件");
        byte[] bytes;
        try { bytes=file.getBytes(); }catch(Exception failure){throw new MSException("读取导入文件失败，请重试");}
        if(bytes.length>50*1024*1024)throw new MSException("导入文件不能超过50MiB");
        String sourceHash=DigestUtils.sha256Hex(bytes);
        String requestHash=DigestUtils.sha256Hex(JSON.toJSONString(request)+":"+sourceHash+":"+name);
        var existing=jdbc.queryForList("SELECT id,request_hash FROM functional_import_job WHERE user_id=? AND token_id=? AND request_id=?",user,tokenId,requestId);
        if(!existing.isEmpty()) {
            if(!requestHash.equals(existing.getFirst().get("request_hash")))throw new MSException("相同请求标识对应的导入内容已变化，请重新预检");
            return get((String)existing.getFirst().get("id"),user,tokenId,1,10);
        }
        request.setImportLocale(org.springframework.context.i18n.LocaleContextHolder.getLocale().toLanguageTag());
        validateArchive(bytes,name);
        if(request.getVersionId()==null||request.getVersionId().isBlank())request.setVersionId(versions.getDefaultVersion(request.getProjectId()));
        validateDestination(request);
        var custom=files.getCustomFields(request.getProjectId());
        String templateHash=DigestUtils.sha256Hex(JSON.toJSONString(custom));
        var session=SessionUser.fromUser(users.getUserDTO(user),"");
        List<Item> prepared=new ArrayList<>();
        long deadline=System.nanoTime()+30_000_000_000L;
        java.util.function.BiConsumer<String,FunctionalCaseExcelData> collect=(action,data)->{
            if(System.nanoTime()>deadline||prepared.size()>=MAX_ITEMS)throw new MSException("导入超过10000条或解析时间限制，请拆分文件");
            String target="UPDATE".equals(action)?data.getNum():null;
            Long revision=null;
            if(target!=null) {
                var found=jdbc.queryForList("SELECT update_time FROM functional_case WHERE id=? AND project_id=? AND deleted=0",target,request.getProjectId());
                if(found.size()!=1)throw new MSException("覆盖目标已变化，请重新预检");
                revision=((Number)found.getFirst().get("update_time")).longValue();
            }
            prepared.add(new Item(action,JSON.parseObject(JSON.toJSONString(data),FunctionalCaseExcelData.class),target,revision,null));
        };
        try {
            if(name.toLowerCase(Locale.ROOT).endsWith(".xmind")) {
                var parser=new XMindCaseParser(request,custom,session,0L);
                var errors=parser.parse(file);
                parser.getList().forEach(item->collect.accept("CREATE",item));
                parser.getUpdateList().forEach(item->collect.accept("UPDATE",item));
                parser.getCheckSuccessList().forEach(item->collect.accept("SKIPPED",item));
                for(var error:errors)prepared.add(new Item("INVALID",null,null,null,safeValidation(error.getErrMsg())));
                parser.clear();
            } else {
                List<Integer> sheets;
                try(var reader=EasyExcel.read(file.getInputStream()).build()) {
                    sheets=reader.excelExecutor().sheetList().stream().map(sheet->sheet.getSheetNo()).toList();
                }
                if(sheets.size()>100)throw new MSException("工作表超过100个，请拆分文件");
                for(int sheet:sheets) {
                    Set<ExcelMergeInfo> merges=new TreeSet<>();
                    EasyExcel.read(file.getInputStream(),null,new FunctionalCasePretreatmentListener(merges)).extraRead(CellExtraTypeEnum.MERGE).sheet(sheet).doRead();
                    var parser=new FunctionalCaseImportEventListener(request,new FunctionalCaseExcelDataFactory().getExcelDataByLocal(),custom,merges,session,0L);
                    parser.collectPreparedItems(collect);
                    EasyExcel.read(file.getInputStream(),parser).sheet(sheet).doRead();
                    for(var error:parser.getErrList())prepared.add(new Item("INVALID",null,null,null,safeValidation(error.getErrMsg())));
                    if(System.nanoTime()>deadline||prepared.size()>MAX_ITEMS)throw new MSException("导入超过10000条或解析时间限制，请拆分文件");
                }
            }
        } catch(MSException failure){throw failure;}
        catch(Exception failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Import precheck failed",failure);throw new MSException("导入文件解析失败，请使用当前模板检查文件格式");}
        if(prepared.isEmpty()||prepared.size()>MAX_ITEMS)throw new MSException("未找到可处理的用例，或超过10000条限制");
        if(prepared.stream().filter(item->item.target()!=null).map(Item::target).distinct().count()!=prepared.stream().filter(item->item.target()!=null).count())throw new MSException("文件多次覆盖同一用例，请合并后重新预检");
        String id=UUID.randomUUID().toString();long now=System.currentTimeMillis();
        String persistedId=new TransactionTemplate(transactionManager).execute(status->{
            // Serialize quota and request key across application instances.
            jdbc.queryForList("SELECT id FROM user WHERE id=? FOR UPDATE",user);
            var replay=jdbc.queryForList("SELECT id,request_hash FROM functional_import_job WHERE user_id=? AND token_id=? AND request_id=?",user,tokenId,requestId);
            if(!replay.isEmpty()) {
                if(!requestHash.equals(replay.getFirst().get("request_hash")))throw new MSException("相同请求标识对应的导入内容已变化，请重新预检");
                return (String)replay.getFirst().get("id");
            }
            Long active=jdbc.queryForObject("SELECT COUNT(*) FROM functional_import_job WHERE user_id=? AND expires_at>? AND state IN ('PRECHECKED','QUEUED','RUNNING')",Long.class,user,now);
            if(active!=null&&active>=5)throw new MSException("最多保留5个待处理导入，请先完成或停止现有任务");
            jdbc.update("INSERT INTO functional_import_job(id,project_id,user_id,token_id,request_id,request_hash,request_json,template_hash,source_hash,source_name,source_bytes,create_time,update_time,expires_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,request.getProjectId(),user,tokenId,requestId,requestHash,JSON.toJSONString(request),templateHash,sourceHash,name,bytes,now,now,now+86400000);
            int position=0;
            for(var item:prepared)jdbc.update("INSERT INTO functional_import_item(job_id,position,action,payload,target_id,expected_version,state,message) VALUES (?,?,?,?,?,?,?,?)",id,++position,item.action(),item.data()==null?null:JSON.toJSONString(item.data()),item.target(),item.version(),Set.of("INVALID","SKIPPED").contains(item.action())?item.action():"PENDING",item.message());
            return id;
        });
        return get(persistedId,user,tokenId,1,10);
    }

    public Map<String,Object> get(String id,String user,String token,int current,int size) {
        var row=owned(id,user,token,false);
        if(current<1||size<1||size>100)throw new MSException("分页参数无效");
        var counts=jdbc.queryForList("SELECT state,COUNT(*) AS total FROM functional_import_item WHERE job_id=? GROUP BY state",id);
        var items=jdbc.queryForList("SELECT position,action,target_id AS caseId,state,message FROM functional_import_item WHERE job_id=? ORDER BY position LIMIT ? OFFSET ?",id,size,(current-1L)*size);
        long total=counts.stream().mapToLong(item->((Number)item.get("total")).longValue()).sum();
        return Map.of("jobId",id,"state",row.get("state"),"fileName",row.get("source_name"),"total",total,"counts",counts,"items",items,"current",current,"pageSize",size,"expiresAt",row.get("expires_at"));
    }

    public Map<String,Object> action(String id,String action,String user,String token) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status->{
            var row=owned(id,user,token,true);String state=(String)row.get("state");
            if("stop".equals(action)) {
                if(Set.of("PRECHECKED","QUEUED","RUNNING").contains(state))jdbc.update("UPDATE functional_import_job SET state='STOPPED',update_time=? WHERE id=?",System.currentTimeMillis(),id);
            } else if("commit".equals(action)) {
                if("PRECHECKED".equals(state))jdbc.update("UPDATE functional_import_job SET state='QUEUED',update_time=? WHERE id=?",System.currentTimeMillis(),id);
                else if(!Set.of("QUEUED","RUNNING","COMPLETED","PARTIAL").contains(state))throw new MSException("当前导入不可提交");
            } else if("retry".equals(action)) {
                if(Set.of("STOPPED","PARTIAL","FAILED").contains(state)) {
                    jdbc.update("UPDATE functional_import_item SET state='PENDING',message=NULL WHERE job_id=? AND state='FAILED'",id);
                    jdbc.update("UPDATE functional_import_job SET state='QUEUED',update_time=? WHERE id=?",System.currentTimeMillis(),id);
                } else if(!Set.of("QUEUED","RUNNING").contains(state))throw new MSException("当前导入没有可重试的条目");
            } else throw new MSException("导入操作无效");
        });
        return get(id,user,token,1,10);
    }

    @Scheduled(fixedDelay=1000,initialDelay=30000)
    public void drain() {
        var jobs=jdbc.queryForList("SELECT id FROM functional_import_job WHERE state IN ('QUEUED','RUNNING') ORDER BY update_time LIMIT 2",String.class);
        for(String id:jobs)for(int i=0;i<20;i++) {
            java.util.concurrent.atomic.AtomicInteger attempted=new java.util.concurrent.atomic.AtomicInteger(-1);
            try {
                Integer position=new TransactionTemplate(transactionManager).execute(status->processOne(id,attempted::set));
                if(position==null)break;
            } catch(Exception failure) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Import item failed for job {}",id,failure);
                // Business writes rolled back with the item transaction. Preserve a safe report and continue other items.
                new TransactionTemplate(transactionManager).executeWithoutResult(status->{
                    jdbc.queryForList("SELECT id FROM functional_import_job WHERE id=? FOR UPDATE",id);
                    if(attempted.get()>=0)jdbc.update("UPDATE functional_import_item SET state='FAILED',message='写入失败或授权、模板、目标版本已变化，请核对后重试' WHERE job_id=? AND position=? AND state='PENDING'",id,attempted.get());
                    else jdbc.update("UPDATE functional_import_job SET state='FAILED',update_time=? WHERE id=? AND state IN ('QUEUED','RUNNING')",System.currentTimeMillis(),id);
                });
                break;
            } finally { AgentTokenContext.clear(); }
        }
    }

    Integer processOne(String id) {
        return processOne(id,ignored->{ });
    }
    private Integer processOne(String id,java.util.function.IntConsumer attempted) {
        var jobs=jdbc.queryForList("SELECT * FROM functional_import_job WHERE id=? AND state IN ('QUEUED','RUNNING') FOR UPDATE SKIP LOCKED",id);
        if(jobs.isEmpty())return null;
        var job=jobs.getFirst();
        if(((Number)job.get("expires_at")).longValue()<=System.currentTimeMillis()){jdbc.update("UPDATE functional_import_job SET state='EXPIRED' WHERE id=?",id);return null;}
        authorize((String)job.get("project_id"),(String)job.get("user_id"),(String)job.get("token_id"));
        var pending=jdbc.queryForList("SELECT * FROM functional_import_item WHERE job_id=? AND state='PENDING' ORDER BY position LIMIT 1 FOR UPDATE",id);
        if(pending.isEmpty()) {
            long failed=jdbc.queryForObject("SELECT COUNT(*) FROM functional_import_item WHERE job_id=? AND state IN ('FAILED','INVALID')",Long.class,id);
            jdbc.update("UPDATE functional_import_job SET state=?,update_time=? WHERE id=?",failed>0?"PARTIAL":"COMPLETED",System.currentTimeMillis(),id);
            return null;
        }
        var item=pending.getFirst();int position=((Number)item.get("position")).intValue();
        attempted.accept(position);
        var request=JSON.parseObject((String)job.get("request_json"),FunctionalCaseImportRequest.class);
        var previousLocale=org.springframework.context.i18n.LocaleContextHolder.getLocaleContext();
        try {
        if(request.getImportLocale()!=null)org.springframework.context.i18n.LocaleContextHolder.setLocale(Locale.forLanguageTag(request.getImportLocale()));
        validateDestination(request);
        var custom=files.getCustomFields(request.getProjectId());
        if(!job.get("template_hash").equals(DigestUtils.sha256Hex(JSON.toJSONString(custom))))throw new MSException("导入模板已变化");
        if(item.get("target_id")!=null) {
            var targets=jdbc.queryForList("SELECT update_time FROM functional_case WHERE id=? AND project_id=? AND deleted=0 FOR UPDATE",item.get("target_id"),request.getProjectId());
            if(targets.size()!=1||((Number)targets.getFirst().get("update_time")).longValue()!=((Number)item.get("expected_version")).longValue())throw new MSException("覆盖目标已变化");
        }
        var data=JSON.parseObject((String)item.get("payload"),FunctionalCaseExcelData.class);
        var session=SessionUser.fromUser(users.getUserDTO((String)job.get("user_id")),"");
        session.setLastProjectId(request.getProjectId());
        session.setLastOrganizationId(jdbc.queryForObject("SELECT organization_id FROM project WHERE id=?",String.class,request.getProjectId()));
        var customMap=custom.stream().collect(Collectors.toMap(field->field.getFieldName(),field->field));
        if("UPDATE".equals(item.get("action")))cases.updateImportData(List.of(data),request,modules.getTree(request.getProjectId()),customMap,new HashMap<>(),session);
        else cases.saveImportData(List.of(data),request,modules.getTree(request.getProjectId()),customMap,new HashMap<>(),session,new AtomicLong(cases.getNextOrder(request.getProjectId())));
        jdbc.update("UPDATE functional_import_item SET state='SUCCESS',message=NULL,target_id=COALESCE(?,target_id) WHERE job_id=? AND position=?",data.getImportedCaseId(),id,position);
        jdbc.update("UPDATE functional_import_job SET state='RUNNING',update_time=? WHERE id=?",System.currentTimeMillis(),id);
        return position;
        } finally {
            org.springframework.context.i18n.LocaleContextHolder.setLocaleContext(previousLocale);
        }
    }

    private Map<String,Object> owned(String id,String user,String token,boolean lock) {
        var rows=jdbc.queryForList("SELECT * FROM functional_import_job WHERE id=? AND user_id=? AND token_id=?"+(lock?" FOR UPDATE":""),id,user,token);
        if(rows.size()!=1)throw AgentBusinessAccess.denied();
        var row=rows.getFirst();authorize((String)row.get("project_id"),user,token);
        if(lock&&((Number)row.get("expires_at")).longValue()<=System.currentTimeMillis())throw new MSException("导入预检已过期，请重新上传");
        return row;
    }
    private void authorize(String project,String user,String tokenId) {
        if("WEB".equals(tokenId)) {
            Long active=jdbc.queryForObject("SELECT COUNT(*) FROM project WHERE id=? AND deleted=0 AND enable=1",Long.class,project);
            if(active==null||active!=1||!access.userHasPermission(user,project,PERMISSION))throw AgentBusinessAccess.denied();
        } else {
            var tokens=jdbc.query("SELECT * FROM agent_token WHERE id=? AND user_id=?",BeanPropertyRowMapper.newInstance(AgentToken.class),tokenId,user);
            if(tokens.size()!=1)throw AgentBusinessAccess.denied();
            var previous=AgentTokenContext.get();
            try {AgentTokenContext.set(tokens.getFirst());access.project(project,PERMISSION,true,AgentTokenScope.CASE_IMPORT_V2);}
            finally {if(previous==null)AgentTokenContext.clear();else AgentTokenContext.set(previous);}
        }
    }
    @Scheduled(fixedDelay=60000,initialDelay=60000)
    public void expire() {
        long now=System.currentTimeMillis();
        jdbc.update("UPDATE functional_import_job SET state='EXPIRED',update_time=? WHERE expires_at<=? AND state IN ('PRECHECKED','QUEUED','RUNNING')",now,now);
        jdbc.update("UPDATE functional_import_job SET source_bytes=X'' WHERE expires_at<=? AND OCTET_LENGTH(source_bytes)>0",now);
    }
    private void validateDestination(FunctionalCaseImportRequest request) {
        String module=request.getModuleId();
        if(module!=null&&!module.isBlank()&&!Set.of("all","root","NONE").contains(module))access.module(request.getProjectId(),module,false);
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM project_version WHERE id=? AND project_id=?",Long.class,request.getVersionId(),request.getProjectId());
        if(count==null||count!=1)throw new MSException("导入版本不存在，请重新选择");
    }
    private static String safeValidation(String message) {
        if(message==null)return "该条用例格式不符合当前模板";
        return message.length()>900?message.substring(0,900):message;
    }
    private void validateArchive(byte[] bytes,String name) {
        if(name.toLowerCase(Locale.ROOT).endsWith(".xls"))return;
        long expanded=0;int entries=0;
        try(var zip=new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;byte[] buffer=new byte[8192];
            while((entry=zip.getNextEntry())!=null) {
                if(++entries>10000||entry.getName().toLowerCase(Locale.ROOT).contains("vbaproject"))throw new MSException("文件包含宏或过多压缩条目");
                int read;while((read=zip.read(buffer))!=-1) {
                    expanded+=read;
                    if(expanded>200L*1024*1024||expanded>Math.max(1024*1024L,bytes.length*100L))throw new MSException("文件解压大小超限，请拆分文件");
                }
            }
            if(entries==0)throw new MSException("导入文件格式无效");
        }catch(MSException failure){throw failure;}catch(Exception failure){throw new MSException("压缩文件损坏，请重新导出后上传");}
    }
}
