package io.metersphere.system.sso.miduo;

import io.metersphere.sdk.constants.HttpMethodConstants;
import io.metersphere.sdk.constants.UserSource;
import io.metersphere.system.config.MiduoSsoProperties;
import io.metersphere.system.domain.OrgWecomSyncConfig;
import io.metersphere.system.domain.OrgWecomSyncConfigExample;
import io.metersphere.system.domain.User;
import io.metersphere.system.dto.sdk.SessionUser;
import io.metersphere.system.dto.sso.miduo.MiduoSsoStatusDTO;
import io.metersphere.system.dto.sso.miduo.MiduoValidateResult;
import io.metersphere.system.dto.user.UserDTO;
import io.metersphere.system.log.constants.OperationLogType;
import io.metersphere.system.mapper.ExtUserMapper;
import io.metersphere.system.mapper.OrgWecomSyncConfigMapper;
import io.metersphere.system.service.UserLoginService;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 米多 SSO 应用服务：status / state / callback / logout / bridge。
 * <p>
 * 流程：浏览器中转 exchange token → 后端 validate → 按 wework_userid 匹配本地用户
 * → 建立 Shiro Session，sessionToken 仅存 Redis → refresh / revoke / 登录桥。
 * SSO 不自动建号；成员须先经企微通讯录同步。
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class MiduoSsoApplicationService {

    private static final Logger log = LoggerFactory.getLogger(MiduoSsoApplicationService.class);

    @Resource
    private MiduoSsoProperties properties;
    @Resource
    private MiduoSsoClient miduoSsoClient;
    @Resource
    private MiduoSsoStateService miduoSsoStateService;
    @Resource
    private MiduoSsoSessionStore miduoSsoSessionStore;
    @Resource
    private ExtUserMapper extUserMapper;
    @Resource
    private OrgWecomSyncConfigMapper orgWecomSyncConfigMapper;
    @Resource
    private UserLoginService userLoginService;

    public MiduoSsoStatusDTO getStatus() {
        MiduoSsoStatusDTO dto = new MiduoSsoStatusDTO();
        dto.setEnabled(properties.isEnabled());
        if (!properties.isEnabled()) {
            dto.setReady(false);
            dto.setReason("DISABLED");
            dto.setMessage("米多 SSO 未启用");
            return dto;
        }
        if (!properties.isConfigured()) {
            dto.setReady(false);
            dto.setReason("DISABLED");
            dto.setMessage("米多 SSO 配置不完整");
            return dto;
        }
        if (!hasWecomConfig()) {
            dto.setReady(false);
            dto.setReason("WECOM_SYNC_NOT_CONFIGURED");
            dto.setMessage("请先完成企微通讯录配置并同步成员");
            return dto;
        }
        if (!hasSyncedUsers()) {
            dto.setReady(false);
            dto.setReason("NO_SYNCED_USERS");
            dto.setMessage("暂无已同步企微成员，请先执行「同步企业微信成员」");
            return dto;
        }
        dto.setReady(true);
        dto.setReason("OK");
        dto.setMessage("ready");
        return dto;
    }

    public Map<String, String> createState() {
        ensureEnabled();
        return Map.of("state", miduoSsoStateService.generateState());
    }

    /**
     * 回调：校验 state（一次性）→ validate exchange token → 匹配用户 → Shiro Session。
     * 禁止信任 URL 上的 mobile/name 等 PII。
     */
    public SessionUser handleCallback(String token, String state) {
        ensureEnabled();
        MiduoSsoStatusDTO status = getStatus();
        if (!status.isReady()) {
            throw new MiduoSsoException(StringUtils.defaultIfBlank(status.getMessage(), "米多 SSO 未就绪"));
        }
        miduoSsoStateService.consumeState(state);
        MiduoValidateResult validate = miduoSsoClient.validateLoginToken(token);
        User user = matchUser(validate.getWeworkUserid());
        SessionUser sessionUser = establishSession(user);
        miduoSsoSessionStore.save(user.getId(), validate.getSessionToken(), validate.getExpiresAt());
        userLoginService.saveLog(user.getId(), HttpMethodConstants.POST.name(),
                "/auth/miduo/callback", "米多 SSO 登录成功", OperationLogType.LOGIN.name());
        log.info("miduo sso login ok userId={} wework={}", user.getId(),
                MiduoSsoLogUtils.maskTail(validate.getWeworkUserid(), 4));
        return sessionUser;
    }

    public void logout() {
        String userId = SessionUtils.getUserId();
        if (StringUtils.isBlank(userId)) {
            SecurityUtils.getSubject().logout();
            return;
        }
        String sessionToken = miduoSsoSessionStore.getSessionToken(userId);
        try {
            if (StringUtils.isNotBlank(sessionToken) && properties.isConfigured()) {
                miduoSsoClient.revokeSessionToken(sessionToken);
            }
        } catch (Exception e) {
            log.warn("miduo revoke failed userId={} err={}", userId, e.getMessage());
        } finally {
            miduoSsoSessionStore.delete(userId);
            try {
                userLoginService.saveLog(userId, HttpMethodConstants.POST.name(),
                        "/auth/miduo/logout", "米多 SSO 登出", OperationLogType.LOGOUT.name());
            } catch (Exception ignored) {
                // ignore audit failure
            }
            SecurityUtils.getSubject().logout();
        }
    }

    /**
     * 生成 state 并返回登录桥 URL（refresh 失败或默认登录入口使用）。
     */
    public Map<String, String> bridgeUrl() {
        ensureEnabled();
        String state = miduoSsoStateService.generateState();
        return Map.of(
                "url", miduoSsoClient.buildBridgeUrl(state),
                "state", state
        );
    }

    public Map<String, Object> enrichLoginPayload(SessionUser sessionUser) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("user", sessionUser);
        payload.put("needMiduoReauth", miduoSsoSessionStore.isNeedReauth(sessionUser.getId()));
        String authenticate = (String) SecurityUtils.getSubject().getSession().getAttribute("authenticate");
        payload.put("authenticate", authenticate);
        return payload;
    }

    public boolean isMiduoSession() {
        Object auth = SecurityUtils.getSubject().getSession().getAttribute("authenticate");
        return StringUtils.equals(UserSource.MIDUO.name(), String.valueOf(auth));
    }

    private User matchUser(String weworkUserid) {
        if (StringUtils.isBlank(weworkUserid)) {
            throw new MiduoSsoException("米多未返回 wework_userid");
        }
        User user = extUserMapper.selectByWecomUserid(weworkUserid.trim());
        if (user == null || BooleanUtils.isTrue(user.getDeleted())) {
            throw new MiduoSsoException("未找到对应成员，请先同步企业微信成员");
        }
        if (BooleanUtils.isFalse(user.getEnable())) {
            throw new MiduoSsoException("用户已禁用，请联系管理员");
        }
        return user;
    }

    private SessionUser establishSession(User user) {
        SecurityUtils.getSubject().getSession().setAttribute("authenticate", UserSource.MIDUO.name());
        UsernamePasswordToken token = new UsernamePasswordToken(user.getId(), "");
        Subject subject = SecurityUtils.getSubject();
        subject.login(token);
        UserDTO userDTO = userLoginService.getUserDTO(user.getId());
        userLoginService.autoSwitch(userDTO);
        SessionUser sessionUser = SessionUser.fromUser(userDTO, SessionUtils.getSessionId());
        SessionUtils.putUser(sessionUser);
        return sessionUser;
    }

    private void ensureEnabled() {
        if (!properties.isConfigured()) {
            throw new MiduoSsoException("米多 SSO 未启用或配置不完整");
        }
    }

    private boolean hasWecomConfig() {
        if (StringUtils.isNotBlank(properties.getOrganizationId())) {
            OrgWecomSyncConfigExample example = new OrgWecomSyncConfigExample();
            example.createCriteria().andOrganizationIdEqualTo(properties.getOrganizationId());
            return orgWecomSyncConfigMapper.countByExample(example) > 0;
        }
        return orgWecomSyncConfigMapper.countByExample(new OrgWecomSyncConfigExample()) > 0;
    }

    private boolean hasSyncedUsers() {
        if (StringUtils.isNotBlank(properties.getOrganizationId())) {
            List<User> users = extUserMapper.listWecomUsersByOrganizationId(properties.getOrganizationId());
            return CollectionUtils.isNotEmpty(users)
                    && users.stream().anyMatch(u -> BooleanUtils.isNotFalse(u.getEnable()));
        }
        OrgWecomSyncConfigExample example = new OrgWecomSyncConfigExample();
        List<OrgWecomSyncConfig> configs = orgWecomSyncConfigMapper.selectByExample(example);
        if (CollectionUtils.isEmpty(configs)) {
            return false;
        }
        for (OrgWecomSyncConfig config : configs) {
            List<User> users = extUserMapper.listWecomUsersByOrganizationId(config.getOrganizationId());
            if (CollectionUtils.isNotEmpty(users)
                    && users.stream().anyMatch(u -> BooleanUtils.isNotFalse(u.getEnable()))) {
                return true;
            }
        }
        return false;
    }
}
