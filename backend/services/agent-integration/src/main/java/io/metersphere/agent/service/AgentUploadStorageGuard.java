package io.metersphere.agent.service;

import io.metersphere.system.service.CommonFileService;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Reserve the object identity before storage writes, so process death cannot lose cleanup responsibility. */
@Service
public class AgentUploadStorageGuard {
    @Resource private JdbcTemplate jdbc;
    @Resource private PlatformTransactionManager transactions;
    @Resource private CommonFileService files;
    public String reserve(String name) {
        String id=IDGenerator.nextStr();
        var independent=new TransactionTemplate(transactions);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        independent.executeWithoutResult(status->jdbc.update("INSERT INTO agent_upload_storage_guard(file_id,file_name,create_time) VALUES (?,?,?)",id,name,System.currentTimeMillis()));
        // Keep cleanup out until the caller's metadata transaction commits or rolls back.
        jdbc.queryForList("SELECT file_id FROM agent_upload_storage_guard WHERE file_id=? FOR UPDATE",id);
        return id;
    }
    public void published(String id){jdbc.update("DELETE FROM agent_upload_storage_guard WHERE file_id=?",id);}
    @Scheduled(fixedDelay=60000,initialDelay=60000)
    public void cleanup() {
        var ids=jdbc.queryForList("SELECT file_id FROM agent_upload_storage_guard WHERE create_time<? ORDER BY create_time LIMIT 50",String.class,System.currentTimeMillis()-1800000);
        for(String id:ids) {
            try {
                new TransactionTemplate(transactions).executeWithoutResult(status->{
                    var rows=jdbc.queryForList("SELECT file_name FROM agent_upload_storage_guard WHERE file_id=? FOR UPDATE SKIP LOCKED",String.class,id);
                    if(rows.isEmpty())return;
                    Long referenced=jdbc.queryForObject("SELECT COUNT(*) FROM agent_temp_attachment WHERE file_id=?",Long.class,id);
                    if(referenced!=null&&referenced>0){published(id);return;}
                    try{files.deleteExpiredTempUpload(id,rows.getFirst());}
                    catch(Exception failure){throw new IllegalStateException("Temporary storage cleanup failed",failure);}
                    published(id);
                });
            }catch(Exception failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Temporary upload cleanup will retry: {}",id,failure);}
        }
    }
}
