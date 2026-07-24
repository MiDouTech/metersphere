package io.metersphere.system.service;

import io.metersphere.sdk.constants.DefaultHubConstants;
import io.metersphere.sdk.constants.ModuleConstants;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.system.mapper.ExtSystemProjectMapper;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 建项时在默认项目/本项目创建同名文件夹；改名/删除副作用。
 * 使用 JdbcTemplate 写 module_type/ref_project_id，避免改动生成 Mapper 全量 XML。
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class DefaultHubFolderService {

    @Resource
    private ExtSystemProjectMapper extSystemProjectMapper;
    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 业务项目创建成功后调用（失败不抛，仅日志）
     */
    public void onBizProjectCreated(String bizProjectId, String projectName, String operator) {
        try {
            createSelfFolder(bizProjectId, projectName, operator);
            String hubProjectId = extSystemProjectMapper.selectDefaultProjectId();
            if (StringUtils.isBlank(hubProjectId) || StringUtils.equals(hubProjectId, bizProjectId)) {
                return;
            }
            createOrSkipHubFolder(hubProjectId, bizProjectId, projectName, operator);
        } catch (Exception e) {
            LogUtils.error("default hub folder create failed, projectId=" + bizProjectId, e);
        }
    }

    public void onBizProjectRenamed(String bizProjectId, String oldName, String newName, String operator) {
        try {
            if (StringUtils.equals(oldName, newName)) {
                return;
            }
            String hubProjectId = extSystemProjectMapper.selectDefaultProjectId();
            if (StringUtils.isBlank(hubProjectId)) {
                return;
            }
            // 仅按 ref_project_id 定位
            Integer updated = jdbcTemplate.update(
                    "UPDATE functional_case_module SET name = ?, update_user = ?, update_time = ? " +
                            "WHERE project_id = ? AND ref_project_id = ? AND module_type = ?",
                    resolveUniqueName(hubProjectId, ModuleConstants.ROOT_NODE_PARENT_ID, newName, null),
                    operator,
                    System.currentTimeMillis(),
                    hubProjectId,
                    bizProjectId,
                    DefaultHubConstants.MODULE_TYPE_FOLDER
            );
            if (updated != null && updated == 0) {
                LogUtils.warn("hub folder rename skipped, no ref_project_id match: " + bizProjectId);
            }
            // 本项目同名根文件夹：尽量按旧名更新（非强制）
            jdbcTemplate.update(
                    "UPDATE functional_case_module SET name = ?, update_user = ?, update_time = ? " +
                            "WHERE project_id = ? AND parent_id = ? AND module_type = ? AND name = ? AND (ref_project_id IS NULL OR ref_project_id = '')",
                    newName, operator, System.currentTimeMillis(),
                    bizProjectId, ModuleConstants.ROOT_NODE_PARENT_ID, DefaultHubConstants.MODULE_TYPE_FOLDER, oldName
            );
        } catch (Exception e) {
            LogUtils.error("default hub folder rename failed, projectId=" + bizProjectId, e);
        }
    }

    public void onBizProjectDeleted(String bizProjectId, String operator) {
        try {
            String hubProjectId = extSystemProjectMapper.selectDefaultProjectId();
            if (StringUtils.isBlank(hubProjectId)) {
                return;
            }
            // 查出枢纽下对应文件夹，级联删除交由用例模块服务更安全；此处先删映射文件夹及其子孙（软删用例走 SQL）
            jdbcTemplate.query(
                    "SELECT id FROM functional_case_module WHERE project_id = ? AND ref_project_id = ? AND module_type = ?",
                    rs -> {
                        String folderId = rs.getString(1);
                        deleteModuleCascade(folderId, operator);
                    },
                    hubProjectId, bizProjectId, DefaultHubConstants.MODULE_TYPE_FOLDER
            );
        } catch (Exception e) {
            LogUtils.error("default hub folder delete failed, projectId=" + bizProjectId, e);
        }
    }

    private void createSelfFolder(String projectId, String name, String operator) {
        String unique = resolveUniqueName(projectId, ModuleConstants.ROOT_NODE_PARENT_ID, name, null);
        insertFolder(projectId, unique, ModuleConstants.ROOT_NODE_PARENT_ID, null, operator);
    }

    private void createOrSkipHubFolder(String hubProjectId, String bizProjectId, String name, String operator) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM functional_case_module WHERE project_id = ? AND ref_project_id = ?",
                Integer.class, hubProjectId, bizProjectId);
        if (count != null && count > 0) {
            return;
        }
        String unique = resolveUniqueName(hubProjectId, ModuleConstants.ROOT_NODE_PARENT_ID, name, null);
        insertFolder(hubProjectId, unique, ModuleConstants.ROOT_NODE_PARENT_ID, bizProjectId, operator);
    }

    private void insertFolder(String projectId, String name, String parentId, String refProjectId, String operator) {
        long now = System.currentTimeMillis();
        Long maxPos = jdbcTemplate.query(
                "SELECT MAX(pos) FROM functional_case_module WHERE parent_id = ?",
                rs -> rs.next() ? rs.getLong(1) : null,
                parentId);
        long pos = (maxPos == null || maxPos == 0) ? 64L : maxPos + 64L;
        jdbcTemplate.update(
                "INSERT INTO functional_case_module (id, project_id, name, parent_id, module_type, ref_project_id, pos, create_time, update_time, create_user, update_user) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                IDGenerator.nextStr(), projectId, name, parentId,
                DefaultHubConstants.MODULE_TYPE_FOLDER, refProjectId,
                pos, now, now, operator, operator
        );
    }

    private String resolveUniqueName(String projectId, String parentId, String baseName, String excludeId) {
        String name = baseName;
        int i = 2;
        while (existsName(projectId, parentId, name, excludeId)) {
            name = baseName + "(" + i + ")";
            i++;
        }
        return name;
    }

    private boolean existsName(String projectId, String parentId, String name, String excludeId) {
        Integer count;
        if (StringUtils.isBlank(excludeId)) {
            count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM functional_case_module WHERE project_id = ? AND parent_id = ? AND name = ?",
                    Integer.class, projectId, parentId, name);
        } else {
            count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM functional_case_module WHERE project_id = ? AND parent_id = ? AND name = ? AND id <> ?",
                    Integer.class, projectId, parentId, name, excludeId);
        }
        return count != null && count > 0;
    }

    private void deleteModuleCascade(String moduleId, String operator) {
        // 子节点
        jdbcTemplate.query("SELECT id FROM functional_case_module WHERE parent_id = ?", rs -> {
            deleteModuleCascade(rs.getString(1), operator);
        }, moduleId);
        // 用例进回收站
        jdbcTemplate.update(
                "UPDATE functional_case SET deleted = 1, delete_user = ?, delete_time = ? WHERE module_id = ? AND deleted = 0",
                operator, System.currentTimeMillis(), moduleId);
        jdbcTemplate.update("DELETE FROM functional_case_module WHERE id = ?", moduleId);
    }
}
