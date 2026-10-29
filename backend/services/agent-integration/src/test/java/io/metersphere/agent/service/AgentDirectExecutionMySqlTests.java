package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentCaseDTO;
import io.metersphere.agent.dto.AgentCaseStepDTO;
import io.metersphere.agent.quality.GlobalQualityGate;
import io.metersphere.agent.quality.QualityPolicyValidator;
import io.metersphere.agent.security.AgentFactCipher;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.functional.service.FunctionalCaseEvidenceStore;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.AgentToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named="quality.mysql",matches="true")
class AgentDirectExecutionMySqlTests {
    @Test void realTransactionsGatePublishDownloadRollbackAndReplay() throws Exception {
        var ds=new SingleConnectionDataSource("jdbc:mysql://127.0.0.1:13326/quality_verify?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only",true);
        try {
            var jdbc=new JdbcTemplate(ds);
            try(var stream=getClass().getResourceAsStream("/migration/3.7.2/ddl/V3.7.2_102__personal_execution_evidence.sql")) {
                assertNotNull(stream);
                for(String sql:new String(stream.readAllBytes(),StandardCharsets.UTF_8).split(";"))
                    if(!sql.isBlank())jdbc.execute(sql.replace("CREATE TABLE","CREATE TEMPORARY TABLE"));
            }
            try(var stream=getClass().getResourceAsStream("/migration/3.7.2/ddl/V3.7.2_103__personal_evidence_review.sql")) {
                assertNotNull(stream);
                for(String sql:new String(stream.readAllBytes(),StandardCharsets.UTF_8).split(";"))
                    if(!sql.isBlank())jdbc.execute(sql.replace("CREATE TABLE","CREATE TEMPORARY TABLE"));
            }
            try(var stream=getClass().getResourceAsStream("/migration/3.7.2/ddl/V3.7.2_131__case_scoped_execution_evidence.sql")) {
                assertNotNull(stream);
                for(String sql:new String(stream.readAllBytes(),StandardCharsets.UTF_8).split(";"))
                    if(!sql.isBlank())jdbc.execute(sql.replace("CREATE TABLE","CREATE TEMPORARY TABLE"));
            }
            jdbc.execute("CREATE TEMPORARY TABLE functional_import_job(request_id VARCHAR(64) NOT NULL)");
            try(var stream=getClass().getResourceAsStream("/migration/3.7.2/ddl/V3.7.2_132__mcp_import_request_identity.sql")) {
                assertNotNull(stream);jdbc.execute(new String(stream.readAllBytes(),StandardCharsets.UTF_8));
            }
            jdbc.update("INSERT INTO functional_import_job VALUES (?)","a".repeat(128));
            assertEquals(128,jdbc.queryForObject("SELECT LENGTH(request_id) FROM functional_import_job",Integer.class));
            jdbc.execute("CREATE TEMPORARY TABLE test_plan(id VARCHAR(64) PRIMARY KEY,status VARCHAR(32),project_id VARCHAR(64))");
            jdbc.execute("CREATE TEMPORARY TABLE test_plan_functional_case(functional_case_id VARCHAR(64),test_plan_id VARCHAR(64),last_exec_result VARCHAR(32),last_exec_time BIGINT,execute_user VARCHAR(64))");
            jdbc.execute("CREATE TEMPORARY TABLE agent_token(id VARCHAR(64) PRIMARY KEY)");
            jdbc.update("INSERT INTO agent_token VALUES ('t')");
            jdbc.execute("CREATE TEMPORARY TABLE functional_case(id VARCHAR(64) PRIMARY KEY,project_id VARCHAR(64),deleted BOOLEAN,last_execute_result VARCHAR(32),last_execute_user VARCHAR(64),last_execute_time BIGINT,update_time BIGINT)");
            jdbc.update("INSERT INTO functional_case VALUES ('c','p',0,'PENDING',NULL,NULL,1)");
            jdbc.execute("CREATE TEMPORARY TABLE functional_case_attachment(id VARCHAR(64) PRIMARY KEY,case_id VARCHAR(64),file_id VARCHAR(64),file_name VARCHAR(255),size BIGINT,local BOOLEAN,create_user VARCHAR(64),create_time BIGINT,file_source VARCHAR(32))");
            jdbc.execute("CREATE TEMPORARY TABLE agent_idempotency_record(id VARCHAR(64) PRIMARY KEY,token_id VARCHAR(64),tool_name VARCHAR(128),request_id VARCHAR(128),request_hash CHAR(64),response_json MEDIUMTEXT,create_time BIGINT,status VARCHAR(32),hash_version VARCHAR(32),completed_time BIGINT,UNIQUE KEY(token_id,tool_name,request_id))");
            jdbc.execute("CREATE TEMPORARY TABLE agent_attachment_upload_intent(request_id VARCHAR(128) NOT NULL)");
            try(var stream=getClass().getResourceAsStream("/migration/3.7.2/ddl/V3.7.2_133__mcp_request_key_comparison.sql")) {
                assertNotNull(stream);
                for(String sql:new String(stream.readAllBytes(),StandardCharsets.UTF_8).split(";"))if(!sql.isBlank())jdbc.execute(sql);
            }
            jdbc.execute("CREATE TEMPORARY TABLE execution_quality_policy_global_state(scope VARCHAR(32),current_policy_id VARCHAR(64))");
            jdbc.execute("CREATE TEMPORARY TABLE execution_quality_policy_global(id VARCHAR(64),version_no INT,content_hash CHAR(64),rules_json TEXT,status VARCHAR(32))");
            var validated=new QualityPolicyValidator().validate(AgentDirectExecutionTests.POLICY);
            assertTrue(validated.valid());
            jdbc.update("INSERT INTO execution_quality_policy_global_state VALUES ('GLOBAL','policy')");
            jdbc.update("INSERT INTO execution_quality_policy_global VALUES ('policy',1,?,?,'PUBLISHED')",validated.contentHash(),validated.normalizedJson());
            var token=new AgentToken();token.setId("t");token.setUserId("u");AgentTokenContext.set(token);
            var source=new AgentCaseDTO();source.setCaseId("c");source.setProjectId("p");source.setUpdateTime(1L);
            var step=new AgentCaseStepDTO();step.setNum(1);step.setDesc("open");step.setExpected("visible");source.setSteps(List.of(step));
            var detail=new io.metersphere.functional.dto.FunctionalCaseDetailDTO();detail.setId("c");detail.setProjectId("p");detail.setName("case");detail.setCaseEditType("STEP");
            detail.setSteps("[{\"num\":1,\"desc\":\"open\",\"result\":\"visible\"}]");
            var definitions=spy(new io.metersphere.functional.service.FunctionalCaseService());
            ReflectionTestUtils.setField(definitions,"caseLifecycleJdbc",jdbc);
            for (String migration : List.of("119__functional_import_effect_outbox", "127__review_recount_queue")) {
                try (var stream=getClass().getResourceAsStream("/migration/3.7.2/ddl/V3.7.2_"+migration+".sql")) {
                    assertNotNull(stream);
                    for (String sql:new String(stream.readAllBytes(),StandardCharsets.UTF_8).split(";"))
                        if (!sql.isBlank()) jdbc.execute(sql.replace("CREATE TABLE", "CREATE TEMPORARY TABLE"));
                }
            }
            jdbc.execute("CREATE TEMPORARY TABLE case_review_functional_case(review_id VARCHAR(64),case_id VARCHAR(64))");
            jdbc.update("INSERT INTO case_review_functional_case VALUES ('review','c')");
            var manager=new DataSourceTransactionManager(ds);
            var recount=new io.metersphere.functional.service.FunctionalReviewRecountService(jdbc,manager);
            var effects=new io.metersphere.functional.service.FunctionalImportEffectOutbox(jdbc,manager,null,definitions,null,null);
            ReflectionTestUtils.setField(effects,"reviewRecount",recount);
            ReflectionTestUtils.setField(definitions,"importEffectOutbox",effects);
            doReturn(Map.of("c",detail)).when(definitions).getAgentCaseDetails(List.of("c"),"p",true);
            var search=mock(AgentFunctionalCaseSearchService.class);when(search.getById("c",true,null)).thenReturn(source);
            var service=new AgentDirectExecutionService();
            ReflectionTestUtils.setField(service,"jdbc",jdbc);
            ReflectionTestUtils.setField(service,"access",mock(AgentBusinessAccess.class));
            ReflectionTestUtils.setField(service,"cases",search);
            ReflectionTestUtils.setField(service,"definitions",definitions);
            ReflectionTestUtils.setField(service,"schemaMapper",new io.metersphere.agent.mapper.AgentCaseSchemaMapper());
            ReflectionTestUtils.setField(service,"operations",new AgentOperationExecutor(jdbc,new DataSourceTransactionManager(ds)));
            ReflectionTestUtils.setField(service,"gate",new GlobalQualityGate(jdbc,null,new QualityPolicyValidator()));
            ReflectionTestUtils.setField(service,"cipher",new AgentFactCipher());
            var base=new LinkedHashMap<String,Object>(Map.of("projectId","p","caseId","c"));
            var contract=service.contract(base);base.put("contentHash",contract.get("contentHash"));
            var upload=new LinkedHashMap<>(base);upload.putAll(Map.of("stepNum",1,"purpose","BEFORE_STEP","fileName","screen.png","contentType","image/png","base64",AgentDirectExecutionTests.png(),"requestId","before"));
            String before=(String)service.upload(upload).get("evidenceId");
            assertEquals(before,service.upload(upload).get("evidenceId"));
            upload.put("purpose","AFTER_STEP");upload.put("requestId","after");
            String after=(String)service.upload(upload).get("evidenceId");
            var submit=new LinkedHashMap<>(base);submit.put("policyHash",contract.get("policyHash"));submit.put("requestId","result");
            submit.put("steps",List.of(Map.of("stepNum",1,"actual","observed with different wording","result","SUCCESS")));
            assertEquals("QUALITY_CASE_EVIDENCE_REQUIRED",assertThrows(MSException.class,()->service.submit(submit)).getMessage());
            assertEquals("PENDING",jdbc.queryForObject("SELECT last_execute_result FROM functional_case",String.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM functional_case_attachment",Integer.class));
            var store=new FunctionalCaseEvidenceStore();ReflectionTestUtils.setField(store,"jdbc",jdbc);
            assertNull(store.read("p","c",before));
            submit.put("steps",List.of(Map.of("stepNum",1,"actual","observed with different wording","result","SUCCESS","evidenceIds",List.of(before,after))));
            submit.put("evidenceIds",List.of(before,after));
            token.setId("other-token");assertThrows(MSException.class,()->service.submit(submit));token.setId("t");
            detail.setName("changed definition");assertEquals("VERSION_CONFLICT",assertThrows(MSException.class,()->service.submit(submit)).getMessage());detail.setName("case");
            jdbc.execute("ALTER TABLE functional_case_attachment ADD UNIQUE KEY forced_failure(file_name)");
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->service.submit(submit));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM agent_case_submission",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM functional_case_attachment",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM agent_case_evidence WHERE submission_id IS NOT NULL",Integer.class));
            jdbc.execute("ALTER TABLE functional_case_attachment DROP INDEX forced_failure");
            jdbc.execute("ALTER TABLE functional_import_effect_outbox MODIFY payload VARCHAR(1) NOT NULL");
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->service.submit(submit));
            assertEquals("PENDING",jdbc.queryForObject("SELECT last_execute_result FROM functional_case",String.class));
            assertEquals(1L,jdbc.queryForObject("SELECT update_time FROM functional_case",Long.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM agent_case_submission",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM functional_case_attachment",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM functional_review_recount",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM functional_import_effect_outbox",Integer.class));
            jdbc.execute("ALTER TABLE functional_import_effect_outbox MODIFY payload MEDIUMTEXT NOT NULL");
            var result=service.submit(submit);
            assertEquals("SUCCESS",result.get("result"));
            assertEquals(result.get("submissionId"),service.submit(submit).get("submissionId"));
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM functional_case_attachment",Integer.class));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM agent_case_submission",Integer.class));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM functional_import_effect_outbox",Integer.class));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM functional_review_recount",Integer.class));
            assertArrayEquals(Base64.getDecoder().decode(AgentDirectExecutionTests.png()),store.read("p","c",before));
            assertNull(store.read("other-project","c",before));
            assertEquals("SUCCESS",service.report(Map.of("projectId","p","caseId","c","submissionId",result.get("submissionId"))).get("result"));
            submit.put("requestId","reuse-consumed");assertThrows(MSException.class,()->service.submit(submit));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM agent_case_submission",Integer.class));
            // Structure-only publication needs no content-review service; retain repeats/extras.
            upload.put("requestId","second-before");upload.put("purpose","BEFORE_STEP");
            String secondBefore=(String)service.upload(upload).get("evidenceId");
            upload.put("requestId","second-after");upload.put("purpose","AFTER_STEP");
            String secondAfter=(String)service.upload(upload).get("evidenceId");
            upload.put("stepNum",2);upload.put("requestId","extra-before");upload.put("purpose","BEFORE_STEP");
            String extraBefore=(String)service.upload(upload).get("evidenceId");
            upload.put("requestId","extra-after");upload.put("purpose","AFTER_STEP");
            String extraAfter=(String)service.upload(upload).get("evidenceId");
            var original=Map.of("stepNum",1,"actual","visible","result","SUCCESS","evidenceIds",List.of(secondBefore,secondAfter));
            var extra=Map.of("stepNum",2,"actual","additional observation","result","ERROR","evidenceIds",List.of(extraBefore,extraAfter));
            submit.put("evidenceIds",List.of(secondBefore,secondAfter,extraBefore,extraAfter));
            submit.put("requestId","missing-original");submit.put("steps",List.of(extra));
            assertEquals("QUALITY_FINAL_FACTS_INCOMPLETE",assertThrows(MSException.class,()->service.submit(submit)).getMessage());
            submit.put("requestId","invalid-status");submit.put("steps",List.of(Map.of("stepNum",1,"actual","visible","result","PASS","evidenceIds",List.of(secondBefore,secondAfter))));
            assertEquals("QUALITY_ASSERTION_INVALID",assertThrows(MSException.class,()->service.submit(submit)).getMessage());
            submit.put("requestId","repeat-extra");
            submit.put("steps",List.of(extra,original,original));
            var second=service.submit(submit);
            assertEquals("ACCEPTED",second.get("gateStatus"));
            assertEquals("PUBLISHED",second.get("publishStatus"));
            assertEquals("ERROR",second.get("result"));
            assertEquals("STRUCTURE_ONLY_V1",second.get("validationMode"));
            var secondReport=service.report(Map.of("projectId","p","caseId","c","submissionId",second.get("submissionId")));
            assertEquals("AGENT_REPORTED",secondReport.get("resultSource"));
            assertEquals(3,((com.fasterxml.jackson.databind.JsonNode)secondReport.get("facts")).path("steps").size());
            assertEquals(second.get("submissionId"),service.submit(submit).get("submissionId"));
            assertEquals(6,jdbc.queryForObject("SELECT COUNT(*) FROM functional_case_attachment",Integer.class));
            assertEquals("ERROR",jdbc.queryForObject("SELECT last_execute_result FROM functional_case",String.class));
            upload.put("stepNum",1);upload.put("requestId","repeat-only-before");upload.put("purpose","BEFORE_STEP");
            String repeatBefore=(String)service.upload(upload).get("evidenceId");
            upload.put("requestId","repeat-only-after");upload.put("purpose","AFTER_STEP");
            String repeatAfter=(String)service.upload(upload).get("evidenceId");
            submit.put("requestId","repeat-only");
            submit.put("evidenceIds",List.of(repeatBefore,repeatAfter));
            submit.put("steps",List.of(
                    Map.of("stepNum",1,"actual","visible","result","ERROR","evidenceIds",List.of(repeatBefore,repeatAfter)),
                    Map.of("stepNum",1,"actual","visible","result","SUCCESS","evidenceIds",List.of(repeatBefore,repeatAfter))));
            assertEquals("ERROR",service.submit(submit).get("result"));
            assertEquals(8,jdbc.queryForObject("SELECT COUNT(*) FROM functional_case_attachment",Integer.class));
            upload.put("requestId","expired-pending");service.upload(upload);
            jdbc.update("UPDATE agent_case_evidence SET expires_at=0");
            service.expirePendingEvidence();
            assertEquals(8,jdbc.queryForObject("SELECT COUNT(*) FROM agent_case_evidence",Integer.class));
            assertNotNull(store.read("p","c",before));
            jdbc.update("INSERT INTO test_plan VALUES ('active','UNDERWAY','p'),('archived','ARCHIVED','p'),('foreign-active','UNDERWAY','other-project'),('foreign-archived','ARCHIVED','other-project')");
            jdbc.update("INSERT INTO test_plan_functional_case VALUES ('c','active','SUCCESS',1,'u'),('c','archived','SUCCESS',1,'u'),('c','foreign-active','SUCCESS',1,'u'),('c','foreign-archived','SUCCESS',1,'u'),('unrelated-case','foreign-active','SUCCESS',1,'u')");
            var hundred=new ArrayList<Map<String,Object>>();var outcomes=new ArrayList<Map<String,Object>>();
            for(int n=1;n<=100;n++){hundred.add(Map.of("num",n,"desc","operation "+n,"result","expected "+n));outcomes.add(Map.of("stepNum",n,"actual","observed "+n,"result",n==100?"ERROR":"SUCCESS"));}
            detail.setSteps(io.metersphere.sdk.util.JSON.toJSONString(hundred));
            var minimal=new LinkedHashMap<String,Object>(Map.of("projectId","p","caseId","c","requestId","hundred-single","fileName","one.png","contentType","image/png","base64",AgentDirectExecutionTests.png()));
            String one=(String)service.upload(minimal).get("evidenceId");
            var singleSubmit=new LinkedHashMap<String,Object>(Map.of("projectId","p","caseId","c","requestId","hundred-single","evidenceIds",List.of(one),"steps",outcomes));
            var published=service.submit(singleSubmit);
            assertEquals("ERROR",published.get("result"));assertEquals("CASE",published.get("evidenceScope"));
            org.springframework.test.util.ReflectionTestUtils.setField(service,"writesEnabled",false);
            assertEquals(published.get("submissionId"),service.submit(singleSubmit).get("submissionId"));
            var paused=new LinkedHashMap<>(minimal);paused.put("requestId","paused-new");
            assertEquals("PERSONAL_EXECUTION_WRITE_DISABLED",assertThrows(MSException.class,()->service.upload(paused)).getMessage());
            org.springframework.test.util.ReflectionTestUtils.setField(service,"writesEnabled",true);
            assertEquals(9,jdbc.queryForObject("SELECT COUNT(*) FROM functional_case_attachment",Integer.class));
            assertArrayEquals(Base64.getDecoder().decode(AgentDirectExecutionTests.png()),store.read("p","c",one));
            assertEquals("ERROR",jdbc.queryForObject("SELECT last_exec_result FROM test_plan_functional_case WHERE test_plan_id='active'",String.class));
            assertEquals("SUCCESS",jdbc.queryForObject("SELECT last_exec_result FROM test_plan_functional_case WHERE test_plan_id='archived'",String.class));
            assertEquals("ERROR",jdbc.queryForObject("SELECT last_exec_result FROM test_plan_functional_case WHERE test_plan_id='foreign-active' AND functional_case_id='c'",String.class));
            assertEquals("SUCCESS",jdbc.queryForObject("SELECT last_exec_result FROM test_plan_functional_case WHERE test_plan_id='foreign-archived'",String.class));
            assertEquals("SUCCESS",jdbc.queryForObject("SELECT last_exec_result FROM test_plan_functional_case WHERE functional_case_id='unrelated-case'",String.class));
            var read=new io.metersphere.agent.tool.functional.FunctionalAttachmentReadTool();
            ReflectionTestUtils.setField(read,"access",mock(AgentBusinessAccess.class));ReflectionTestUtils.setField(read,"jdbc",jdbc);
            var attachmentService=new io.metersphere.functional.service.FunctionalCaseAttachmentService();ReflectionTestUtils.setField(attachmentService,"evidenceStore",store);
            ReflectionTestUtils.setField(read,"attachments",attachmentService);
            var readArgs=new LinkedHashMap<String,Object>(Map.of("projectId","p","caseId","c","fileId",one));
            assertEquals(AgentDirectExecutionTests.png(),((io.metersphere.agent.tool.AgentMcpImageResult)read.execute(readArgs)).data());
            jdbc.update("DELETE FROM functional_case_attachment WHERE file_id=?",one);
            assertThrows(MSException.class,()->read.execute(readArgs));
            readArgs.put("submissionId",published.get("submissionId"));
            assertEquals(AgentDirectExecutionTests.png(),((io.metersphere.agent.tool.AgentMcpImageResult)read.execute(readArgs)).data());
            readArgs.put("projectId","foreign");assertThrows(MSException.class,()->read.execute(readArgs));
            var lookup=new io.metersphere.agent.tool.functional.PersonalOperationGetTool();
            ReflectionTestUtils.setField(lookup,"access",mock(AgentBusinessAccess.class));ReflectionTestUtils.setField(lookup,"jdbc",jdbc);
            var lookupArgs=new LinkedHashMap<String,Object>(Map.of("projectId","p","caseId","c","requestId","hundred-single","toolName","metersphere.functional.execution.evidence.upload"));
            assertEquals("CONSUMED",((Map<?,?>)lookup.execute(lookupArgs)).get("resourceState"));
            lookupArgs.put("toolName","metersphere.functional.execution.submit");assertEquals(published.get("submissionId"),((Map<?,?>)lookup.execute(lookupArgs)).get("submissionId"));
            lookupArgs.put("requestId","missing");assertEquals("UNKNOWN",((Map<?,?>)lookup.execute(lookupArgs)).get("outcome"));
            var provider=new AgentDirectExecutionViewProvider(jdbc,new AgentFactCipher());
            detail.setLastExecuteResult("ERROR");detail.setLastExecuteUser("u");
            detail.setLastExecuteTime(jdbc.queryForObject("SELECT last_execute_time FROM functional_case WHERE id='c'",Long.class));
            assertEquals(published.get("submissionId"),provider.current(detail).orElseThrow().id());
            jdbc.update("UPDATE agent_case_current_execution SET submission_id='' WHERE case_id='c'");
            assertTrue(provider.current(detail).isEmpty());
        } finally {AgentTokenContext.clear();ds.destroy();}
    }
}
