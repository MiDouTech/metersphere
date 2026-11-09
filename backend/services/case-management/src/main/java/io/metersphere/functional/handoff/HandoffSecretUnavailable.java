package io.metersphere.functional.handoff;

/** Safe deployment failure; never exposes a key, ciphertext or receiver address. */
public class HandoffSecretUnavailable extends IllegalStateException {
    public HandoffSecretUnavailable() {
        super("Webhook加密配置缺失或不可用，请联系部署管理员核对持久化加密密钥；配置未保存");
    }
}
