package io.metersphere.system.service.impl;

import io.metersphere.system.domain.UserInvite;
import io.metersphere.system.dto.request.UserRegisterRequest;
import io.metersphere.system.dto.user.request.UserBatchCreateRequest;
import io.metersphere.system.mapper.UserMapper;
import io.metersphere.system.mapper.UserRoleRelationMapper;
import io.metersphere.system.service.UserRoleRelationService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class CommunityUserXpackServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserRoleRelationMapper userRoleRelationMapper;
    @Mock
    private UserRoleRelationService userRoleRelationService;

    private CommunityUserXpackServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CommunityUserXpackServiceImpl(userMapper, userRoleRelationMapper, userRoleRelationService);
    }

    @Test
    void gwHowToAddUserBatchReturnsSuccess() {
        Assertions.assertEquals(0, service.GWHowToAddUser(new UserBatchCreateRequest(), "LOCAL", "admin"));
    }

    @Test
    void gwHowToAddUserRegisterReturnsSuccess() {
        UserInvite userInvite = new UserInvite();
        userInvite.setEmail("test@example.com");
        userInvite.setInviteUser("admin");
        userInvite.setRoles("[]");
        Assertions.assertEquals(0, service.GWHowToAddUser(new UserRegisterRequest(), userInvite));
    }

    @Test
    void gwHowToChangeUserReturnsSuccess() {
        Assertions.assertEquals(0, service.GWHowToChangeUser(List.of("user1"), true, "admin"));
    }

    @Test
    void gwHowToDeleteUserReturnsSuccess() {
        Assertions.assertEquals(1, service.GWHowToDeleteUser(List.of("user1"), "admin"));
    }
}
