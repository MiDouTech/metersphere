package io.metersphere.dashboard.service;

import io.metersphere.project.domain.Project;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.dto.user.UserDTO;
import io.metersphere.system.service.PermissionCheckService;
import jakarta.annotation.Resource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class DashboardProjectService {

    @Resource
    private PermissionCheckService permissionCheckService;

    public static final String API_TEST = "apiTest";
    public static final String TEST_PLAN = "testPlan";
    public static final String FUNCTIONAL_CASE = "caseManagement";
    public static final String BUG = "bugManagement";

    /**
     * 获取用户组织内有只读权限且开启相关模块的项目
     *
     * @param userId 当前用户
     * @return 只读权限对应的开启模块的项目ids
     */
    public Map<String, Set<String>> getPermissionModuleProjectIds(List<Project> projects, String userId) {
        boolean isAdmin = isAdmin(userId);
        Set<String> projectSet = projects.stream().map(Project::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, String> moduleMap = toModuleMap(projects);
        Map<String, Set<String>> hasModuleProjectIds = new HashMap<>();
        Set<String> searchCaseProjectIds = new LinkedHashSet<>();
        Set<String> searchReviewProjectIds = new LinkedHashSet<>();
        Set<String> searchApiProjectIds = new LinkedHashSet<>();
        Set<String> searchApiCaseProjectIds = new LinkedHashSet<>();
        Set<String> searchScenarioProjectIds = new LinkedHashSet<>();
        Set<String> searchPlanProjectIds = new LinkedHashSet<>();
        Set<String> searchBugProjectIds = new LinkedHashSet<>();
        //查出用户在选中的项目中有读取权限的, admin所有项目都有权限
        if (!isAdmin) {
            Set<String> permissionSet = getPermissionSet();
            Map<String, Set<String>> hasUserPermissionProjectIds = permissionCheckService.getHasUserPermissionProjectIds(userId, projectSet, permissionSet);
            //查出这些项目分别有模块的
            Set<String> functionalProjectIds = hasUserPermissionProjectIds.get(PermissionConstants.FUNCTIONAL_CASE_READ);
            //检查是否开启功能用例模块
            if (CollectionUtils.isNotEmpty(functionalProjectIds)) {
                searchCaseProjectIds = filterByModule(functionalProjectIds, moduleMap, FUNCTIONAL_CASE);
            }
            Set<String> reviewProjectIds = hasUserPermissionProjectIds.get(PermissionConstants.CASE_REVIEW_READ);
            if (CollectionUtils.isNotEmpty(reviewProjectIds)) {
                searchReviewProjectIds = filterByModule(reviewProjectIds, moduleMap, FUNCTIONAL_CASE);
            }
            //检查是否开启接口模块
            Set<String> apiProjectIds = hasUserPermissionProjectIds.get(PermissionConstants.PROJECT_API_DEFINITION_READ);
            if (CollectionUtils.isNotEmpty(apiProjectIds)) {
                searchApiProjectIds = filterByModule(apiProjectIds, moduleMap, API_TEST);
            }
            Set<String> apiCaseProjectIds = hasUserPermissionProjectIds.get(PermissionConstants.PROJECT_API_DEFINITION_CASE_READ);
            if (CollectionUtils.isNotEmpty(apiCaseProjectIds)) {
                searchApiCaseProjectIds = filterByModule(apiCaseProjectIds, moduleMap, API_TEST);
            }
            Set<String> scenarioProjectIds = hasUserPermissionProjectIds.get(PermissionConstants.PROJECT_API_SCENARIO_READ);
            if (CollectionUtils.isNotEmpty(scenarioProjectIds)) {
                searchScenarioProjectIds = filterByModule(scenarioProjectIds, moduleMap, API_TEST);
            }
            //检查是否开启测试计划模块
            Set<String> planProjectIds = hasUserPermissionProjectIds.get(PermissionConstants.TEST_PLAN_READ);
            if (CollectionUtils.isNotEmpty(planProjectIds)) {
                searchPlanProjectIds = filterByModule(planProjectIds, moduleMap, TEST_PLAN);
            }
            //检查是否开启缺陷模块
            Set<String> bugProjectIds = hasUserPermissionProjectIds.get(PermissionConstants.PROJECT_BUG_READ);
            if (CollectionUtils.isNotEmpty(bugProjectIds)) {
                searchBugProjectIds = filterByModule(bugProjectIds, moduleMap, BUG);
            }
        } else {
            //查出这些项目分别有模块的
            searchCaseProjectIds = filterByModule(projectSet, moduleMap, FUNCTIONAL_CASE);
            searchReviewProjectIds = filterByModule(projectSet, moduleMap, FUNCTIONAL_CASE);
            searchApiProjectIds = filterByModule(projectSet, moduleMap, API_TEST);
            searchApiCaseProjectIds = filterByModule(projectSet, moduleMap, API_TEST);
            searchScenarioProjectIds = filterByModule(projectSet, moduleMap, API_TEST);
            searchPlanProjectIds = filterByModule(projectSet, moduleMap, TEST_PLAN);
            searchBugProjectIds = filterByModule(projectSet, moduleMap, BUG);
        }
        //如果value 为空，则没有权限或者没开启模块
        hasModuleProjectIds.put(PermissionConstants.FUNCTIONAL_CASE_READ, searchCaseProjectIds);
        hasModuleProjectIds.put(PermissionConstants.CASE_REVIEW_READ, searchReviewProjectIds);
        hasModuleProjectIds.put(PermissionConstants.PROJECT_API_DEFINITION_READ, searchApiProjectIds);
        hasModuleProjectIds.put(PermissionConstants.PROJECT_API_DEFINITION_CASE_READ, searchApiCaseProjectIds);
        hasModuleProjectIds.put(PermissionConstants.PROJECT_API_SCENARIO_READ, searchScenarioProjectIds);
        hasModuleProjectIds.put(PermissionConstants.TEST_PLAN_READ, searchPlanProjectIds);
        hasModuleProjectIds.put(PermissionConstants.PROJECT_BUG_READ, searchBugProjectIds);

        return hasModuleProjectIds;
    }

    /**
     * 当前用户在组织内有任意权限的且开启模块的项目
     *
     * @param userProject 在组织内有任意权限项目
     * @return 模块开启对应的项目ids
     */
    public Map<String, Set<String>> getModuleProjectIds(List<Project> userProject) {
        Map<String, String> moduleMap = toModuleMap(userProject);
        Set<String> projectIds = userProject.stream().map(Project::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> searchCaseProjectIds = filterByModule(projectIds, moduleMap, FUNCTIONAL_CASE);
        Set<String> searchApiProjectIds = filterByModule(projectIds, moduleMap, API_TEST);
        Set<String> searchPlanProjectIds = filterByModule(projectIds, moduleMap, TEST_PLAN);
        Set<String> searchBugProjectIds = filterByModule(projectIds, moduleMap, BUG);
        Map<String, Set<String>> hasModuleProjectIds = new HashMap<>();
        hasModuleProjectIds.put(PermissionConstants.FUNCTIONAL_CASE_READ, searchCaseProjectIds);
        hasModuleProjectIds.put(PermissionConstants.CASE_REVIEW_READ, searchCaseProjectIds);
        hasModuleProjectIds.put(PermissionConstants.PROJECT_API_DEFINITION_READ, searchApiProjectIds);
        hasModuleProjectIds.put(PermissionConstants.PROJECT_API_DEFINITION_CASE_READ, searchApiProjectIds);
        hasModuleProjectIds.put(PermissionConstants.PROJECT_API_SCENARIO_READ, searchApiProjectIds);
        hasModuleProjectIds.put(PermissionConstants.TEST_PLAN_READ, searchPlanProjectIds);
        hasModuleProjectIds.put(PermissionConstants.PROJECT_BUG_READ, searchBugProjectIds);
        return hasModuleProjectIds;
    }

    /**
     * Collectors.toMap 不允许 value 为 null；空 module_setting 的项目须安全跳过模块匹配。
     */
    private static Map<String, String> toModuleMap(List<Project> projects) {
        Map<String, String> moduleMap = new HashMap<>();
        if (CollectionUtils.isEmpty(projects)) {
            return moduleMap;
        }
        for (Project project : projects) {
            moduleMap.put(project.getId(), StringUtils.defaultString(project.getModuleSetting()));
        }
        return moduleMap;
    }

    private static Set<String> filterByModule(Set<String> projectIds, Map<String, String> moduleMap, String module) {
        return projectIds.stream()
                .filter(id -> StringUtils.contains(moduleMap.get(id), module))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> getPermissionSet() {
        Set<String> permissionSet = new HashSet<>();
        permissionSet.add(PermissionConstants.FUNCTIONAL_CASE_READ);
        permissionSet.add(PermissionConstants.CASE_REVIEW_READ);
        permissionSet.add(PermissionConstants.PROJECT_API_DEFINITION_READ);
        permissionSet.add(PermissionConstants.PROJECT_API_DEFINITION_CASE_READ);
        permissionSet.add(PermissionConstants.PROJECT_API_SCENARIO_READ);
        permissionSet.add(PermissionConstants.TEST_PLAN_READ);
        permissionSet.add(PermissionConstants.PROJECT_BUG_READ);
        return permissionSet;
    }

    private boolean isAdmin(String userId) {
        UserDTO userDTO = permissionCheckService.getUserDTO(userId);
        if (userDTO == null) {
            return false;
        }
        return permissionCheckService.checkAdmin(userDTO);
    }
}
