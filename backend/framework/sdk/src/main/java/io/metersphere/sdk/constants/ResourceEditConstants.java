package io.metersphere.sdk.constants;

/**
 * 资源编辑自动保存 / 锁 / 快照常量
 */
public final class ResourceEditConstants {
    private ResourceEditConstants() {
    }

    /** 系统参数：自动保存总开关，缺省 true */
    public static final String AUTOSAVE_ENABLED_PARAM_KEY = "resource.edit.autosave.enabled";

    /** 系统参数：写入路径（Agent/导入/批量）单次快照，缺省 false */
    public static final String WRITE_PATH_SNAPSHOT_ENABLED_PARAM_KEY = "resource.edit.writepath.snapshot.enabled";

    /** 无操作自动释锁（毫秒） */
    public static final long LOCK_TTL_MS = 15 * 60 * 1000L;

    /** 每资源最多保留快照条数（含当前，支撑 Undo 2 步 + 余量） */
    public static final int MAX_SNAPSHOTS_PER_RESOURCE = 3;

    /** 最多可 Undo 步数 */
    public static final int MAX_UNDO_STEPS = 2;

    /** payload 建议上限（字符），超出拒绝入栈 */
    public static final int MAX_PAYLOAD_CHARS = 2_000_000;

    public static final String TYPE_FUNCTIONAL_CASE = "FUNCTIONAL_CASE";
    public static final String TYPE_BUG = "BUG";
    public static final String TYPE_TEST_PLAN_DOCUMENT = "TEST_PLAN_DOCUMENT";
}
