package io.metersphere.agent.service;

@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="protocol.mysql",matches="true")
class AgentReviewMySqlRegressionTests extends AgentReviewRegressionTests {
    @Override protected boolean mysql(){return true;}
}
