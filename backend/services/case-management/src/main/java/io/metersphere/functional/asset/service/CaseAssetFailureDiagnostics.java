package io.metersphere.functional.asset.service;

import io.metersphere.sdk.util.LogUtils;
import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;

/** Whitelisted async diagnostics: never emit exception messages, payloads or stack traces. */
public final class CaseAssetFailureDiagnostics {
    private CaseAssetFailureDiagnostics() {}
    public static String record(String phase, String reference, Throwable error) {
        phase = phase != null && phase.matches("[A-Z_]{1,40}") ? phase : "UNKNOWN";
        reference = reference != null && reference.matches("[A-Za-z0-9_:-]{1,160}") ? reference : "UNKNOWN";
        Throwable root = error;
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        SQLException sql = null;
        while (root != null && seen.add(root)) {
            if (root instanceof SQLException value) sql = value;
            if (root.getCause() == null || seen.contains(root.getCause())) break;
            root = root.getCause();
        }
        String state = sql == null ? "NONE" : sql.getSQLState();
        if (state == null || !state.matches("[A-Z0-9]{5}")) state = "UNKNOWN";
        var conflict = root instanceof io.metersphere.functional.hub.service.CaseAssetMappingConflictException value ? value : null;
        String category = conflict != null ? "CASE_SOURCE_MAPPING_CONFLICT" : sql != null ? "DATABASE_ERROR" : "PROCESSING_ERROR";
        String rootType = root == null ? "Unknown" : root.getClass().getSimpleName();
        if (!rootType.matches("[A-Za-z0-9_$]{1,100}")) rootType = "UNKNOWN";
        LogUtils.error("case_asset_failure phase=" + phase + " reference=" + reference + " time="
                + System.currentTimeMillis() + " category=" + category + " rootType=" + rootType
                + " sqlState=" + state + " vendorCode=" + (sql == null ? 0 : sql.getErrorCode())
                + (conflict == null ? "" : conflict.diagnosticIds()));
        return (conflict == null ? "处理未完成，请根据错误编号联系管理员定位。" :
                "CASE_SOURCE_MAPPING_CONFLICT：来源映射需人工核对，未合并资产。" + conflict.diagnosticIds()) + "错误编号：" + reference;
    }
}
