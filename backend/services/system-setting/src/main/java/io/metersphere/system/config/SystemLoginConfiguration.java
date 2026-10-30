package io.metersphere.system.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Register login properties for both the packaged app and independently bootstrapped modules. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({LocalLoginProperties.class, MiduoSsoProperties.class})
public class SystemLoginConfiguration { }
