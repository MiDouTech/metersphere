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
    private AgentExecutionArtifactDTO artifact(String purpose) {
        var artifact=new AgentExecutionArtifactDTO();artifact.setPurpose(purpose);artifact.setContentType("image/png");artifact.setSizeBytes(100L);return artifact;
    }
}
