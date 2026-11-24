package io.metersphere.system.sso.miduo;

import org.apache.commons.lang3.StringUtils;

/**
 * SSO 日志脱敏工具。
 */
public final class MiduoSsoLogUtils {

    private MiduoSsoLogUtils() {
    }

    /** Whitelisted failure metadata only: no messages, stack traces, URLs or credentials. */
    public static java.util.Map<String, Object> failureFields(Throwable failure) {
        Throwable root = failure;
        java.sql.SQLException sql = null;
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        for (int depth = 0; root != null && depth < 32 && seen.add(root); depth++) {
            if (root instanceof java.sql.SQLException value) sql = value;
            if (root.getCause() == null || seen.contains(root.getCause())) break;
            root = root.getCause();
        }
        String type = root == null ? "UNKNOWN" : root.getClass().getSimpleName();
        if (!type.matches("[A-Za-z0-9_$]{1,100}")) type = "UNKNOWN";
        String state = sql == null ? "NONE" : sql.getSQLState();
        if (state == null || !state.matches("[A-Z0-9]{5}")) state = "UNKNOWN";
        return java.util.Map.of("category", sql == null ? "PROCESSING_ERROR" : "DATABASE_ERROR",
                "rootType", type, "sqlState", state, "vendorCode", sql == null ? 0 : sql.getErrorCode());
    }

    public static String maskToken(String token) {
        if (StringUtils.isBlank(token)) {
            return "";
        }
        if (token.length() <= 6) {
            return "***";
        }
        return token.substring(0, 6) + "***";
    }

    public static String maskTail(String value, int keep) {
        if (StringUtils.isBlank(value)) {
            return "";
        }
        if (value.length() <= keep) {
            return "***";
        }
        return "***" + value.substring(value.length() - keep);
    }
}
