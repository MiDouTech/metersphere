package io.metersphere.system.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 米多星球第三方 SSO 配置（仅后端；Secret 禁止入库 / 禁止下发前端）。
 * <p>
 * 环境变量建议：
 * MIDUO_SSO_BASE_URL / MIDUO_SSO_APP_CODE / MIDUO_SSO_APP_SECRET /
 * MIDUO_SSO_REDIRECT_URI / MIDUO_SSO_SHORTCUT_ID
 */
@ConfigurationProperties(prefix = MiduoSsoProperties.PREFIX)
@Getter
@Setter
public class MiduoSsoProperties {
    public static final String PREFIX = "miduo.sso";

    /** 是否启用米多 SSO */
    private boolean enabled = false;
    /** 米多 API 根地址，如 https://xxx */
    private String baseUrl = "";
    private String appCode = "";
    private String appSecret = "";
    /**
     * 与米多登录桥白名单字符级一致的 redirectUri。
     * 生产须 HTTPS；推荐：https://{host}/#/sso/miduo/callback
     */
    private String redirectUri = "";
    /** 工作台快捷入口 ID（可选） */
    private String shortcutId = "";
    /** 状态检查所用组织 ID（企微同步就绪判定） */
    private String organizationId = "";
    /** state TTL（秒），建议 5～10 分钟 */
    private long stateTtlSeconds = 600;
    /** sessionToken Redis TTL（秒）；对齐米多 sessionToken TTL（交付单常见 28800） */
    private long sessionTtlSeconds = 28800;
    /** refresh 提前量（秒）：距过期少于此值则 refresh */
    private long refreshAheadSeconds = 1800;

    public boolean isConfigured() {
        return enabled
                && baseUrl != null && !baseUrl.isBlank()
                && appCode != null && !appCode.isBlank()
                && appSecret != null && !appSecret.isBlank()
                && redirectUri != null && !redirectUri.isBlank();
    }
}
