package io.metersphere.functional.hub.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Reuses all seven reviewed scenarios against native InnoDB locking reads and a real RR transaction. */
@EnabledIfSystemProperty(named = "quality.mysql", matches = "true")
class CaseAssetSourceMappingRepairMySqlTests extends CaseAssetSourceMappingRepairTests {
    private static final String SERVER = "jdbc:mysql://127.0.0.1:13326/";
    private static final String OPTIONS = "?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=3000&socketTimeout=5000";
    private String schema;
    private JdbcTemplate admin;

    @Override
    @BeforeEach
    void setup() {
        super.setup();
        JdbcTemplate sourceFixture = jdbc;
        schema = "asset_mapping_" + UUID.randomUUID().toString().replace("-", "");
        requireOwnSchema();
        admin = new JdbcTemplate(new DriverManagerDataSource(SERVER + "quality_verify" + OPTIONS,
                "root", "quality-test-only"));
        admin.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        var nativeSource = new RepeatableReadDataSource(SERVER + schema + OPTIONS);
        var nativeJdbc = new JdbcTemplate(nativeSource);
        try (Connection source = Objects.requireNonNull(sourceFixture.getDataSource()).getConnection()) {
            copyOwnFixture(source, nativeJdbc);
        } catch (SQLException failure) {
            throw new IllegalStateException("Native mapping fixture initialization failed", failure);
        }
        String isolation;
        try {
            isolation = nativeJdbc.queryForObject("SELECT @@transaction_isolation", String.class);
        } catch (BadSqlGrammarException olderServer) {
            isolation = nativeJdbc.queryForObject("SELECT @@tx_isolation", String.class);
        }
        assertEquals("REPEATABLE-READ", isolation);
        jdbc = nativeJdbc;
        tx = new TransactionTemplate(new DataSourceTransactionManager(nativeSource));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.setTimeout(5);
        ReflectionTestUtils.setField(repair, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(snapshots, "jdbc", jdbc);
        // Hashes must use the same native JDBC representation as the production locking reads.
        map.setContentHash(snapshots.lockAndRead(List.of("source")).get("source").contentHash());
    }

    @Override
    void repairNow() {
        try (var ids = org.mockito.Mockito.mockStatic(io.metersphere.system.uid.IDGenerator.class)) {
            ids.when(io.metersphere.system.uid.IDGenerator::nextStr).thenReturn("audit");
            tx.executeWithoutResult(status -> {
                // Establish a real RR consistent-read view before the current locking snapshot.
                // In the concurrent test this sees the old blob while another transaction holds its edit.
                jdbc.query("SELECT steps FROM functional_case_blob WHERE id='canonical'", (rs, n) -> rs.getBytes(1));
                repair.repair(map, "canonical", "source-ref", "operator", "catalog");
            });
        }
    }

    private static void copyOwnFixture(Connection source, JdbcTemplate destination) throws SQLException {
        DatabaseMetaData metadata = source.getMetaData();
        var tables = new TreeMap<String, String>();
        try (ResultSet rows = metadata.getTables(null, null, "%", new String[]{"TABLE", "BASE TABLE"})) {
            while (rows.next()) {
                if ("public".equalsIgnoreCase(rows.getString("TABLE_SCHEM")))
                    tables.put(identifier(rows.getString("TABLE_NAME")), rows.getString("TABLE_SCHEM"));
            }
        }
        if (tables.isEmpty()) throw new IllegalStateException("Isolated source fixture has no tables");
        for (var table : tables.entrySet()) {
            var primary = new TreeMap<Short, String>();
            try (ResultSet keys = metadata.getPrimaryKeys(null, table.getValue(), table.getKey())) {
                while (keys.next()) primary.put(keys.getShort("KEY_SEQ"), identifier(keys.getString("COLUMN_NAME")));
            }
            var columns = new ArrayList<String>();
            var definitions = new ArrayList<String>();
            var binary = new HashSet<String>();
            try (ResultSet fields = metadata.getColumns(null, table.getValue(), table.getKey(), "%")) {
                while (fields.next()) {
                    String name = identifier(fields.getString("COLUMN_NAME"));
                    int type = fields.getInt("DATA_TYPE");
                    columns.add(name);
                    String nativeType = switch (type) {
                        case Types.BIGINT -> "BIGINT";
                        case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> "INT";
                        case Types.BIT, Types.BOOLEAN -> "BOOLEAN";
                        case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> {
                            binary.add(name);
                            yield "LONGBLOB";
                        }
                        case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.CLOB ->
                                primary.containsValue(name) ? "VARCHAR(128)" : "source_snapshot".equals(name) ? "MEDIUMTEXT" : "VARCHAR(255)";
                        default -> throw new IllegalStateException("Unsupported isolated fixture JDBC type " + type);
                    };
                    definitions.add("`" + name + "` " + nativeType + (primary.containsValue(name) ? " NOT NULL" : ""));
                }
            }
            if (!primary.isEmpty()) definitions.add("PRIMARY KEY (" + quoted(primary.values()) + ")");
            destination.execute("CREATE TABLE `" + table.getKey() + "` (" + String.join(",", definitions) + ") ENGINE=InnoDB");
            // The complete source is a fresh per-test H2 fixture, never a business database.
            try (Statement statement = source.createStatement(); ResultSet rows = statement.executeQuery("SELECT " + quoted(columns) + " FROM `" + table.getKey() + "`")) {
                while (rows.next()) {
                    var values = new Object[columns.size()];
                    for (int i = 0; i < columns.size(); i++)
                        values[i] = binary.contains(columns.get(i)) ? rows.getBytes(columns.get(i)) : rows.getObject(columns.get(i));
                    destination.update("INSERT INTO `" + table.getKey() + "` (" + quoted(columns) + ") VALUES ("
                            + String.join(",", Collections.nCopies(columns.size(), "?")) + ")", values);
                }
            }
        }
    }

    private static String quoted(Collection<String> values) {
        return values.stream().map(value -> "`" + identifier(value) + "`").collect(java.util.stream.Collectors.joining(","));
    }
    private static String identifier(String value) {
        if (value == null || !value.matches("[a-z_][a-z0-9_]*")) throw new IllegalStateException("Invalid isolated fixture identifier");
        return value;
    }
    private void requireOwnSchema() {
        if (schema == null || !schema.matches("asset_mapping_[a-f0-9]{32}"))
            throw new IllegalStateException("Refuse unrelated schema access");
    }
    @AfterEach
    void dropOwnNativeFixture() {
        if (admin == null || schema == null) return;
        requireOwnSchema();
        admin.execute("DROP DATABASE IF EXISTS `" + schema + "`");
    }

    /** Every worker gets its own native connection; never share one connection between the two threads. */
    private static final class RepeatableReadDataSource extends DriverManagerDataSource {
        RepeatableReadDataSource(String url) { super(url, "root", "quality-test-only"); }
        @Override public Connection getConnection() throws SQLException {
            Connection connection = super.getConnection();
            try {
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SET SESSION innodb_lock_wait_timeout=2");
                }
                return connection;
            } catch (SQLException failure) {
                connection.close();
                throw failure;
            }
        }
    }
}
