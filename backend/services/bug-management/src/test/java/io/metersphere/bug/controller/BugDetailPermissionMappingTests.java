package io.metersphere.bug.controller;

import io.metersphere.sdk.constants.PermissionConstants;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

class BugDetailPermissionMappingTests {

    @Test
    void shouldKeepBugDetailActionPermissionAnnotations() {
        assertPermission(BugController.class, "get", PermissionConstants.PROJECT_BUG_READ);
        assertPermission(BugController.class, "update", PermissionConstants.PROJECT_BUG_UPDATE);
        assertPermission(BugController.class, "add", PermissionConstants.PROJECT_BUG_ADD);
        assertPermission(BugController.class, "delete", PermissionConstants.PROJECT_BUG_DELETE);
        assertPermission(BugController.class, "follow", PermissionConstants.PROJECT_BUG_READ);
        assertPermission(BugController.class, "unfollow", PermissionConstants.PROJECT_BUG_READ);
        assertPermission(BugCommentController.class, "add", PermissionConstants.PROJECT_BUG_COMMENT);
    }

    private void assertPermission(Class<?> controllerClass, String methodName, String permission) {
        Method method = Arrays.stream(controllerClass.getDeclaredMethods())
                .filter(item -> item.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(controllerClass.getSimpleName() + "." + methodName + " not found"));
        RequiresPermissions annotation = method.getAnnotation(RequiresPermissions.class);
        Assertions.assertNotNull(annotation, controllerClass.getSimpleName() + "." + methodName + " missing @RequiresPermissions");
        Assertions.assertArrayEquals(new String[]{permission}, annotation.value());
    }
}
