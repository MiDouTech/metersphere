package io.metersphere.functional.hub.service;

import io.metersphere.sdk.constants.DefaultHubConstants;
import io.metersphere.sdk.constants.ModuleConstants;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 业务模块路径 ↔ 枢纽/目标项目模块映射（JDBC 写 module_type）
 */
@Service
public class DefaultHubModuleResolver {

    @Resource
    private JdbcTemplate jdbcTemplate;

    public String findHubFolderId(String hubProjectId, String bizProjectId) {
        List<String> ids = jdbcTemplate.queryForList(
                "SELECT id FROM functional_case_module WHERE project_id = ? AND ref_project_id = ? AND module_type = ? LIMIT 1",
                String.class, hubProjectId, bizProjectId, DefaultHubConstants.MODULE_TYPE_FOLDER);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    /** 业务用例 module → 枢纽 folder 下对应子模块（按需创建 MODULE 节点） */
    public String resolveHubModuleId(String bizProjectId, String bizModuleId, String hubProjectId, String operator) {
        String hubFolderId = findHubFolderId(hubProjectId, bizProjectId);
        if (StringUtils.isBlank(hubFolderId)) {
            return null;
        }
        if (StringUtils.isBlank(bizModuleId)) {
            return hubFolderId;
        }
        List<String> pathNames = buildModulePathNames(bizProjectId, bizModuleId);
        String parentId = hubFolderId;
        for (String name : pathNames) {
            parentId = findOrCreateModule(hubProjectId, parentId, name, null, operator);
        }
        return parentId;
    }

    /** 枢纽用例 module → 目标项目 self 根下对应路径 */
    public String resolveTargetModuleId(String hubProjectId, String hubModuleId, String targetProjectId, String operator) {
        String targetRootFolderId = findSelfRootFolder(targetProjectId);
        if (StringUtils.isBlank(targetRootFolderId)) {
            targetRootFolderId = findOrCreateModule(targetProjectId, ModuleConstants.ROOT_NODE_PARENT_ID,
                    "导入", null, operator);
        }
        if (StringUtils.isBlank(hubModuleId)) {
            return targetRootFolderId;
        }
        List<String> pathNames = buildRelativePathFromHubFolder(hubProjectId, hubModuleId);
        String parentId = targetRootFolderId;
        for (String name : pathNames) {
            parentId = findOrCreateModule(targetProjectId, parentId, name, null, operator);
        }
        return parentId;
    }

    public List<String> listDescendantModuleIds(String projectId, List<String> rootModuleIds) {
        if (rootModuleIds == null || rootModuleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> all = new ArrayList<>(rootModuleIds);
        List<String> frontier = new ArrayList<>(rootModuleIds);
        while (!frontier.isEmpty()) {
            String placeholders = frontier.stream().map(id -> "?").collect(Collectors.joining(","));
            List<String> children = jdbcTemplate.queryForList(
                    "SELECT id FROM functional_case_module WHERE project_id = ? AND parent_id IN (" + placeholders + ")",
                    String.class, concat(projectId, frontier));
            frontier = children.stream().filter(id -> !all.contains(id)).toList();
            all.addAll(frontier);
        }
        return all;
    }

    private Object[] concat(String projectId, List<String> ids) {
        Object[] args = new Object[ids.size() + 1];
        args[0] = projectId;
        for (int i = 0; i < ids.size(); i++) {
            args[i + 1] = ids.get(i);
        }
        return args;
    }

    private String findSelfRootFolder(String projectId) {
        List<String> ids = jdbcTemplate.queryForList(
                "SELECT id FROM functional_case_module WHERE project_id = ? AND parent_id = ? AND module_type = ? " +
                        "AND (ref_project_id IS NULL OR ref_project_id = '') LIMIT 1",
                String.class, projectId, ModuleConstants.ROOT_NODE_PARENT_ID, DefaultHubConstants.MODULE_TYPE_FOLDER);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private List<String> buildModulePathNames(String projectId, String moduleId) {
        Map<String, ModuleNode> nodes = loadModules(projectId);
        List<String> names = new ArrayList<>();
        String current = moduleId;
        while (current != null && nodes.containsKey(current)) {
            ModuleNode node = nodes.get(current);
            if (StringUtils.equals(node.parentId, ModuleConstants.ROOT_NODE_PARENT_ID)) {
                break;
            }
            names.add(node.name);
            current = node.parentId;
        }
        Collections.reverse(names);
        return names;
    }

    private List<String> buildRelativePathFromHubFolder(String hubProjectId, String hubModuleId) {
        Map<String, ModuleNode> nodes = loadModules(hubProjectId);
        List<String> names = new ArrayList<>();
        String current = hubModuleId;
        while (current != null && nodes.containsKey(current)) {
            ModuleNode node = nodes.get(current);
            if (DefaultHubConstants.MODULE_TYPE_FOLDER.equals(node.moduleType)
                    && StringUtils.isNotBlank(node.refProjectId)) {
                break;
            }
            if (StringUtils.equals(node.parentId, ModuleConstants.ROOT_NODE_PARENT_ID)) {
                break;
            }
            names.add(node.name);
            current = node.parentId;
        }
        Collections.reverse(names);
        return names;
    }

    private Map<String, ModuleNode> loadModules(String projectId) {
        return jdbcTemplate.query(
                "SELECT id, name, parent_id, module_type, ref_project_id FROM functional_case_module WHERE project_id = ?",
                rs -> {
                    Map<String, ModuleNode> map = new java.util.HashMap<>();
                    while (rs.next()) {
                        ModuleNode n = new ModuleNode();
                        n.id = rs.getString(1);
                        n.name = rs.getString(2);
                        n.parentId = rs.getString(3);
                        n.moduleType = rs.getString(4);
                        n.refProjectId = rs.getString(5);
                        map.put(n.id, n);
                    }
                    return map;
                }, projectId);
    }

    private String findOrCreateModule(String projectId, String parentId, String name, String refProjectId, String operator) {
        List<String> exist = jdbcTemplate.queryForList(
                "SELECT id FROM functional_case_module WHERE project_id = ? AND parent_id = ? AND name = ? LIMIT 1",
                String.class, projectId, parentId, name);
        if (!exist.isEmpty()) {
            return exist.getFirst();
        }
        long now = System.currentTimeMillis();
        Long maxPos = jdbcTemplate.query("SELECT MAX(pos) FROM functional_case_module WHERE parent_id = ?",
                rs -> rs.next() ? rs.getLong(1) : null, parentId);
        long pos = (maxPos == null || maxPos == 0) ? 64L : maxPos + 64L;
        String id = IDGenerator.nextStr();
        jdbcTemplate.update(
                "INSERT INTO functional_case_module (id, project_id, name, parent_id, module_type, ref_project_id, pos, create_time, update_time, create_user, update_user) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                id, projectId, name, parentId, DefaultHubConstants.MODULE_TYPE_MODULE, refProjectId,
                pos, now, now, operator, operator);
        return id;
    }

    private static class ModuleNode {
        String id;
        String name;
        String parentId;
        String moduleType;
        String refProjectId;
    }
}
