package io.metersphere.agent.controller;

import io.metersphere.sdk.constants.PermissionConstants;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

class TestAssetControllerPermissionTests {

    @Test
    void businessDocumentEndpointShouldRequireAiDocumentReadPermission() throws Exception {
        Method method = TestAssetController.class.getDeclaredMethod("documents",
                String.class, String.class, String.class, Integer.class, Integer.class);
        RequiresPermissions permission = method.getAnnotation(RequiresPermissions.class);

        Assertions.assertNotNull(permission);
        Assertions.assertArrayEquals(new String[]{PermissionConstants.FUNCTIONAL_CASE_AI_READ}, permission.value());
    }
}
