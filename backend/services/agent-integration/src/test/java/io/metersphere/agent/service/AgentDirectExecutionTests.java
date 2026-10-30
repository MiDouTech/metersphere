package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentExecutionArtifactDTO;
import io.metersphere.agent.quality.GlobalQualityGate;
import io.metersphere.agent.quality.QualityPolicyValidator;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentDirectExecutionTests {
    @Test void ordinaryCaseCopyMaterializesApprovedEvidenceForExistingStorageConsumers() throws Exception {
        var service=new io.metersphere.functional.service.FunctionalCaseAttachmentService();
        var store=org.mockito.Mockito.mock(io.metersphere.functional.service.FunctionalCaseEvidenceStore.class);
        var files=org.mockito.Mockito.mock(io.metersphere.system.service.FileService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"evidenceStore",store);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"fileService",files);
        byte[] bytes=Base64.getDecoder().decode(png());
        org.mockito.Mockito.when(store.read("p","source","e")).thenReturn(bytes);
        assertTrue(service.copyExecutionEvidence("p","source","target-project","target","e","screen.png"));
        var request=org.mockito.ArgumentCaptor.forClass(io.metersphere.sdk.file.FileRequest.class);
        org.mockito.Mockito.verify(files).upload(org.mockito.ArgumentMatchers.eq(bytes),request.capture());
        assertTrue(request.getValue().getFolder().contains("target"));
        assertEquals("screen.png",request.getValue().getFileName());
        assertFalse(service.copyExecutionEvidence("p","unknown","p","target","e","screen.png"));
        org.mockito.Mockito.verifyNoMoreInteractions(files);
    }
    static final String POLICY="""
        {"schemaVersion":"quality-policy.v1","name":"global","rules":[
        {"ruleId":"Q-EVIDENCE-01","parameters":{"requiredRoles":["BEFORE_ACTION","AFTER_ACTION"],"allowedMimeTypes":["image/png"],"maxArtifactBytes":5242880}},
        {"ruleId":"Q-ASSERT-01","parameters":{"allowedOperators":["EQUALS"],"expectedSource":"FROZEN_CONTRACT"}}]}
        """;
    static String png() throws Exception {
        var output=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(64,64,BufferedImage.TYPE_INT_RGB),"png",output);
        return Base64.getEncoder().encodeToString(output.toByteArray());
    }
    @Test void evidenceMustDecodeAsTheDeclaredBoundedImage() throws Exception {
        assertTrue(AgentDirectExecutionService.decodeImage(png(),"image/png").length>0);
        assertThrows(MSException.class,()->AgentDirectExecutionService.decodeImage(png(),"image/jpeg"));
        assertThrows(MSException.class,()->AgentDirectExecutionService.decodeImage("PGh0bWw+","image/png"));
        assertThrows(MSException.class,()->AgentDirectExecutionService.decodeImage("!invalid","image/png"));
        assertThrows(MSException.class,()->AgentDirectExecutionService.decodeImage("A".repeat(6990509),"image/png"));
        var tiny=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",tiny);
        assertThrows(MSException.class,()->AgentDirectExecutionService.decodeImage(Base64.getEncoder().encodeToString(tiny.toByteArray()),"image/png"));
    }
    @Test void directModeValidatesEvidenceWithoutDerivingBusinessVerdicts() {
        var gate=new GlobalQualityGate(null,null,new QualityPolicyValidator());
        var policy=new GlobalQualityGate.Binding("global",1,"hash",POLICY);
        var before=artifact("BEFORE_STEP");
        var after=artifact("AFTER_STEP");
        assertDoesNotThrow(()->gate.verifyDirectEvidence(policy,List.of(before,after)));
        assertEquals("QUALITY_EVIDENCE_REQUIRED",assertThrows(MSException.class,
                ()->gate.verifyDirectEvidence(policy,List.of(after))).getMessage());
        after.setContentType("image/jpeg");
        assertThrows(MSException.class,()->gate.verifyDirectEvidence(policy,List.of(before,after)));
    }
    @Test void coverageAllowsRepeatedExtraAndUnorderedStepsButNotOmissions() throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        assertDoesNotThrow(()->AgentDirectExecutionService.requireStepCoverage(json.readTree("[{\"stepNum\":2},{\"stepNum\":1},{\"stepNum\":1},{\"stepNum\":3}]"),2));
        for(String invalid:List.of("[]","[{\"stepNum\":1},{\"stepNum\":1},{\"stepNum\":3}]","[{\"stepNum\":0},{\"stepNum\":2}]","[{\"stepNum\":1.5},{\"stepNum\":2}]","[{\"stepNum\":\"1\"},{\"stepNum\":2}]"))
            assertThrows(MSException.class,()->AgentDirectExecutionService.requireStepCoverage(json.readTree(invalid),2));
    }
    @Test void oneCaseImageDoesNotInheritRunnerBeforeAfterRoles() throws Exception {
        var gate=new GlobalQualityGate(null,null,new QualityPolicyValidator());
        assertDoesNotThrow(()->gate.verifyDirectCaseEvidence(List.of(artifact("AFTER_STEP"))));
        assertThrows(MSException.class,()->gate.verifyDirectCaseEvidence(List.of()));
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var steps=json.readTree("[{\"stepNum\":1},{\"stepNum\":2}]");
        assertEquals(Set.of("e"),AgentDirectExecutionService.collectEvidence(Map.of("evidenceIds",List.of("e","e")),steps));
        assertEquals(Set.of("e"),AgentDirectExecutionService.collectEvidence(Map.of(),json.readTree("[{\"evidenceIds\":[\"e\"]},{\"evidenceIds\":[]}]")));
        assertThrows(MSException.class,()->AgentDirectExecutionService.collectEvidence(Map.of(),steps));
        assertThrows(MSException.class,()->AgentDirectExecutionService.collectEvidence(Map.of("evidenceIds",Collections.nCopies(11,"e")),steps));
    }
    @Test void redactionPreservesJsonStructureAndPrimitiveTypes() throws Exception {
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var input=Map.of("actual","password=abc", "token","private", "count",2,"ok",true,"nested",List.of(Map.of("actual","Bearer secret")));
        var result=mapper.readTree(io.metersphere.agent.security.AgentSensitiveDataSanitizer.sanitizeJson(mapper.writeValueAsString(input)));
        assertEquals("password=***",result.path("actual").asText());assertEquals("***",result.path("token").asText());
        assertEquals(2,result.path("count").asInt());assertTrue(result.path("ok").asBoolean());assertEquals("Bearer ***",result.path("nested").get(0).path("actual").asText());
    }
    @Test void canonicalRequestsIgnoreNestedObjectOrderButRetainArrayOrder() {
        var first=new LinkedHashMap<String,Object>();first.put("a",1);first.put("b",2);
        var second=new LinkedHashMap<String,Object>();second.put("b",2);second.put("a",1);
        assertEquals(AgentRequestFingerprint.hash(Map.of("nested",first,"requestId","old")),AgentRequestFingerprint.hash(Map.of("nested",second,"requestId","new")));
        assertNotEquals(AgentRequestFingerprint.hash(Map.of("items",List.of(1,2))),AgentRequestFingerprint.hash(Map.of("items",List.of(2,1))));
    }
    @Test void contractUsesDefinitionOnlyAndReportsUnsupportedTypesWithoutPublicReadToolDependency() {
        var service=new AgentDirectExecutionService();
        var projects=org.mockito.Mockito.mock(AgentProjectService.class);
        org.mockito.Mockito.when(projects.resolveProjectId(org.mockito.ArgumentMatchers.anyString())).thenAnswer(call->call.getArgument(0));
        org.springframework.test.util.ReflectionTestUtils.setField(service,"projects",projects);
        var detail=new io.metersphere.functional.dto.FunctionalCaseDetailDTO();
        detail.setId("c");detail.setProjectId("p");detail.setName("definition");detail.setCaseEditType("STEP");
        detail.setSteps("[{\"num\":1,\"desc\":\"open\",\"result\":\"visible\",\"actualResult\":\"old result\"}]");
        var definitions=org.mockito.Mockito.mock(io.metersphere.functional.service.FunctionalCaseService.class);
        org.mockito.Mockito.when(definitions.getAgentCaseDetails(List.of("c"),"p",true)).thenReturn(Map.of("c",detail));
        org.springframework.test.util.ReflectionTestUtils.setField(service,"definitions",definitions);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"access",org.mockito.Mockito.mock(AgentBusinessAccess.class));
        org.springframework.test.util.ReflectionTestUtils.setField(service,"schemaMapper",new io.metersphere.agent.mapper.AgentCaseSchemaMapper());
        org.springframework.test.util.ReflectionTestUtils.setField(service,"operations",new AgentOperationExecutor(org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class),org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class)));
        var gate=org.mockito.Mockito.mock(GlobalQualityGate.class);
        org.mockito.Mockito.when(gate.currentForSubmission()).thenReturn(new GlobalQualityGate.Binding("p",1,"policy",POLICY));
        org.springframework.test.util.ReflectionTestUtils.setField(service,"gate",gate);
        var args=Map.<String,Object>of("projectId","p","caseId","c");
        var first=service.contract(args);assertEquals(true,first.get("supported"));
        assertEquals(false,((Map<?,?>)first.get("effectiveEvidencePolicy")).get("stepEvidenceRequired"));
        org.mockito.Mockito.when(projects.resolveProjectId("Project Name")).thenReturn("p");
        org.mockito.Mockito.when(projects.resolveProjectId("100001")).thenReturn("p");
        assertEquals(first.get("contentHash"),service.contract(Map.of("projectId","Project Name","caseId","c")).get("contentHash"));
        assertEquals(first.get("contentHash"),service.contract(Map.of("projectId","100001","caseId","c")).get("contentHash"));
        var frozen=(io.metersphere.agent.dto.AgentCaseDTO)first.get("case");assertNull(frozen.getSteps().getFirst().getActualResult());
        detail.setLastExecuteResult("ERROR");detail.setUpdateTime(999L);detail.setExecutionFacts(Map.of("previous","not a definition"));
        assertEquals(first.get("contentHash"),service.contract(args).get("contentHash"));
        detail.setName("changed definition");assertNotEquals(first.get("contentHash"),service.contract(args).get("contentHash"));
        detail.setCaseEditType("TEXT");assertEquals(false,service.contract(args).get("supported"));
    }
    private AgentExecutionArtifactDTO artifact(String purpose) {
        var artifact=new AgentExecutionArtifactDTO();artifact.setPurpose(purpose);artifact.setContentType("image/png");artifact.setSizeBytes(100L);return artifact;
    }
}
