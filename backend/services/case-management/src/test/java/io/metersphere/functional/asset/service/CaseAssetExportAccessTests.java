package io.metersphere.functional.asset.service;

import io.metersphere.functional.hub.service.DefaultHubModuleResolver;
import io.metersphere.functional.request.FunctionalCaseExportRequest;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.service.UserLoginService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CaseAssetExportAccessTests {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final UserLoginService users=mock(UserLoginService.class);
    final DefaultHubModuleResolver modules=mock(DefaultHubModuleResolver.class);
    final CaseAssetExportAccess access=new CaseAssetExportAccess(jdbc,users,modules);

    @Test void rejectsDimensionAndAllSelectionBeforeDatabaseReads() {
        var request=new FunctionalCaseExportRequest();
        request.setDimension("SYSTEM");
        assertThrows(MSException.class,()->access.authorize(request,"user"));
        request.setDimension("PROJECT");request.setSelectAll(true);
        assertThrows(MSException.class,()->access.authorize(request,"user"));
        request.setSelectAll(false);request.setProjectIds(List.of("foreign"));
        assertThrows(MSException.class,()->access.authorize(request,"user"));
        verifyNoInteractions(jdbc,users,modules);
    }
    @Test void deletedCatalogCannotBeUsed() {
        assertThrows(MSException.class,()->access.authorizeCatalog("removed","user"));
        verifyNoInteractions(users,modules);
    }
    @Test void removedUserCannotReadCatalogFields() {
        when(jdbc.queryForList(anyString(),eq("catalog"))).thenReturn(List.of(Map.of("organizationId","org","moduleId","root","projectId","hub")));
        assertThrows(MSException.class,()->access.authorizeCatalog("catalog","removed"));
        verify(users).getUserDTO("removed");
        verifyNoInteractions(modules);
    }
    @Test void emptyExportNeverSucceeds() {
        assertThrows(MSException.class,()->access.validateIds("catalog",List.of()));
        verifyNoInteractions(jdbc,users,modules);
    }
}
