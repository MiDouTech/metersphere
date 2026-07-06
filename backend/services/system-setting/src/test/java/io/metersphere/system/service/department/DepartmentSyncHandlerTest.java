package io.metersphere.system.service.department;

import io.metersphere.system.domain.Department;
import io.metersphere.system.dto.wecom.WecomDepartmentDTO;
import io.metersphere.system.mapper.DepartmentMapper;
import io.metersphere.system.mapper.ExtDepartmentMapper;
import io.metersphere.system.uid.IDGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentSyncHandlerTest {

    @InjectMocks
    private DepartmentSyncHandler departmentSyncHandler;
    @Mock
    private DepartmentMapper departmentMapper;
    @Mock
    private ExtDepartmentMapper extDepartmentMapper;

    @Test
    void sync_firstFullSync_createsDepartmentsAndFixesParent() {
        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn("dept-new-1", "dept-new-2");
            when(extDepartmentMapper.listByOrganizationId("org-1")).thenReturn(new ArrayList<>());

            List<WecomDepartmentDTO> wecomDepartments = List.of(
                    department(1L, "Root", 0L),
                    department(2L, "研发部", 1L)
            );

            SyncPartResult result = departmentSyncHandler.sync("org-1", "admin", wecomDepartments);

            Assertions.assertEquals(2, result.getCreated());
            Assertions.assertEquals(1, result.getUpdated());
            Assertions.assertEquals(0, result.getFailed());
            verify(departmentMapper, times(2)).insert(any(Department.class));
            verify(departmentMapper, times(1)).updateByPrimaryKeySelective(any(Department.class));
        }
    }

    @Test
    void sync_repeatSync_noDuplicateInsert() {
        Department root = existing("dept-root", 1L, "Root", null);
        Department child = existing("dept-child", 2L, "研发部", "dept-root");
        when(extDepartmentMapper.listByOrganizationId("org-1")).thenReturn(List.of(root, child));

        SyncPartResult result = departmentSyncHandler.sync("org-1", "admin",
                List.of(department(1L, "Root", 0L), department(2L, "研发部", 1L)));

        Assertions.assertEquals(0, result.getCreated());
        Assertions.assertEquals(0, result.getUpdated());
        verify(departmentMapper, never()).insert(any(Department.class));
    }

    @Test
    void sync_deletedDepartment_disablesLocalDepartment() {
        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn("dept-root-new");
            Department stale = existing("dept-stale", 99L, "旧部门", null);
            when(extDepartmentMapper.listByOrganizationId("org-1")).thenReturn(List.of(stale));

            SyncPartResult result = departmentSyncHandler.sync("org-1", "admin", List.of(department(1L, "Root", 0L)));

            Assertions.assertEquals(1, result.getDisabled());
            ArgumentCaptor<Department> captor = ArgumentCaptor.forClass(Department.class);
            verify(departmentMapper, org.mockito.Mockito.atLeastOnce()).updateByPrimaryKeySelective(captor.capture());
            Assertions.assertTrue(captor.getAllValues().stream()
                    .anyMatch(item -> "dept-stale".equals(item.getId()) && Integer.valueOf(0).equals(item.getDeptStatus())));
        }
    }

    @Test
    void sync_emptyDepartmentList_skipsDeactivation() {
        Department stale = existing("dept-stale", 99L, "旧部门", null);
        when(extDepartmentMapper.listByOrganizationId("org-1")).thenReturn(List.of(stale));

        SyncPartResult result = departmentSyncHandler.sync("org-1", "admin", List.of());

        Assertions.assertEquals(0, result.getDisabled());
        Assertions.assertTrue(result.getErrorMessage().contains("跳过部门失活收敛"));
        verify(departmentMapper, never()).updateByPrimaryKeySelective(eq(stale));
    }

    private WecomDepartmentDTO department(Long id, String name, Long parentId) {
        WecomDepartmentDTO dto = new WecomDepartmentDTO();
        dto.setId(id);
        dto.setName(name);
        dto.setParentid(parentId);
        dto.setOrder(1L);
        return dto;
    }

    private Department existing(String id, Long wecomDeptId, String name, String parentId) {
        Department department = new Department();
        department.setId(id);
        department.setOrganizationId("org-1");
        department.setWecomDeptId(wecomDeptId);
        department.setName(name);
        department.setParentId(parentId);
        department.setDeptStatus(1);
        department.setSyncStatus(1);
        department.setSortOrder(1);
        return department;
    }
}
