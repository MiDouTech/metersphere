package io.metersphere.system.service.department;

import io.metersphere.system.domain.Department;
import io.metersphere.system.dto.department.DepartmentTreeNode;
import io.metersphere.system.dto.department.DepartmentUserCountDTO;
import io.metersphere.system.mapper.ExtDepartmentMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DepartmentQueryService {

    private static final int ENABLED = 1;

    @Resource
    private ExtDepartmentMapper extDepartmentMapper;
    @Resource
    private OrgStructureAccessService orgStructureAccessService;

    public List<DepartmentTreeNode> getTree(String organizationId) {
        orgStructureAccessService.validateReadable(organizationId);
        List<Department> departments = extDepartmentMapper.listByOrganizationId(organizationId).stream()
                .filter(item -> Objects.equals(item.getDeptStatus(), ENABLED))
                .collect(Collectors.toList());
        return buildTree(departments, false, organizationId);
    }

    public List<DepartmentTreeNode> getTreeWithStats(String organizationId) {
        orgStructureAccessService.validateReadable(organizationId);
        List<Department> departments = extDepartmentMapper.listByOrganizationId(organizationId);
        return buildTree(departments, true, organizationId);
    }

    private List<DepartmentTreeNode> buildTree(List<Department> departments, boolean withStats, String organizationId) {
        if (departments.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, DepartmentTreeNode> nodeMap = new LinkedHashMap<>();
        for (Department department : departments) {
            nodeMap.put(department.getId(), toNode(department, withStats));
        }
        List<DepartmentTreeNode> roots = new ArrayList<>();
        for (DepartmentTreeNode node : nodeMap.values()) {
            if (StringUtils.isBlank(node.getParentId()) || !nodeMap.containsKey(node.getParentId())) {
                roots.add(node);
            } else {
                nodeMap.get(node.getParentId()).getChildren().add(node);
            }
        }
        sortTree(roots);
        if (withStats) {
            Map<String, Long> directCounts = loadDirectUserCounts(organizationId);
            for (DepartmentTreeNode root : roots) {
                fillStats(root, directCounts);
            }
        }
        return roots;
    }

    private Map<String, Long> loadDirectUserCounts(String organizationId) {
        Map<String, Long> counts = new HashMap<>();
        for (DepartmentUserCountDTO item : extDepartmentMapper.countDirectUsersByOrganizationId(organizationId)) {
            counts.put(item.getDepartmentId(), item.getUserCount());
        }
        return counts;
    }

    private long fillStats(DepartmentTreeNode node, Map<String, Long> directCounts) {
        long direct = directCounts.getOrDefault(node.getId(), 0L);
        node.setDirectUserCount(direct);
        long total = direct;
        for (DepartmentTreeNode child : node.getChildren()) {
            total += fillStats(child, directCounts);
        }
        node.setTotalUserCount(total);
        return total;
    }

    private void sortTree(List<DepartmentTreeNode> nodes) {
        nodes.sort(Comparator
                .comparing(DepartmentTreeNode::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(DepartmentTreeNode::getName, Comparator.nullsLast(String::compareTo)));
        for (DepartmentTreeNode node : nodes) {
            sortTree(node.getChildren());
        }
    }

    private DepartmentTreeNode toNode(Department department, boolean withStats) {
        DepartmentTreeNode node = new DepartmentTreeNode();
        node.setId(department.getId());
        node.setName(department.getName());
        node.setParentId(department.getParentId());
        node.setSortOrder(department.getSortOrder());
        if (withStats) {
            node.setDeptStatus(department.getDeptStatus());
            node.setSyncStatus(department.getSyncStatus());
            node.setSyncTime(department.getSyncTime());
        }
        return node;
    }
}
