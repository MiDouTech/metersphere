package io.metersphere.system.sso.miduo;

import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

class MiduoSsoDiagnosticsTests {
    @Test void sqlMetadataSurvivesWithoutCredentialsMessagesOrStack() {
        var fields = MiduoSsoLogUtils.failureFields(new IllegalStateException(
                "https://private.example/?token=synthetic-private-token",
                new SQLException("password=synthetic-private-password", "08006", 1045)));
        assertEquals("DATABASE_ERROR", fields.get("category"));
        assertEquals("SQLException", fields.get("rootType"));
        assertEquals("08006", fields.get("sqlState"));
        assertEquals(1045, fields.get("vendorCode"));
        assertEquals(4, fields.size());
        assertFalse(fields.toString().contains("private"));
        assertFalse(fields.toString().contains("password"));
        assertFalse(fields.toString().contains("https"));
    }

    @Test void untrustedSqlStateIsNotLoggedAndCyclesTerminate() {
        var sql = new SQLException("synthetic-private-token", "secret\npassword", 0);
        var outer = new RuntimeException("synthetic-private-password", sql);
        sql.initCause(outer);
        var fields = MiduoSsoLogUtils.failureFields(outer);
        assertEquals("UNKNOWN", fields.get("sqlState"));
        assertFalse(fields.toString().contains("secret"));
        assertFalse(fields.toString().contains("private"));
    }
}
