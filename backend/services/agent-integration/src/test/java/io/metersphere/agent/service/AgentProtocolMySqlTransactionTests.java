package io.metersphere.agent.service;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
/** Runs the same production transactions and the exact, unmodified new DDL in disposable native MySQL schemas. */
@EnabledIfSystemProperty(named="protocol.mysql",matches="true")
class AgentProtocolMySqlTransactionTests extends AgentProtocolTransactionTests {
    @Override protected boolean mysql(){return true;}
}
