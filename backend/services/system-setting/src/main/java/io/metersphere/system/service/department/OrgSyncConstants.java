package io.metersphere.system.service.department;

import org.apache.commons.lang3.StringUtils;

public final class OrgSyncConstants {

    public static final String SYNC_MODE_MANUAL = "MANUAL";
    public static final String SYNC_MODE_SCHEDULE = "SCHEDULE";
    public static final String SYNC_MODE_LOGIN = "LOGIN";

    public static final String SYNC_STATUS_SUCCESS = "SUCCESS";
    public static final String SYNC_STATUS_PARTIAL = "PARTIAL";
    public static final String SYNC_STATUS_FAILED = "FAILED";

    public static final long ROOT_WECOM_DEPARTMENT_ID = 1L;
    public static final String PROTECTED_USER_ID = "admin";
    public static final String SYSTEM_ACCOUNT_PREFIX = "DEV_";
    public static final String WECOM_SYNC_EMAIL_SUFFIX = "@wecom.sync.internal";
    public static final int MAX_EMAIL_LENGTH = 64;

    public static final String EMAIL_CONFLICT_PENDING = "PENDING";
    public static final String EMAIL_CONFLICT_RESOLVED = "RESOLVED";
    public static final String EMAIL_CONFLICT_SKIP = "SKIP";
    public static final String EMAIL_CONFLICT_OVERWRITE = "OVERWRITE";
    public static final String EMAIL_CONFLICT_CREATE = "CREATE";
    public static final String EMAIL_CONFLICT_SCENE_CREATE = "CREATE";
    public static final String EMAIL_CONFLICT_SCENE_UPDATE = "UPDATE";

    private OrgSyncConstants() {
    }

    public static boolean isPlaceholderEmail(String email) {
        return StringUtils.isNotBlank(email)
                && StringUtils.endsWithIgnoreCase(email.trim(), WECOM_SYNC_EMAIL_SUFFIX);
    }

    public static String buildPlaceholderEmail(String wecomUserId) {
        String id = StringUtils.defaultIfBlank(wecomUserId, "unknown").trim().toLowerCase();
        return id + WECOM_SYNC_EMAIL_SUFFIX;
    }
}
