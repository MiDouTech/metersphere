package io.metersphere.system.config;

import io.metersphere.system.mapper.UserMapper;
import io.metersphere.system.mapper.UserRoleRelationMapper;
import io.metersphere.system.service.LicenseService;
import io.metersphere.system.service.UserRoleRelationService;
import io.metersphere.system.service.UserXpackService;
import io.metersphere.system.service.impl.CommunityLicenseServiceImpl;
import io.metersphere.system.service.impl.CommunityUserXpackServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers community edition License / UserXpack beans when no enterprise implementation exists.
 */
@Configuration
public class CommunityXpackConfiguration {

    @Bean
    @ConditionalOnMissingBean(LicenseService.class)
    public LicenseService communityLicenseService() {
        return new CommunityLicenseServiceImpl();
    }

    @Bean
    @ConditionalOnMissingBean(UserXpackService.class)
    public UserXpackService communityUserXpackService(UserMapper userMapper,
                                                      UserRoleRelationMapper userRoleRelationMapper,
                                                      UserRoleRelationService userRoleRelationService) {
        return new CommunityUserXpackServiceImpl(userMapper, userRoleRelationMapper, userRoleRelationService);
    }
}
