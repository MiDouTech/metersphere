package io.metersphere.sdk.constants;

/**
 * 默认项目（枢纽）相关常量
 */
public final class DefaultHubConstants {
    private DefaultHubConstants() {
    }

    /** 默认组织 num */
    public static final long DEFAULT_ORG_NUM = 100001L;

    /** 历史默认项目 ID（迁移优先标记） */
    public static final String LEGACY_DEFAULT_PROJECT_ID = "100001100001";

    public static final String ROLE_HUB_PROJECT_MEMBER = "default_hub_project_member";
    public static final String ROLE_HUB_ORG_SETTING = "default_hub_org_setting";

    public static final String MODULE_TYPE_MODULE = "MODULE";
    public static final String MODULE_TYPE_FOLDER = "FOLDER";

    public static final String TREE_TYPE_MODULE = "module";
    public static final String TREE_TYPE_FOLDER = "folder";

    /** 系统参数：枢纽同步总开关（缺省 true） */
    public static final String SYNC_ENABLED_PARAM_KEY = "default.hub.sync.enabled";

    /** 同步任务类型 */
    public static final String JOB_TYPE_EVENT = "EVENT";
    public static final String JOB_TYPE_CRON = "CRON";
    public static final String JOB_TYPE_MANUAL = "MANUAL";
    public static final String JOB_TYPE_IMPORT_CASE = "IMPORT_CASE";
    public static final String JOB_TYPE_IMPORT_PLAN = "IMPORT_PLAN";

    /** 同步任务状态 */
    public static final String JOB_STATUS_PENDING = "PENDING";
    public static final String JOB_STATUS_RUNNING = "RUNNING";
    public static final String JOB_STATUS_SUCCESS = "SUCCESS";
    public static final String JOB_STATUS_FAILED = "FAILED";

    /** 每日 0 点对账 Cron（Asia/Shanghai 由 JVM 时区配置） */
    public static final String SYNC_CRON_DAILY = "0 0 0 * * ?";

    /** 用例导入单次上限 */
    public static final int MAX_CASE_IMPORT_BATCH = 500;

    /** 异步任务等待上限（毫秒） */
    public static final long ASYNC_WAIT_TIMEOUT_MS = 5 * 60 * 1000L;

    /** 冲突策略 */
    public static final String CONFLICT_SKIP = "SKIP";
    public static final String CONFLICT_OVERWRITE = "OVERWRITE";

    /** 用例选择模式 */
    public static final String SELECT_CASE_IDS = "CASE_IDS";
    public static final String SELECT_MODULE_IDS = "MODULE_IDS";
    public static final String SELECT_FOLDER_IDS = "FOLDER_IDS";
    public static final String SELECT_ALL = "ALL";
    public static final String SELECT_UNPLANNED = "UNPLANNED";
}
