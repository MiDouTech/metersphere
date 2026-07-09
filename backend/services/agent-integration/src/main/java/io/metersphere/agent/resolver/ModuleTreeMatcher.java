package io.metersphere.agent.resolver;

import io.metersphere.functional.service.FunctionalCaseModuleService;
import io.metersphere.system.dto.sdk.BaseTreeNode;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class ModuleTreeMatcher {
    @Resource
    private FunctionalCaseModuleService functionalCaseModuleService;

    public ModuleMatchResult match(String projectId, String query) {
        if (StringUtils.isBlank(query)) {
            return ModuleMatchResult.miss();
        }
        List<BaseTreeNode> tree = functionalCaseModuleService.getTree(projectId);
        if (CollectionUtils.isEmpty(tree)) {
            return ModuleMatchResult.miss();
        }
        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        Set<String> moduleIds = new LinkedHashSet<>();
        List<String> paths = new ArrayList<>();
        for (BaseTreeNode node : tree) {
            matchNode(node, normalizedQuery, moduleIds, paths);
        }
        if (moduleIds.isEmpty()) {
            return ModuleMatchResult.miss();
        }
        return ModuleMatchResult.hit(moduleIds, paths);
    }

    private void matchNode(BaseTreeNode node, String query, Set<String> moduleIds, List<String> paths) {
        if (matches(node, query)) {
            paths.add(formatPath(node.getPath()));
            collectSubtreeIds(node, moduleIds);
        }
        if (CollectionUtils.isNotEmpty(node.getChildren())) {
            for (BaseTreeNode child : node.getChildren()) {
                matchNode(child, query, moduleIds, paths);
            }
        }
    }

    private boolean matches(BaseTreeNode node, String query) {
        String name = StringUtils.defaultString(node.getName()).toLowerCase(Locale.ROOT);
        String path = formatPath(node.getPath()).toLowerCase(Locale.ROOT);
        return name.contains(query) || path.contains(query);
    }

    private void collectSubtreeIds(BaseTreeNode node, Set<String> moduleIds) {
        moduleIds.add(node.getId());
        if (CollectionUtils.isNotEmpty(node.getChildren())) {
            for (BaseTreeNode child : node.getChildren()) {
                collectSubtreeIds(child, moduleIds);
            }
        }
    }

    public List<AgentModuleNode> flatten(String projectId) {
        List<BaseTreeNode> tree = functionalCaseModuleService.getTree(projectId);
        List<AgentModuleNode> result = new ArrayList<>();
        if (CollectionUtils.isEmpty(tree)) {
            return result;
        }
        for (BaseTreeNode node : tree) {
            flattenNode(node, result);
        }
        return result;
    }

    public Set<String> expandModuleSubtree(String projectId, String moduleId) {
        if (StringUtils.isBlank(moduleId)) {
            return Collections.emptySet();
        }
        List<BaseTreeNode> tree = functionalCaseModuleService.getTree(projectId);
        Set<String> moduleIds = new LinkedHashSet<>();
        for (BaseTreeNode node : tree) {
            collectSubtreeById(node, moduleId, moduleIds);
        }
        if (moduleIds.isEmpty()) {
            moduleIds.add(moduleId);
        }
        return moduleIds;
    }

    private void collectSubtreeById(BaseTreeNode node, String targetModuleId, Set<String> moduleIds) {
        if (StringUtils.equals(node.getId(), targetModuleId)) {
            collectSubtreeIds(node, moduleIds);
            return;
        }
        if (CollectionUtils.isNotEmpty(node.getChildren())) {
            for (BaseTreeNode child : node.getChildren()) {
                collectSubtreeById(child, targetModuleId, moduleIds);
            }
        }
    }

    private void flattenNode(BaseTreeNode node, List<AgentModuleNode> result) {
        AgentModuleNode moduleNode = new AgentModuleNode();
        moduleNode.setId(node.getId());
        moduleNode.setName(node.getName());
        moduleNode.setParentId(node.getParentId());
        moduleNode.setPath(formatPath(node.getPath()));
        result.add(moduleNode);
        if (CollectionUtils.isNotEmpty(node.getChildren())) {
            for (BaseTreeNode child : node.getChildren()) {
                flattenNode(child, result);
            }
        }
    }

    private String formatPath(String path) {
        if (StringUtils.isBlank(path)) {
            return "";
        }
        return StringUtils.removeStart(path, "/");
    }

    @lombok.Data
    public static class ModuleMatchResult {
        private boolean hit;
        private Set<String> moduleIds = new LinkedHashSet<>();
        private List<String> paths = new ArrayList<>();

        public static ModuleMatchResult miss() {
            ModuleMatchResult result = new ModuleMatchResult();
            result.setHit(false);
            return result;
        }

        public static ModuleMatchResult hit(Set<String> moduleIds, List<String> paths) {
            ModuleMatchResult result = new ModuleMatchResult();
            result.setHit(true);
            result.setModuleIds(moduleIds);
            result.setPaths(paths);
            return result;
        }
    }

    @lombok.Data
    public static class AgentModuleNode {
        private String id;
        private String name;
        private String path;
        private String parentId;
    }
}
