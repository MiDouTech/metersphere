package io.metersphere.functional.handoff;

/** Safe deployment failure; never exposes a key, ciphertext or receiver address. */
public class HandoffSecretUnavailable extends IllegalStateException {
    public enum Reason { KEY_CONFIGURATION, CIPHERTEXT_FORMAT, AUTHENTICATION_FAILED, CRYPTO_UNAVAILABLE, SECRET_STATE }
    private final Reason reason;
    public HandoffSecretUnavailable() {
        this(Reason.KEY_CONFIGURATION);
    }
    public HandoffSecretUnavailable(Reason reason) {
        super("Webhook加密配置缺失或不可用，请联系部署管理员核对持久化加密密钥；配置未保存");
        this.reason=reason;
    }
    public Reason reason(){return reason;}
}
