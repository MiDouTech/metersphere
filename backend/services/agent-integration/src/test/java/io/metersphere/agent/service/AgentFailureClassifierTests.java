package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentExecutionStepDTO;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AgentFailureClassifierTests {
    private final AgentFailureClassifier classifier=new AgentFailureClassifier();
    @Test void productFailureRequiresEvidence(){
        AgentExecutionStepDTO step=new AgentExecutionStepDTO();step.setFailureCategory("ASSERTION_MISMATCH");
        assertEquals("NEEDS_REVIEW",classifier.classify(List.of(step),0,false,false).verdict());
        AgentFailureClassifier.Classification result=classifier.classify(List.of(step),1,false,false);
        assertEquals("PRODUCT_FAILED",result.verdict());assertTrue(result.evidenceSufficient());
    }
    @Test void environmentAndCleanupFailuresNeverBecomeProductBugs(){
        AgentExecutionStepDTO step=new AgentExecutionStepDTO();step.setFailureCategory("ENV_NETWORK");
        assertEquals("ENV_FAILED",classifier.classify(List.of(step),2,false,false).verdict());
        assertEquals("DATA_FAILED",classifier.classify(List.of(step),2,true,false).verdict());
    }
}
