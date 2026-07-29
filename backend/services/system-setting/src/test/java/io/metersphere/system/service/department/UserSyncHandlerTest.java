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
    void sync_newUser_createsWithPlaceholderEmailAndNoPhone() {
        mockDepartments();
        when(extUserMapper.listWecomUsersByOrganizationId("org-1")).thenReturn(new ArrayList<>());
        when(extUserMapper.selectByWecomUserid("zhangsan")).thenReturn(null);
        mockTokenUsers(List.of(user("zhangsan", "张三", "13800000001", "zhangsan@example.com", null, 1L, null, 1)));
        doAnswer(invocation -> {
            UserBatchCreateRequest request = invocation.getArgument(0);
            request.getUserInfoList().getFirst().setId("user-1");
            return new UserBatchCreateResponse();
        }).when(simpleUserService).addUser(any(UserBatchCreateRequest.class), anyString(), anyString());

        SyncPartResult result = userSyncHandler.sync("org-1", "admin", "corp", "secret");

        Assertions.assertEquals(1, result.getCreated());
        ArgumentCaptor<UserBatchCreateRequest> createCaptor = ArgumentCaptor.forClass(UserBatchCreateRequest.class);
        verify(simpleUserService).addUser(createCaptor.capture(), anyString(), eq("admin"));
        Assertions.assertEquals("zhangsan@wecom.sync.internal",
                createCaptor.getValue().getUserInfoList().getFirst().getEmail());
        Assertions.assertNull(createCaptor.getValue().getUserInfoList().getFirst().getPhone());
        verify(organizationService).addMemberBySystem(any(OrganizationMemberRequest.class), eq("admin"));
    }

    @Test
    void sync_newUser_bizMailIgnored_stillPlaceholder() {
        mockDepartments();
        when(extUserMapper.listWecomUsersByOrganizationId("org-1")).thenReturn(new ArrayList<>());
        when(extUserMapper.selectByWecomUserid("lisi")).thenReturn(null);
        mockTokenUsers(List.of(user("lisi", "李四", null, null, "lisi@corp.com", 1L, null, 1)));
        doAnswer(invocation -> {
            UserBatchCreateRequest request = invocation.getArgument(0);
            request.getUserInfoList().getFirst().setId("user-2");
            return new UserBatchCreateResponse();
        }).when(simpleUserService).addUser(any(UserBatchCreateRequest.class), anyString(), anyString());

        userSyncHandler.sync("org-1", "admin", "corp", "secret");

        ArgumentCaptor<UserBatchCreateRequest> createCaptor = ArgumentCaptor.forClass(UserBatchCreateRequest.class);
        verify(simpleUserService).addUser(createCaptor.capture(), anyString(), anyString());
        Assertions.assertEquals("lisi@wecom.sync.internal",
                createCaptor.getValue().getUserInfoList().getFirst().getEmail());
    }

    @Test
    void sync_existingUser_doesNotUpdateEmailOrPhone() {
        mockDepartments();
        User existing = buildUser("user-1", "zhangsan", "13800000001");
        existing.setName("张三");
        existing.setDepartmentId("dept-1");
        existing.setEmail("old@example.com");
        when(extUserMapper.listWecomUsersByOrganizationId("org-1")).thenReturn(List.of(existing));
        mockTokenUsers(List.of(user("zhangsan", "张三", "13900000001", "new@corp.com", null, 1L, null, 1)));

        SyncPartResult result = userSyncHandler.sync("org-1", "admin", "corp", "secret");

        Assertions.assertEquals(0, result.getUpdated());
        Assertions.assertEquals("old@example.com", existing.getEmail());
        Assertions.assertEquals("13800000001", existing.getPhone());
        verify(userMapper, never()).updateByPrimaryKeySelective(any(User.class));
    }

    @Test
    void sync_existingUser_updatesNameOnly() {
        mockDepartments();
        User existing = buildUser("user-1", "zhangsan", "13800000001");
        existing.setName("张三");
        existing.setDepartmentId("dept-1");
        existing.setEmail("old@example.com");
        when(extUserMapper.listWecomUsersByOrganizationId("org-1")).thenReturn(List.of(existing));
        mockTokenUsers(List.of(user("zhangsan", "张三改名", "13900000001", "new@corp.com", null, 1L, null, 1)));

        SyncPartResult result = userSyncHandler.sync("org-1", "admin", "corp", "secret");

        Assertions.assertEquals(1, result.getUpdated());
        Assertions.assertEquals("张三改名", existing.getName());
        Assertions.assertEquals("old@example.com", existing.getEmail());
        Assertions.assertEquals("13800000001", existing.getPhone());
    }

    @Test
    void sync_mainDepartmentPreferred() {
        Department dept1 = new Department();
        dept1.setId("dept-1");
        dept1.setWecomDeptId(1L);
        Department dept2 = new Department();
        dept2.setId("dept-2");
        dept2.setWecomDeptId(2L);
        when(extDepartmentMapper.listByOrganizationId("org-1")).thenReturn(List.of(dept1, dept2));
        when(extUserMapper.listWecomUsersByOrganizationId("org-1")).thenReturn(new ArrayList<>());
        when(extUserMapper.selectByWecomUserid("zhangsan")).thenReturn(null);
        mockTokenUsers(List.of(user("zhangsan", "张三", "13800000001", "zhangsan@example.com", null, 1L, 2L, 1)));
        doAnswer(invocation -> {
            UserBatchCreateRequest request = invocation.getArgument(0);
            request.getUserInfoList().getFirst().setId("user-1");
            return new UserBatchCreateResponse();
        }).when(simpleUserService).addUser(any(UserBatchCreateRequest.class), anyString(), anyString());

        userSyncHandler.sync("org-1", "admin", "corp", "secret");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateByPrimaryKeySelective(captor.capture());
        Assertions.assertEquals("dept-2", captor.getValue().getDepartmentId());
        Assertions.assertEquals("zhangsan", captor.getValue().getWecomUserid());
    }

    @Test
    void sync_emptyUserList_skipsDeactivation() {
        mockDepartments();
        User stale = buildUser("user-stale", "lisi", "13800000002");
        when(extUserMapper.listWecomUsersByOrganizationId("org-1")).thenReturn(List.of(stale));
        mockTokenUsers(List.of());

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
        mockTokenUsers(List.of());

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

    private void mockTokenUsers(List<WecomUserDTO> users) {
        when(wecomContactClient.executeWithToken(anyString(), anyString(), any())).thenAnswer(invocation -> {
            Function<String, List<WecomUserDTO>> action = invocation.getArgument(2);
            return action.apply("token");
        });
        when(wecomContactClient.listDepartmentUsers("token", 1L, true)).thenReturn(users);
    }

    private WecomUserDTO user(String userid, String name, String mobile, String email, String bizMail,
                              Long deptId, Long mainDept, Integer status) {
        WecomUserDTO dto = new WecomUserDTO();
        dto.setUserid(userid);
        dto.setName(name);
        dto.setMobile(mobile);
        dto.setEmail(email);
        dto.setBizMail(bizMail);
        dto.setDepartment(List.of(deptId));
        dto.setMainDepartment(mainDept);
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
