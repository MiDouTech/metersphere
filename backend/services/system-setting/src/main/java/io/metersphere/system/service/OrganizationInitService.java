package io.metersphere.system.service;

import io.metersphere.sdk.constants.TemplateScopeType;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 组织初始化服务，新建组织时复用默认组织同等粒度的基础数据初始化。
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class OrganizationInitService {

    @Resource
    private BaseTemplateService baseTemplateService;
    @Resource
    private BaseStatusFlowSettingService baseStatusFlowSettingService;

    /**
     * 初始化新建组织的全部基础数据。
     */
    public void initOrganization(String organizationId, String operatorId) {
        initOrgTemplates(organizationId, operatorId);
        initOrgStatusFlow(organizationId, operatorId);
        initOrgDefaultRoles(organizationId, operatorId);
    }

    /**
     * 初始化组织级用例/API/Bug/UI/测试计划模板（含功能与缺陷自定义字段）。
     */
    public void initOrgTemplates(String organizationId, String operatorId) {
        TemplateScopeType scopeType = TemplateScopeType.ORGANIZATION;
        baseTemplateService.initFunctionalDefaultTemplate(organizationId, scopeType);
        baseTemplateService.initBugDefaultTemplate(organizationId, scopeType);
        baseTemplateService.initApiDefaultTemplate(organizationId, scopeType);
        baseTemplateService.initUiDefaultTemplate(organizationId, scopeType);
        baseTemplateService.initTestPlanDefaultTemplate(organizationId, scopeType);
    }

    /**
     * 组织自定义字段随功能/缺陷模板初始化一并创建，此处保留独立入口供扩展。
     */
    public void initOrgCustomFields(String organizationId, String operatorId) {
        // functional_priority、bug_degree 等字段已在 initOrgTemplates 中创建
    }

    /**
     * 初始化组织级 Bug 状态流。
     */
    public void initOrgStatusFlow(String organizationId, String operatorId) {
        baseStatusFlowSettingService.initBugDefaultStatusFlowSetting(organizationId, TemplateScopeType.ORGANIZATION);
    }

    /**
     * org_admin / org_member 为全局内置角色，创建组织时通过 user_role_relation 绑定。
     */
    public void initOrgDefaultRoles(String organizationId, String operatorId) {
        // 内置角色无需按组织复制，成员关系在 OrganizationService.add 中绑定
    }
}
