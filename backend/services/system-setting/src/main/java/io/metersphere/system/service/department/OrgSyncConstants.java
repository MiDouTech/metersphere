package io.metersphere.system.service.department;

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

    private OrgSyncConstants() {
    }
}
