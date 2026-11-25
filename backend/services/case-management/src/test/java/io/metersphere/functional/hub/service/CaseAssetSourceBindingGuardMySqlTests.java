package io.metersphere.functional.hub.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Same scenarios on the repository's isolated local MySQL fixture; never connects to deployment data. */
@EnabledIfSystemProperty(named = "quality.mysql", matches = "true")
class CaseAssetSourceBindingGuardMySqlTests extends CaseAssetSourceBindingGuardTests {
    private static final String SERVER = "jdbc:mysql://127.0.0.1:13326/";
    private static final String OPTIONS = "?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=3000&socketTimeout=5000";
    private JdbcTemplate admin;
    private String schema;

    @Override
    @BeforeEach
    void setup() {
        schema = "msp36_binding_" + UUID.randomUUID().toString().replace("-", "");
        admin = new JdbcTemplate(new DriverManagerDataSource(SERVER + "quality_verify" + OPTIONS,
                "root", "quality-test-only"));
        admin.execute("CREATE DATABASE `" + ownSchema() + "`");
        var ds = new DriverManagerDataSource(SERVER + schema + OPTIONS, "root", "quality-test-only");
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.setTimeout(5);
        initializeFixture();
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND engine='InnoDB'",
                Integer.class, schema));
    }

    @Override
    void syncUsingOldBinding() {
        tx.executeWithoutResult(status -> {
            assertEquals("REPEATABLE-READ", jdbc.queryForObject("SELECT @@transaction_isolation", String.class));
            // Establish an old consistent-read view while DETACH is uncommitted. A plain re-read
            // would retain that old binding after the row-lock wait; the guard must use current reads.
            jdbc.queryForList("SELECT id FROM default_hub_case_map WHERE biz_case_id='source'");
            super.syncUsingOldBinding();
        });
    }

    @AfterEach
    void cleanup() {
        if (admin != null && schema != null) admin.execute("DROP DATABASE IF EXISTS `" + ownSchema() + "`");
    }

    private String ownSchema() {
        if (schema == null || !schema.matches("msp36_binding_[a-f0-9]{32}"))
            throw new IllegalStateException("Refusing to access a non-fixture schema");
        return schema;
    }
}
