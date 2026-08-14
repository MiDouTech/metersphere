package io.metersphere.agent.controller;

import io.metersphere.agent.dto.AgentTokenCreateRequest;
import io.metersphere.agent.dto.AgentTokenPageRequest;
import io.metersphere.agent.dto.AgentTokenUpdateRequest;
import io.metersphere.sdk.constants.PermissionConstants;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PatchMapping;

import java.lang.reflect.Method;
import java.util.List;

class PersonalAgentTokenControllerTests {

    @Test
    void personalTokenEndpointsShouldUseReadConnectAndRevokePermissions() throws Exception {
        assertPermission("page", List.of(AgentTokenPageRequest.class), PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_READ);
        assertPermission("create", List.of(AgentTokenCreateRequest.class), PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_CONNECT);
        assertPermission("update", List.of(String.class, AgentTokenUpdateRequest.class), PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_CONNECT);
        assertPermission("enable", List.of(String.class), PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_CONNECT);
        assertPermission("disable", List.of(String.class), PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_CONNECT);
        assertPermission("delete", List.of(String.class), PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_REVOKE);
    }

    @Test
    void removedNoOpTestEndpointMustNotReappear() {
        Assertions.assertTrue(
                java.util.Arrays.stream(PersonalAgentTokenController.class.getDeclaredMethods())
                        .noneMatch(method -> method.getName().equals("test")),
                "Personal Token 的无效 test 接口不应重新暴露");
    }

    @Test
    void updateEndpointShouldUsePatchToMatchFrontendContract() throws Exception {
        Method method = PersonalAgentTokenController.class.getDeclaredMethod(
                "update", String.class, AgentTokenUpdateRequest.class);
        Assertions.assertNotNull(method.getAnnotation(PatchMapping.class));
    }

    private void assertPermission(String methodName, List<Class<?>> parameterTypes, String expected) throws Exception {
        Method method = PersonalAgentTokenController.class.getDeclaredMethod(
                methodName, parameterTypes.toArray(Class[]::new));
        RequiresPermissions annotation = method.getAnnotation(RequiresPermissions.class);
        Assertions.assertNotNull(annotation, methodName + " 缺少权限注解");
        Assertions.assertArrayEquals(new String[]{expected}, annotation.value());
    }
}
