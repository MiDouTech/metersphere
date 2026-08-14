package io.metersphere.functional.controller;

import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.security.CheckOwner;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

class AiSourceDocumentControllerSecurityTests {

    @Test
    void documentDetailShouldRequireProjectOwnershipAndAiReadPermission() throws Exception {
        Method method = AiSourceDocumentController.class.getDeclaredMethod("get", String.class, String.class);
        CheckOwner owner = method.getAnnotation(CheckOwner.class);
        RequiresPermissions permission = method.getAnnotation(RequiresPermissions.class);

        Assertions.assertNotNull(owner);
        Assertions.assertEquals("#projectId", owner.resourceId());
        Assertions.assertEquals("project", owner.resourceType());
        Assertions.assertNotNull(permission);
        Assertions.assertArrayEquals(new String[]{PermissionConstants.FUNCTIONAL_CASE_AI_READ}, permission.value());
    }
}
