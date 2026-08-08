package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentExecutionStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AgentExecutionStateMachineTests {
    @Test
    void shouldAllowNormalExecutionPath() {
        Assertions.assertTrue(AgentExecutionStateMachine.canTransition(
                AgentExecutionStatus.CREATED, AgentExecutionStatus.QUEUED));
        Assertions.assertTrue(AgentExecutionStateMachine.canTransition(
                AgentExecutionStatus.QUEUED, AgentExecutionStatus.PREPARING_BROWSER));
        Assertions.assertTrue(AgentExecutionStateMachine.canTransition(
                AgentExecutionStatus.PREPARING_BROWSER, AgentExecutionStatus.RUNNING));
        Assertions.assertTrue(AgentExecutionStateMachine.canTransition(
                AgentExecutionStatus.RUNNING, AgentExecutionStatus.WRITING_BACK));
        Assertions.assertTrue(AgentExecutionStateMachine.canTransition(
                AgentExecutionStatus.WRITING_BACK, AgentExecutionStatus.SUCCESS));
    }

    @Test
    void shouldRejectTerminalAndSkippedTransitions() {
        Assertions.assertFalse(AgentExecutionStateMachine.canTransition(
                AgentExecutionStatus.SUCCESS, AgentExecutionStatus.RUNNING));
        Assertions.assertFalse(AgentExecutionStateMachine.canTransition(
                AgentExecutionStatus.CREATED, AgentExecutionStatus.SUCCESS));
        Assertions.assertThrows(IllegalStateException.class, () ->
                AgentExecutionStateMachine.requireTransition(
                        AgentExecutionStatus.QUEUED, AgentExecutionStatus.SUCCESS));
    }

    @Test
    void shouldAllowCancelFromAnyNonTerminalOperationalState() {
        Assertions.assertTrue(AgentExecutionStateMachine.canTransition(
                AgentExecutionStatus.WAITING_CONFIRMATION, AgentExecutionStatus.CANCELED));
        Assertions.assertTrue(AgentExecutionStateMachine.canTransition(
                AgentExecutionStatus.RUNNING, AgentExecutionStatus.CANCELED));
        Assertions.assertFalse(AgentExecutionStateMachine.canTransition(
                AgentExecutionStatus.FAILED, AgentExecutionStatus.CANCELED));
    }
}
