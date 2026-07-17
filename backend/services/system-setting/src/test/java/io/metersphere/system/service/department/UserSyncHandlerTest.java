package io.metersphere.system.service.department;

import io.metersphere.system.domain.Department;
import io.metersphere.system.domain.User;
import io.metersphere.system.dto.request.OrganizationMemberRequest;
import io.metersphere.system.dto.user.request.UserBatchCreateRequest;
import io.metersphere.system.dto.user.response.UserBatchCreateResponse;
import io.metersphere.system.dto.wecom.WecomUserDTO;
import io.metersphere.system.mapper.ExtDepartmentMapper;
import io.metersphere.system.mapper.ExtUserMapper;
import io.metersphere.system.mapper.UserMapper;
import io.metersphere.system.service.OrganizationService;
import io.metersphere.system.service.SimpleUserService;
import io.metersphere.system.service.wecom.WecomContactClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSyncHandlerTest {

    @InjectMocks
    private UserSyncHandler userSyncHandler;
    @Mock
    private WecomContactClient wecomContactClient;
    @Mock
    private ExtDepartmentMapper extDepartmentMapper;
    @Mock
    private ExtUserMapper extUserMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private SimpleUserService simpleUserService;
    @Mock
    private OrganizationService organizationService;

    @Test
    void sync_newUser_createsUserAndOrgMember() {
        mockDepartments();
        when(extUserMapper.listWecomUsersByOrganizationId("org-1")).thenReturn(new ArrayList<>());
        when(extUserMapper.selectByWecomUserid("zhangsan")).thenReturn(null);
        when(wecomContactClient.executeWithToken(anyString(), anyString(), any())).thenAnswer(invocation -> {
            Function<String, List<WecomUserDTO>> action = invocation.getArgument(2);
            return action.apply("token");
        });
        when(wecomContactClient.listDepartmentUsers("token", 1L, true))
                .thenReturn(List.of(user("zhangsan", "张三", "13800000001", "zhangsan@example.com", 1L, 1)));
        doAnswer(invocation -> {
            UserBatchCreateRequest request = invocation.getArgument(0);
            request.getUserInfoList().getFirst().setId("user-1");
            return new UserBatchCreateResponse();
        }).when(simpleUserService).addUser(any(UserBatchCreateRequest.class), anyString(), anyString());

        SyncPartResult result = userSyncHandler.sync("org-1", "admin", "corp", "secret");

        Assertions.assertEquals(1, result.getCreated());
        verify(simpleUserService).addUser(any(UserBatchCreateRequest.class), anyString(), eq("admin"));
        verify(organizationService).addMemberBySystem(any(OrganizationMemberRequest.class), eq("admin"));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateByPrimaryKeySelective(captor.capture());
        Assertions.assertEquals("", captor.getValue().getPassword());
        Assertions.assertEquals("zhangsan", captor.getValue().getWecomUserid());
    }

    @Test
    void sync_existingUser_emptyMobile_doesNotClearPhone() {
        mockDepartments();
        User existing = buildUser("user-1", "zhangsan", "13800000001");
        existing.setName("张三");
        existing.setDepartmentId("dept-1");
        existing.setEmail("zhangsan@example.com");
        when(extUserMapper.listWecomUsersByOrganizationId("org-1")).thenReturn(List.of(existing));
        when(wecomContactClient.executeWithToken(anyString(), anyString(), any())).thenAnswer(invocation -> {
            Function<String, List<WecomUserDTO>> action = invocation.getArgument(2);
            return action.apply("token");
        });
        when(wecomContactClient.listDepartmentUsers("token", 1L, true))
                .thenReturn(List.of(user("zhangsan", "张三", null, "zhangsan@example.com", 1L, 1)));

        SyncPartResult result = userSyncHandler.sync("org-1", "admin", "corp", "secret");

        Assertions.assertEquals(0, result.getCreated());
        Assertions.assertEquals(0, result.getUpdated());
        verify(userMapper, never()).updateByPrimaryKeySelective(any(User.class));
        Assertions.assertEquals("13800000001", existing.getPhone());
    }

    @Test
    void sync_deletedUser_disablesLocalUser() {
        mockDepartments();
        User stale = buildUser("user-stale", "lisi", "13800000002");
        when(extUserMapper.listWecomUsersByOrganizationId("org-1")).thenReturn(List.of(stale));
        when(extUserMapper.selectByWecomUserid("zhangsan")).thenReturn(null);
        when(wecomContactClient.executeWithToken(anyString(), anyString(), any())).thenAnswer(invocation -> {
            Function<String, List<WecomUserDTO>> action = invocation.getArgument(2);
            return action.apply("token");
        });
        when(wecomContactClient.listDepartmentUsers("token", 1L, true))
                .thenReturn(List.of(user("zhangsan", "张三", "13800000001", "zhangsan@example.com", 1L, 1)));
        doAnswer(invocation -> {
            UserBatchCreateRequest request = invocation.getArgument(0);
            request.getUserInfoList().getFirst().setId("user-new");
            return new UserBatchCreateResponse();
        }).when(simpleUserService).addUser(any(UserBatchCreateRequest.class), anyString(), anyString());

        SyncPartResult result = userSyncHandler.sync("org-1", "admin", "corp", "secret");

        Assertions.assertEquals(1, result.getDisabled());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper, org.mockito.Mockito.atLeastOnce()).updateByPrimaryKeySelective(captor.capture());
        Assertions.assertTrue(captor.getAllValues().stream()
                .anyMatch(item -> "user-stale".equals(item.getId()) && Boolean.FALSE.equals(item.getEnable())));
    }

    @Test
    void sync_emptyUserList_skipsDeactivation() {
        mockDepartments();
        User stale = buildUser("user-stale", "lisi", "13800000002");
        when(extUserMapper.listWecomUsersByOrganizationId("org-1")).thenReturn(List.of(stale));
        when(wecomContactClient.executeWithToken(anyString(), anyString(), any())).thenAnswer(invocation -> {
            Function<String, List<WecomUserDTO>> action = invocation.getArgument(2);
            return action.apply("token");
        });
        when(wecomContactClient.listDepartmentUsers("token", 1L, true)).thenReturn(List.of());

        SyncPartResult result = userSyncHandler.sync("org-1", "admin", "corp", "secret");

        Assertions.assertEquals(0, result.getDisabled());
        Assertions.assertTrue(result.getErrorMessage().contains("跳过用户失活收敛"));
        verify(userMapper, never()).updateByPrimaryKeySelective(any(User.class));
    }

    @Test
    void sync_protectedAdmin_notDeactivated() {
        mockDepartments();
        User admin = buildUser(OrgSyncConstants.PROTECTED_USER_ID, "admin-user", "13800000003");
        when(extUserMapper.listWecomUsersByOrganizationId("org-1")).thenReturn(List.of(admin));
        when(wecomContactClient.executeWithToken(anyString(), anyString(), any())).thenAnswer(invocation -> {
            Function<String, List<WecomUserDTO>> action = invocation.getArgument(2);
            return action.apply("token");
        });
        when(wecomContactClient.listDepartmentUsers("token", 1L, true)).thenReturn(List.of());

        SyncPartResult result = userSyncHandler.sync("org-1", "admin", "corp", "secret");

        Assertions.assertEquals(0, result.getDisabled());
        verify(userMapper, never()).updateByPrimaryKeySelective(eq(admin));
    }

    private void mockDepartments() {
        Department department = new Department();
        department.setId("dept-1");
        department.setWecomDeptId(1L);
        when(extDepartmentMapper.listByOrganizationId("org-1")).thenReturn(List.of(department));
    }

    private WecomUserDTO user(String userid, String name, String mobile, String email, Long deptId, Integer status) {
        WecomUserDTO dto = new WecomUserDTO();
        dto.setUserid(userid);
        dto.setName(name);
        dto.setMobile(mobile);
        dto.setEmail(email);
        dto.setDepartment(List.of(deptId));
        dto.setStatus(status);
        return dto;
    }

    private User buildUser(String id, String wecomUserid, String phone) {
        User user = new User();
        user.setId(id);
        user.setWecomUserid(wecomUserid);
        user.setPhone(phone);
        user.setEnable(true);
        user.setName(wecomUserid);
        user.setSyncStatus(1);
        return user;
    }
}
