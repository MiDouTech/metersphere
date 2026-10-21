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
            jdbc.execute("CREATE TEMPORARY TABLE agent_token(id VARCHAR(64) PRIMARY KEY)");
            jdbc.update("INSERT INTO agent_token VALUES ('t')");
            jdbc.execute("CREATE TEMPORARY TABLE functional_case(id VARCHAR(64) PRIMARY KEY,project_id VARCHAR(64),deleted BOOLEAN,last_execute_result VARCHAR(32),last_execute_user VARCHAR(64),last_execute_time BIGINT)");
            jdbc.update("INSERT INTO functional_case VALUES ('c','p',0,'PENDING',NULL,NULL)");
            jdbc.execute("CREATE TEMPORARY TABLE functional_case_attachment(id VARCHAR(64) PRIMARY KEY,case_id VARCHAR(64),file_id VARCHAR(64),file_name VARCHAR(255),size BIGINT,local BOOLEAN,create_user VARCHAR(64),create_time BIGINT,file_source VARCHAR(32))");
            jdbc.execute("CREATE TEMPORARY TABLE agent_idempotency_record(id VARCHAR(64) PRIMARY KEY,token_id VARCHAR(64),tool_name VARCHAR(128),request_id VARCHAR(128),request_hash CHAR(64),response_json MEDIUMTEXT,create_time BIGINT,status VARCHAR(32),hash_version VARCHAR(32),completed_time BIGINT,UNIQUE KEY(token_id,tool_name,request_id))");
            jdbc.execute("CREATE TEMPORARY TABLE execution_quality_policy_global_state(scope VARCHAR(32),current_policy_id VARCHAR(64))");
            jdbc.execute("CREATE TEMPORARY TABLE execution_quality_policy_global(id VARCHAR(64),version_no INT,content_hash CHAR(64),rules_json TEXT,status VARCHAR(32))");
            var validated=new QualityPolicyValidator().validate(AgentDirectExecutionTests.POLICY);
            assertTrue(validated.valid());
            jdbc.update("INSERT INTO execution_quality_policy_global_state VALUES ('GLOBAL','policy')");
            jdbc.update("INSERT INTO execution_quality_policy_global VALUES ('policy',1,?,?,'PUBLISHED')",validated.contentHash(),validated.normalizedJson());
            var token=new AgentToken();token.setId("t");token.setUserId("u");AgentTokenContext.set(token);
            var source=new AgentCaseDTO();source.setCaseId("c");source.setProjectId("p");source.setUpdateTime(1L);
            var step=new AgentCaseStepDTO();step.setNum(1);step.setDesc("open");step.setExpected("visible");source.setSteps(List.of(step));
            var search=mock(AgentFunctionalCaseSearchService.class);when(search.getById("c",true,null)).thenReturn(source);
            var service=new AgentDirectExecutionService();
            ReflectionTestUtils.setField(service,"jdbc",jdbc);
            ReflectionTestUtils.setField(service,"access",mock(AgentBusinessAccess.class));
            ReflectionTestUtils.setField(service,"cases",search);
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
            submit.put("steps",List.of(Map.of("stepNum",1,"actual","observed with different wording","result","SUCCESS","evidenceIds",List.of(after))));
            assertEquals("QUALITY_EVIDENCE_REQUIRED",assertThrows(MSException.class,()->service.submit(submit)).getMessage());
            assertEquals("PENDING",jdbc.queryForObject("SELECT last_execute_result FROM functional_case",String.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM functional_case_attachment",Integer.class));
            var store=new FunctionalCaseEvidenceStore();ReflectionTestUtils.setField(store,"jdbc",jdbc);
            assertNull(store.read("p","c",before));
            submit.put("steps",List.of(Map.of("stepNum",1,"actual","observed with different wording","result","SUCCESS","evidenceIds",List.of(before,after))));
            token.setId("other-token");assertThrows(MSException.class,()->service.submit(submit));token.setId("t");
            submit.put("contentHash","0".repeat(64));assertEquals("VERSION_CONFLICT",assertThrows(MSException.class,()->service.submit(submit)).getMessage());submit.put("contentHash",contract.get("contentHash"));
            jdbc.execute("ALTER TABLE functional_case_attachment ADD UNIQUE KEY forced_failure(file_name)");
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->service.submit(submit));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM agent_case_submission",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM functional_case_attachment",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM agent_case_evidence WHERE submission_id IS NOT NULL",Integer.class));
            jdbc.execute("ALTER TABLE functional_case_attachment DROP INDEX forced_failure");
            var result=service.submit(submit);
            assertEquals("SUCCESS",result.get("result"));
            assertEquals(result.get("submissionId"),service.submit(submit).get("submissionId"));
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM functional_case_attachment",Integer.class));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM agent_case_submission",Integer.class));
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
        } finally {AgentTokenContext.clear();ds.destroy();}
    }
}
