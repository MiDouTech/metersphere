package io.metersphere.agent.service;

import io.metersphere.functional.planreview.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImportFollowupOptionsTests {
    private Map<String,Object> options(Set<PlanReviewPolicy.Permission> permissions,boolean grant) {
        var jdbc=mock(JdbcTemplate.class);var access=mock(AgentBusinessAccess.class);
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM project"),eq(Long.class),eq("project"))).thenReturn(1L);
        when(access.userHasPermission(anyString(),anyString(),anyString())).thenReturn(true);
        var auth=mock(PlanReviewAuthorization.class);
        when(auth.current("user","project",true)).thenReturn(new PlanReviewPolicy.Access(permissions,grant));
        var coordinator=new ImportFollowupCoordinator(jdbc,mock(PlatformTransactionManager.class),null,access,null,null,null,null);
        ReflectionTestUtils.setField(coordinator,"relationAuthorization",auth);
        return coordinator.optionsForProject("project","user");
    }
    @Test void disabledLegacyServiceGrantDoesNotBlockCreation(){
        var result=options(EnumSet.allOf(PlanReviewPolicy.Permission.class),false);
        assertEquals(true,result.get("canCreatePermission"));assertEquals(true,result.get("serviceGranted"));assertEquals(true,result.get("canCreate"));
    }
    @Test void creationStillUsesExistingObjectPermissions(){
        assertEquals(true,options(EnumSet.allOf(PlanReviewPolicy.Permission.class),true).get("canCreate"));
        var missing=options(EnumSet.of(PlanReviewPolicy.Permission.IMPORT),true);
        assertEquals(false,missing.get("canCreatePermission"));assertEquals(false,missing.get("canCreate"));
    }
}
