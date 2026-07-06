package io.metersphere.system.controller;

import io.metersphere.system.dto.department.DepartmentTreeNode;
import io.metersphere.system.service.department.DepartmentQueryService;
import io.metersphere.system.utils.SessionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "组织架构-部门")
@RestController
@RequestMapping("/department")
@Validated
public class DepartmentController {

    @Resource
    private DepartmentQueryService departmentQueryService;

    @GetMapping("/tree")
    @Operation(summary = "组织架构-通用部门树（业务选人）")
    public List<DepartmentTreeNode> tree(@RequestParam @NotBlank String organizationId) {
        SessionUtils.setCurrentOrganizationId(organizationId);
        try {
            return departmentQueryService.getTree(organizationId);
        } finally {
            SessionUtils.clearCurrentOrganizationId();
        }
    }
}
