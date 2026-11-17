package io.metersphere.agent.service;

import io.metersphere.functional.planreview.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Explicit project runtime configuration only. Missing isolation never falls back to the main datasource. */
@Configuration
public class PlanReviewLegacySandboxConfiguration {
    @Bean
    @ConditionalOnProperty(name="plan-review.rehearsal.enabled",havingValue="true")
    public PlanReviewLegacySandbox planReviewLegacySandbox(JdbcTemplate primary,Environment environment){
        String url=environment.getRequiredProperty("plan-review.rehearsal.jdbc-url");
        if(!url.startsWith("jdbc:mysql://")||url.matches("(?i).*([?&])(password|user)=.*"))throw new IllegalArgumentException("请为隔离演练单独配置MySQL地址、账号与安全密码来源");
        var source=new DriverManagerDataSource(url,environment.getRequiredProperty("plan-review.rehearsal.username"),environment.getRequiredProperty("plan-review.rehearsal.password"));
        return new PlanReviewLegacyJdbcSandbox(primary,new JdbcTemplate(source));
    }
}
