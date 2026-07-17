package io.metersphere.system.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LOCAL 账密登录配置。
 * <p>
 * 环境变量：MS_LOCAL_LOGIN_ENABLED
 */
@ConfigurationProperties(prefix = LocalLoginProperties.PREFIX)
@Getter
@Setter
public class LocalLoginProperties {
    public static final String PREFIX = "metersphere.local-login";

    /**
     * 是否允许普通 LOCAL 账密登录；false 时仅保留 admin 运维入口。
     */
    private boolean enabled = true;

    @PostConstruct
    public void initFromEnvironment() {
        String value = System.getenv("MS_LOCAL_LOGIN_ENABLED");
        if (StringUtils.isNotBlank(value)) {
            enabled = Boolean.parseBoolean(value);
        }
    }
}
