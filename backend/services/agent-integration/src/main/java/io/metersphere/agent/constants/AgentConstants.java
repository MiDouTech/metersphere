package io.metersphere.agent.constants;

public class AgentConstants {
    public static final String API_PREFIX = "/api/agent/v1";
    /** 网关剥离 /api 后的内部前缀 */
    public static final String API_PREFIX_STRIPPED = "/agent/v1";
    public static final String TOKEN_PREFIX = "msat_";
    public static final String HEADER_PROJECT = "X-MS-PROJECT";
    public static final String HEADER_PROJECT_LEGACY = "PROJECT";
    /** Agent 检索默认每页条数 */
    public static final int DEFAULT_PAGE_SIZE = 50;
    /** Agent 检索每页上限（禁止大页无节制拉取） */
    public static final int MAX_PAGE_SIZE = 100;
    /** 单 Token 全局请求：滑动窗口内上限 */
    public static final int RATE_LIMIT_PER_MINUTE = 120;
    /** 单 Token 检索类接口：滑动窗口内上限（防轮询） */
    public static final int SEARCH_RATE_LIMIT_PER_MINUTE = 30;
    /** 相邻两次检索最小间隔（毫秒） */
    public static final long SEARCH_MIN_INTERVAL_MS = 300L;
    public static final int RATE_LIMIT_WINDOW_MS = 60_000;
    public static final int MAX_ATTACHMENT_SIZE_BYTES = 5 * 1024 * 1024;
    public static final int MAX_ATTACHMENTS_PER_SUBMIT = 10;
    public static final String PRIORITY_FIELD = "functional_priority";
    public static final String FILTER_CASE_LEVEL = "caseLevel";
    public static final String FILTER_LAST_EXECUTE_RESULT = "lastExecuteResult";
    public static final String FILTER_LAST_EXEC_RESULT = "lastExecResult";

    private AgentConstants() {
    }

    /**
     * 将 pageSize 规范到 [1, MAX_PAGE_SIZE]；非法或未传时用默认值。
     */
    public static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
