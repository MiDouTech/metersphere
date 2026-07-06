package io.metersphere.system.service.department;

import io.metersphere.system.domain.Department;
import io.metersphere.system.dto.department.DepartmentTreeNode;
import io.metersphere.system.dto.department.DepartmentUserCountDTO;
import io.metersphere.system.mapper.ExtDepartmentMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentQueryServiceTest {

    @InjectMocks
    private DepartmentQueryService departmentQueryService;
    @Mock
    private ExtDepartmentMapper extDepartmentMapper;
    @Mock
    private OrgStructureAccessService orgStructureAccessService;

    @Test
    void getTree_emptyOrganization() {
        doNothing().when(orgStructureAccessService).validateReadable(anyString());
        when(extDepartmentMapper.listByOrganizationId("org-empty")).thenReturn(new ArrayList<>());
        List<DepartmentTreeNode> tree = departmentQueryService.getTree("org-empty");
        Assertions.assertTrue(tree.isEmpty());
    }

    @Test
    void getTreeWithStats_threeLevelTreeAndCounts() {
        doNothing().when(orgStructureAccessService).validateReadable(anyString());
        when(extDepartmentMapper.listByOrganizationId("org-1")).thenReturn(buildDepartments());
        when(extDepartmentMapper.countDirectUsersByOrganizationId("org-1")).thenReturn(buildCounts());

        List<DepartmentTreeNode> tree = departmentQueryService.getTreeWithStats("org-1");
        Assertions.assertEquals(1, tree.size());
        DepartmentTreeNode root = tree.getFirst();
        Assertions.assertEquals("root", root.getId());
        Assertions.assertEquals(2, root.getChildren().size());

        DepartmentTreeNode childA = root.getChildren().stream().filter(item -> "child-a".equals(item.getId())).findFirst().orElseThrow();
        Assertions.assertEquals(1, childA.getChildren().size());
        Assertions.assertEquals("grandchild", childA.getChildren().getFirst().getId());

        Assertions.assertEquals(2L, childA.getDirectUserCount());
        Assertions.assertEquals(3L, childA.getTotalUserCount());
        Assertions.assertEquals(4L, root.getTotalUserCount());
    }

    private List<Department> buildDepartments() {
        List<Department> departments = new ArrayList<>();
        departments.add(dept("root", null, 1));
        departments.add(dept("child-a", "root", 1));
        departments.add(dept("child-b", "root", 2));
        departments.add(dept("grandchild", "child-a", 1));
        return departments;
    }

    private Department dept(String id, String parentId, int sortOrder) {
        Department department = new Department();
        department.setId(id);
        department.setParentId(parentId);
        department.setName(id);
        department.setSortOrder(sortOrder);
        department.setDeptStatus(1);
        department.setSyncStatus(1);
        department.setSyncTime(1L);
        return department;
    }

    private List<DepartmentUserCountDTO> buildCounts() {
        List<DepartmentUserCountDTO> counts = new ArrayList<>();
        counts.add(count("child-a", 2L));
        counts.add(count("child-b", 1L));
        counts.add(count("grandchild", 1L));
        return counts;
    }

    private DepartmentUserCountDTO count(String departmentId, Long userCount) {
        DepartmentUserCountDTO dto = new DepartmentUserCountDTO();
        dto.setDepartmentId(departmentId);
        dto.setUserCount(userCount);
        return dto;
    }
}
