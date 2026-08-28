package io.metersphere.system.controller;

import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.dto.ai.agent.AiAgentConnectionStatusRequest;
import io.metersphere.system.dto.ai.agent.AiAgentDeviceAuthenticateRequest;
import io.metersphere.system.dto.ai.agent.AiAgentDeviceChallengeRequest;
import io.metersphere.system.dto.ai.agent.AiAgentDeviceDTO;
import io.metersphere.system.dto.ai.agent.AiAgentPairingConsumeRequest;
import io.metersphere.system.dto.ai.agent.AiAgentPairingCreateRequest;
import io.metersphere.system.dto.ai.agent.AiUserAgentConnectionCreateRequest;
import io.metersphere.system.dto.ai.agent.AiUserAgentConnectionDTO;
import io.metersphere.system.service.ai.agent.AiUserAgentService;
import io.metersphere.system.service.ai.agent.AiUserAgentFeatureService;
import io.metersphere.system.service.ai.agent.bridge.AgentBridgeSessionRegistry;
import io.metersphere.system.utils.SessionUtils;
import jakarta.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class AiUserAgentController {
    private final AiUserAgentService service;
    private final AiUserAgentFeatureService featureService;
    private final AgentBridgeSessionRegistry sessionRegistry;

    public AiUserAgentController(AiUserAgentService service, AiUserAgentFeatureService featureService,
                                 AgentBridgeSessionRegistry sessionRegistry) {
        this.service = service;
        this.featureService = featureService;
        this.sessionRegistry = sessionRegistry;
    }

    @GetMapping("/ai/user-agent/features")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_READ)
    public Map<String, Object> features() {
        return featureService.flags();
    }

    @GetMapping("/ai/agent-bridge/install-info")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_READ)
    public Map<String, Object> bridgeInstallInfo() {
        return featureService.bridgeInstallInfo();
    }

    @GetMapping("/ai/user-agent/connections")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_READ)
    public List<AiUserAgentConnectionDTO> connections() {
        return service.listConnections(SessionUtils.getUserId());
    }

    @PostMapping("/ai/user-agent/connections")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_CONNECT)
    public AiUserAgentConnectionDTO createConnection(@Valid @RequestBody AiUserAgentConnectionCreateRequest request) {
        featureService.assertProvisioningAllowed();
        return service.createConnection(request, SessionUtils.getUserId());
    }

    @PostMapping("/ai/user-agent/connections/{id}/authorize")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_CONNECT)
    public void authorizeConnection(@PathVariable String id) {
        featureService.assertProvisioningAllowed();
        AiUserAgentConnectionDTO connection = service.prepareAuthorization(id, SessionUtils.getUserId());
        sessionRegistry.authorize(connection.getDeviceId(), connection.getId(), connection.getProvider());
    }

    @PostMapping("/ai/user-agent/connections/{id}/revoke")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_REVOKE)
    public void revokeConnection(@PathVariable String id) {
        service.revokeConnection(id, SessionUtils.getUserId());
    }

    @PostMapping("/ai/agent-bridge/pairing")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_CONNECT)
    public Map<String, Object> pairing(@Valid @RequestBody AiAgentPairingCreateRequest request) {
        featureService.assertProvisioningAllowed();
        return service.createPairing(request, SessionUtils.getUserId());
    }

    @GetMapping("/ai/user-agent/connections/{id}")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_READ)
    public AiUserAgentConnectionDTO connection(@PathVariable String id) {
        return service.getConnection(id, SessionUtils.getUserId());
    }

    @GetMapping("/ai/user-agent/connections/{id}/impact")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_READ)
    public Map<String, Object> connectionImpact(@PathVariable String id) {
        return service.connectionImpact(id, SessionUtils.getUserId());
    }

    @DeleteMapping("/ai/user-agent/connections/{id}")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_REVOKE)
    public void deleteConnection(@PathVariable String id) {
        service.deleteConnection(id, SessionUtils.getUserId());
    }

    @GetMapping("/ai/agent-bridge/pairing/{id}")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_CONNECT)
    public Map<String, Object> pairingStatus(@PathVariable String id) {
        return service.pairingStatus(id, SessionUtils.getUserId());
    }

    @PostMapping("/ai/agent-bridge/pairing/consume")
    public Map<String, Object> consumePairing(@Valid @RequestBody AiAgentPairingConsumeRequest request) {
        featureService.assertProvisioningAllowed();
        return service.consumePairing(request);
    }

    @PostMapping("/ai/agent-bridge/challenge")
    public Map<String, Object> challenge(@Valid @RequestBody AiAgentDeviceChallengeRequest request) {
        return service.createChallenge(request.getDeviceId());
    }

    @PostMapping("/ai/agent-bridge/authenticate")
    public Map<String, Object> authenticate(@Valid @RequestBody AiAgentDeviceAuthenticateRequest request) {
        return service.authenticate(request);
    }

    @PostMapping("/ai/agent-bridge/heartbeat")
    public void heartbeat(@RequestHeader("X-Agent-Device-Id") String deviceId,
                          @RequestHeader("Authorization") String authorization) {
        service.heartbeat(deviceId, bearer(authorization));
    }

    @PostMapping("/ai/agent-bridge/connections/status")
    public void reportConnectionStatus(@Valid @RequestBody AiAgentConnectionStatusRequest request,
                                       @RequestHeader("Authorization") String authorization) {
        service.reportConnectionStatus(request, bearer(authorization));
    }

    @GetMapping("/ai/agent-bridge/devices")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_READ)
    public List<AiAgentDeviceDTO> devices() {
        return service.listDevices(SessionUtils.getUserId());
    }

    @PostMapping("/ai/agent-bridge/devices/{id}/revoke")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_REVOKE)
    public void revokeDevice(@PathVariable String id) {
        service.revokeDevice(id, SessionUtils.getUserId());
    }

    private String bearer(String authorization) {
        if (!StringUtils.startsWithIgnoreCase(authorization, "Bearer ")) {
            return null;
        }
        return StringUtils.trim(StringUtils.substring(authorization, 7));
    }
}
