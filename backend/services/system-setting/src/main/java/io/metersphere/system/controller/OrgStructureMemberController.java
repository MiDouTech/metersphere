package io.metersphere.system.controller;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.dto.department.DepartmentTreeNode;
import io.metersphere.system.dto.department.OrgStructureMemberDetailDTO;
import io.metersphere.system.dto.department.OrgStructureMemberItemDTO;
import io.metersphere.system.dto.department.OrgStructureMemberPageRequest;
import io.metersphere.system.service.department.DepartmentQueryService;
import io.metersphere.system.service.department.OrgStructureMemberService;
import io.metersphere.system.utils.PageUtils;
import io.metersphere.system.utils.Pager;
import io.metersphere.system.utils.SessionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "组织架构-管理")
@RestController
@RequestMapping("/org-structure")
@Validated
public class OrgStructureMemberController {

    @Resource
    private DepartmentQueryService departmentQueryService;
    @Resource
    private OrgStructureMemberService orgStructureMemberService;

    @GetMapping("/departments/tree")
    @Operation(summary = "组织架构-管理端部门树（含成员统计）")
    @RequiresPermissions(value = {
            PermissionConstants.SYSTEM_ORGANIZATION_PROJECT_READ,
            PermissionConstants.ORGANIZATION_MEMBER_READ
    }, logical = Logical.OR)
    public List<DepartmentTreeNode> departmentTree(@RequestParam @NotBlank String organizationId) {
        SessionUtils.setCurrentOrganizationId(organizationId);
        try {
            return departmentQueryService.getTreeWithStats(organizationId);
        } finally {
            SessionUtils.clearCurrentOrganizationId();
        }
    }

    @GetMapping("/members/page")
    @Operation(summary = "组织架构-成员分页")
    @RequiresPermissions(value = {
            PermissionConstants.SYSTEM_ORGANIZATION_PROJECT_READ,
            PermissionConstants.ORGANIZATION_MEMBER_READ
    }, logical = Logical.OR)
    public Pager<List<OrgStructureMemberItemDTO>> page(@Validated OrgStructureMemberPageRequest request) {
        SessionUtils.setCurrentOrganizationId(request.getOrganizationId());
        try {
            Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize());
            return PageUtils.setPageInfo(page, orgStructureMemberService.page(request));
        } finally {
            SessionUtils.clearCurrentOrganizationId();
        }
    }

    @GetMapping("/members/{id}")
    @Operation(summary = "组织架构-成员详情（脱敏）")
    @RequiresPermissions(value = {
            PermissionConstants.SYSTEM_ORGANIZATION_PROJECT_READ,
            PermissionConstants.ORGANIZATION_MEMBER_READ
    }, logical = Logical.OR)
    public OrgStructureMemberDetailDTO detail(@PathVariable("id") String id,
                                              @RequestParam @NotBlank String organizationId) {
        SessionUtils.setCurrentOrganizationId(organizationId);
        try {
            return orgStructureMemberService.detail(id, organizationId);
        } finally {
            SessionUtils.clearCurrentOrganizationId();
        }
    }
}
