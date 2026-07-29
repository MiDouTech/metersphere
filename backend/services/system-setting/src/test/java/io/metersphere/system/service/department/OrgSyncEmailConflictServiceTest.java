package io.metersphere.system.service.department;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.User;
import io.metersphere.system.domain.UserExample;
import io.metersphere.system.dto.department.OrgSyncEmailConflictDTO;
import io.metersphere.system.mapper.UserMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrgSyncEmailConflictServiceTest {

    @InjectMocks
    private OrgSyncEmailConflictService orgSyncEmailConflictService;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private UserMapper userMapper;
    @Mock
    private io.metersphere.system.log.service.OperationLogService operationLogService;

    @Test
    void resolve_skip_marksResolvedWithoutTouchingUsers() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("c-1")))
                .thenReturn(List.of(pending("c-1", OrgSyncConstants.EMAIL_CONFLICT_SCENE_UPDATE)));

        orgSyncEmailConflictService.resolve("c-1", "SKIP", "admin");

        verify(jdbcTemplate).update(
                contains("UPDATE org_sync_email_conflict SET status"),
                eq(OrgSyncConstants.EMAIL_CONFLICT_RESOLVED),
                eq(OrgSyncConstants.EMAIL_CONFLICT_SKIP),
                eq("admin"),
                anyLong(),
                anyLong(),
                eq("c-1"));
        verify(userMapper, never()).updateByPrimaryKeySelective(any(User.class));
    }

    @Test
    void resolve_create_allowedForCreateScene() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("c-2")))
                .thenReturn(List.of(pending("c-2", OrgSyncConstants.EMAIL_CONFLICT_SCENE_CREATE)));

        orgSyncEmailConflictService.resolve("c-2", "CREATE", "admin");

        verify(jdbcTemplate).update(
                contains("UPDATE org_sync_email_conflict SET status"),
                eq(OrgSyncConstants.EMAIL_CONFLICT_RESOLVED),
                eq(OrgSyncConstants.EMAIL_CONFLICT_CREATE),
                eq("admin"),
                anyLong(),
                anyLong(),
                eq("c-2"));
        verify(userMapper, never()).updateByPrimaryKeySelective(any(User.class));
    }

    @Test
    void resolve_create_rejectedForUpdateScene() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("c-3")))
                .thenReturn(List.of(pending("c-3", OrgSyncConstants.EMAIL_CONFLICT_SCENE_UPDATE)));

        MSException ex = Assertions.assertThrows(MSException.class,
                () -> orgSyncEmailConflictService.resolve("c-3", "CREATE", "admin"));
        Assertions.assertTrue(ex.getMessage().contains("请选择跳过或覆盖"));
        verify(userMapper, never()).updateByPrimaryKeySelective(any(User.class));
    }

    @Test
    void resolve_overwrite_movesEmailToPendingAndPlaceholderOccupied() {
        OrgSyncEmailConflictDTO conflict = pending("c-4", OrgSyncConstants.EMAIL_CONFLICT_SCENE_UPDATE);
        conflict.setPendingUserId("pending-1");
        conflict.setOccupiedUserId("occupied-1");
        conflict.setConflictEmail("same@corp.com");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("c-4")))
                .thenReturn(List.of(conflict));

        User pendingUser = new User();
        pendingUser.setId("pending-1");
        pendingUser.setEmail("pending@wecom.sync.internal");
        when(userMapper.selectByPrimaryKey("pending-1")).thenReturn(pendingUser);

        User occupied = new User();
        occupied.setId("occupied-1");
        occupied.setEmail("same@corp.com");
        occupied.setWecomUserid("occ-wecom");
        occupied.setDeleted(false);
        when(userMapper.selectByPrimaryKey("occupied-1")).thenReturn(occupied);
        when(userMapper.selectByExample(any(UserExample.class))).thenReturn(List.of(occupied));

        orgSyncEmailConflictService.resolve("c-4", "OVERWRITE", "admin");

        ArgumentCaptor<User> updateCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper, org.mockito.Mockito.times(2)).updateByPrimaryKeySelective(updateCaptor.capture());
        List<User> updates = updateCaptor.getAllValues();
        Assertions.assertEquals("occupied-1", updates.get(0).getId());
        Assertions.assertTrue(OrgSyncConstants.isPlaceholderEmail(updates.get(0).getEmail()));
        Assertions.assertEquals("pending-1", updates.get(1).getId());
        Assertions.assertEquals("same@corp.com", updates.get(1).getEmail());
        verify(jdbcTemplate).update(
                contains("UPDATE org_sync_email_conflict SET status"),
                eq(OrgSyncConstants.EMAIL_CONFLICT_RESOLVED),
                eq(OrgSyncConstants.EMAIL_CONFLICT_OVERWRITE),
                eq("admin"),
                anyLong(),
                anyLong(),
                eq("c-4"));
    }

    private OrgSyncEmailConflictDTO pending(String id, String scene) {
        OrgSyncEmailConflictDTO dto = new OrgSyncEmailConflictDTO();
        dto.setId(id);
        dto.setOrganizationId("org-1");
        dto.setWecomUserid("zhangsan");
        dto.setConflictScene(scene);
        dto.setStatus(OrgSyncConstants.EMAIL_CONFLICT_PENDING);
        dto.setConflictEmail("same@corp.com");
        dto.setOccupiedUserId("occupied-1");
        return dto;
    }
}
