package io.metersphere.system.edit;

/**
 * 业务资源编辑适配器：通用层通过此接口回写正式数据
 */
public interface ResourceEditAdapter {

    String resourceType();

    /** 读取当前正式数据 JSON（加锁基线 / 保存后快照） */
    String loadPayload(String resourceId);

    /** 将快照 JSON 写回正式表 */
    void applyPayload(String resourceId, String payloadJson, String operator);
}
