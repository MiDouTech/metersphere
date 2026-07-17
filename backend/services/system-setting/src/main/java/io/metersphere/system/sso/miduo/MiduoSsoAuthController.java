package io.metersphere.system.sso.miduo;

import io.metersphere.system.controller.handler.ResultHolder;
import io.metersphere.system.dto.sdk.SessionUser;
import io.metersphere.system.dto.sso.miduo.MiduoSsoCallbackRequest;
import io.metersphere.system.dto.sso.miduo.MiduoSsoStatusDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 米多 SSO 公开接口（Shiro anon）。
 * <ul>
 *   <li>POST /auth/miduo/callback — 浏览器提交 {token, state}</li>
 *   <li>GET  /auth/miduo/bridge-url — 登录桥（含新 state）</li>
 *   <li>POST /auth/miduo/logout — revoke + 销毁本地会话</li>
 * </ul>
 */
@RestController
@RequestMapping("/auth/miduo")
@Tag(name = "米多星球 SSO")
@Validated
public class MiduoSsoAuthController {

    @Resource
    private MiduoSsoApplicationService miduoSsoApplicationService;

    @GetMapping("/status")
    @Operation(summary = "米多 SSO 状态")
    public MiduoSsoStatusDTO status() {
        return miduoSsoApplicationService.getStatus();
    }

    @GetMapping("/state")
    @Operation(summary = "生成 state（CSRF）")
    public Map<String, String> state() {
        return miduoSsoApplicationService.createState();
    }

    @PostMapping("/callback")
    @Operation(summary = "米多 SSO 回调登录（校验 exchange token）")
    public ResultHolder callback(@RequestBody MiduoSsoCallbackRequest request) {
        if (request == null || StringUtils.isAnyBlank(request.getToken(), request.getState())) {
            throw new MiduoSsoException("token / state 不能为空");
        }
        SessionUser sessionUser = miduoSsoApplicationService.handleCallback(request.getToken(), request.getState());
        return ResultHolder.success(sessionUser);
    }

    @PostMapping("/logout")
    @Operation(summary = "米多 SSO 登出并 revoke sessionToken")
    public ResultHolder logout() {
        miduoSsoApplicationService.logout();
        return ResultHolder.success("logout success");
    }

    @GetMapping("/bridge-url")
    @Operation(summary = "获取米多登录桥 URL（含 state）")
    public Map<String, String> bridgeUrl() {
        return miduoSsoApplicationService.bridgeUrl();
    }
}
