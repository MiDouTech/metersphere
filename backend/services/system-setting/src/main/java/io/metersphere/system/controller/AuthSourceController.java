package io.metersphere.system.controller;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.dto.AuthSourceDTO;
import io.metersphere.system.dto.request.UpdateAuthStatusRequest;
import io.metersphere.system.dto.sdk.BasePageRequest;
import io.metersphere.system.service.AuthSourceService;
import io.metersphere.system.utils.PageUtils;
import io.metersphere.system.utils.Pager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/system/authsource")
@Tag(name = "系统设置-认证源")
public class AuthSourceController {

    @Resource
    private AuthSourceService authSourceService;

    @PostMapping("/list")
    @Operation(summary = "认证源列表")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_AUTH_READ)
    public Pager<List<AuthSourceDTO>> list(@RequestBody BasePageRequest request) {
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize(), true);
        return PageUtils.setPageInfo(page, authSourceService.list(request));
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "认证源详情")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_AUTH_READ)
    public AuthSourceDTO get(@PathVariable String id) {
        return authSourceService.get(id);
    }

    @PostMapping("/add")
    @Operation(summary = "添加认证源")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_AUTH_READ_ADD)
    public void add(@RequestBody AuthSourceDTO request) {
        authSourceService.add(request);
    }

    @PostMapping("/update")
    @Operation(summary = "更新认证源")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_AUTH_READ_UPDATE)
    public void update(@RequestBody AuthSourceDTO request) {
        authSourceService.update(request);
    }

    @PostMapping("/update/status")
    @Operation(summary = "更新认证源状态")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_AUTH_READ_UPDATE)
    public void updateStatus(@RequestBody UpdateAuthStatusRequest request) {
        authSourceService.updateStatus(request);
    }

    @GetMapping("/delete/{id}")
    @Operation(summary = "删除认证源")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_AUTH_READ_DELETE)
    public void delete(@PathVariable String id) {
        authSourceService.delete(id);
    }

    @PostMapping("/ldap/test-connect")
    @Operation(summary = "测试 LDAP 连接")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_AUTH_READ_UPDATE)
    public void testLdapConnect(@RequestBody Map<String, String> request) {
        authSourceService.testLdapConnect(request);
    }

    @PostMapping("/ldap/test-login")
    @Operation(summary = "测试 LDAP 登录")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_AUTH_READ_UPDATE)
    public void testLdapLogin(@RequestBody Map<String, String> request) {
        authSourceService.testLdapLogin(request);
    }
}
