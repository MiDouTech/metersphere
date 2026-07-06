package io.metersphere.system.service.department;

import io.metersphere.system.domain.Department;
import io.metersphere.system.dto.wecom.WecomDepartmentDTO;
import io.metersphere.system.mapper.DepartmentMapper;
import io.metersphere.system.mapper.ExtDepartmentMapper;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DepartmentSyncHandler {

    @Resource
    private DepartmentMapper departmentMapper;
    @Resource
    private ExtDepartmentMapper extDepartmentMapper;

    public SyncPartResult sync(String organizationId, String operatorId, List<WecomDepartmentDTO> wecomDepartments) {
        SyncPartResult result = new SyncPartResult();
        if (wecomDepartments == null) {
            wecomDepartments = List.of();
        }
        long syncTime = System.currentTimeMillis();

        Map<Long, Department> existingMap = extDepartmentMapper.listByOrganizationId(organizationId).stream()
                .filter(item -> item.getWecomDeptId() != null)
                .collect(Collectors.toMap(Department::getWecomDeptId, Function.identity(), (left, right) -> left));
        Map<Long, String> wecomToLocalId = new HashMap<>();
        Set<Long> incomingDeptIds = new HashSet<>();

        for (WecomDepartmentDTO wecomDept : wecomDepartments) {
            if (wecomDept == null || wecomDept.getId() == null) {
                continue;
            }
            incomingDeptIds.add(wecomDept.getId());
            try {
                Department existing = existingMap.get(wecomDept.getId());
                if (existing == null) {
                    Department created = buildNewDepartment(organizationId, operatorId, wecomDept, syncTime);
                    departmentMapper.insert(created);
                    existingMap.put(wecomDept.getId(), created);
                    wecomToLocalId.put(wecomDept.getId(), created.getId());
                    result.setCreated(result.getCreated() + 1);
                    continue;
                }
                wecomToLocalId.put(wecomDept.getId(), existing.getId());
                if (updateDepartment(existing, wecomDept, operatorId, syncTime)) {
                    departmentMapper.updateByPrimaryKeySelective(existing);
                    result.setUpdated(result.getUpdated() + 1);
                }
            } catch (Exception ex) {
                result.setFailed(result.getFailed() + 1);
                result.appendError("部门[" + wecomDept.getId() + "]同步失败: " + ex.getMessage() + "; ");
            }
        }

        for (WecomDepartmentDTO wecomDept : wecomDepartments) {
            if (wecomDept == null || wecomDept.getId() == null) {
                continue;
            }
            Department current = existingMap.get(wecomDept.getId());
            if (current == null) {
                continue;
            }
            try {
                String targetParentId = resolveParentId(wecomDept.getParentid(), wecomToLocalId);
                if (!Objects.equals(targetParentId, current.getParentId())) {
                    current.setParentId(targetParentId);
                    current.setSyncTime(syncTime);
                    current.setUpdateTime(syncTime);
                    current.setUpdateUser(operatorId);
                    departmentMapper.updateByPrimaryKeySelective(current);
                    result.setUpdated(result.getUpdated() + 1);
                }
            } catch (Exception ex) {
                result.setFailed(result.getFailed() + 1);
                result.appendError("部门[" + wecomDept.getId() + "]父子关系同步失败: " + ex.getMessage() + "; ");
            }
        }

        List<Department> staleDepartments = new ArrayList<>();
        if (incomingDeptIds.isEmpty() && !existingMap.isEmpty()) {
            result.appendError("企微部门返回空列表，已跳过部门失活收敛; ");
        } else {
            staleDepartments = existingMap.values().stream()
                    .filter(item -> item.getWecomDeptId() != null && !incomingDeptIds.contains(item.getWecomDeptId()))
                    .toList();
            for (Department staleDept : staleDepartments) {
                try {
                    if (!Integer.valueOf(0).equals(staleDept.getDeptStatus())) {
                        staleDept.setDeptStatus(0);
                        staleDept.setSyncTime(syncTime);
                        staleDept.setUpdateTime(syncTime);
                        staleDept.setUpdateUser(operatorId);
                        departmentMapper.updateByPrimaryKeySelective(staleDept);
                        result.setDisabled(result.getDisabled() + 1);
                    }
                } catch (Exception ex) {
                    result.setFailed(result.getFailed() + 1);
                    result.appendError("部门[" + staleDept.getWecomDeptId() + "]失活失败: " + ex.getMessage() + "; ");
                }
            }
        }

        result.setTotal(incomingDeptIds.size() + staleDepartments.size());
        result.setSuccess(Math.max(result.getTotal() - result.getFailed(), 0));
        return result;
    }

    private Department buildNewDepartment(String organizationId, String operatorId,
                                            WecomDepartmentDTO wecomDept, long syncTime) {
        Department department = new Department();
        department.setId(IDGenerator.nextStr());
        department.setOrganizationId(organizationId);
        department.setName(trimToNull(wecomDept.getName()));
        department.setWecomDeptId(wecomDept.getId());
        department.setSortOrder(defaultOrder(wecomDept.getOrder()));
        department.setDeptStatus(1);
        department.setSyncStatus(1);
        department.setSyncTime(syncTime);
        department.setLeaderWecomUserid(resolveLeader(wecomDept));
        department.setCreateTime(syncTime);
        department.setUpdateTime(syncTime);
        department.setCreateUser(operatorId);
        department.setUpdateUser(operatorId);
        return department;
    }

    private boolean updateDepartment(Department existing, WecomDepartmentDTO wecomDept,
                                     String operatorId, long syncTime) {
        boolean changed = false;
        String latestName = trimToNull(wecomDept.getName());
        if (!Objects.equals(latestName, existing.getName())) {
            existing.setName(latestName);
            changed = true;
        }
        Integer latestOrder = defaultOrder(wecomDept.getOrder());
        if (!Objects.equals(latestOrder, existing.getSortOrder())) {
            existing.setSortOrder(latestOrder);
            changed = true;
        }
        String latestLeader = resolveLeader(wecomDept);
        if (!Objects.equals(latestLeader, existing.getLeaderWecomUserid())) {
            existing.setLeaderWecomUserid(latestLeader);
            changed = true;
        }
        if (!Integer.valueOf(1).equals(existing.getDeptStatus())) {
            existing.setDeptStatus(1);
            changed = true;
        }
        if (!Integer.valueOf(1).equals(existing.getSyncStatus())) {
            existing.setSyncStatus(1);
            changed = true;
        }
        if (changed) {
            existing.setSyncTime(syncTime);
            existing.setUpdateTime(syncTime);
            existing.setUpdateUser(operatorId);
        }
        return changed;
    }

    private String resolveParentId(Long wecomParentId, Map<Long, String> wecomToLocalId) {
        if (wecomParentId == null || wecomParentId <= 0) {
            return null;
        }
        return wecomToLocalId.get(wecomParentId);
    }

    private String resolveLeader(WecomDepartmentDTO wecomDept) {
        if (wecomDept == null || CollectionUtils.isEmpty(wecomDept.getDepartmentLeader())) {
            return null;
        }
        return trimToNull(wecomDept.getDepartmentLeader().getFirst());
    }

    private Integer defaultOrder(Long order) {
        return order == null ? 0 : order.intValue();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
