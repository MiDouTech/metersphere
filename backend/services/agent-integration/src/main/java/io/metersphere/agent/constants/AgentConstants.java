package io.metersphere.agent.constants;

public class AgentConstants {
    public static final String API_PREFIX = "/api/agent/v1";
    /** 网关剥离 /api 后的内部前缀 */
    public static final String API_PREFIX_STRIPPED = "/agent/v1";
    public static final String TOKEN_PREFIX = "msat_";
    public static final String HEADER_PROJECT = "X-MS-PROJECT";
    public static final String HEADER_PROJECT_LEGACY = "PROJECT";
    public static final int MAX_PAGE_SIZE = 500;
    public static final int MAX_ATTACHMENT_SIZE_BYTES = 5 * 1024 * 1024;
    public static final int MAX_ATTACHMENTS_PER_SUBMIT = 10;
    public static final String PRIORITY_FIELD = "functional_priority";
    public static final String FILTER_CASE_LEVEL = "caseLevel";
    public static final String FILTER_LAST_EXECUTE_RESULT = "lastExecuteResult";
    public static final String FILTER_LAST_EXEC_RESULT = "lastExecResult";

    private AgentConstants() {
    }
}
