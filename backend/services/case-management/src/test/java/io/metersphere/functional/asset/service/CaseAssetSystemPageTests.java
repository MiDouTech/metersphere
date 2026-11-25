package io.metersphere.functional.asset.service;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import io.metersphere.functional.asset.dto.CaseAssetPageRequest;
import io.metersphere.functional.service.FunctionalCaseService;
import io.metersphere.functional.hub.service.DefaultHubModuleResolver;
import io.metersphere.system.service.DefaultHubProjectService;
import io.metersphere.system.utils.SessionUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CaseAssetSystemPageTests {
 @Test void systemViewOverridesCallerScopeAndIncludesNestedCatalogModules() {
  var service=new CaseAssetService();var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
  jdbc.execute("CREATE TABLE project(id varchar,organization_id varchar,deleted boolean,enable boolean)");jdbc.execute("CREATE TABLE functional_case_module(id varchar,project_id varchar)");jdbc.execute("CREATE TABLE case_asset_catalog(id varchar,name varchar,organization_id varchar,hub_module_id varchar,deleted boolean)");
  jdbc.update("INSERT INTO project VALUES('hub','org',false,true)");jdbc.update("INSERT INTO functional_case_module VALUES('root','hub')");jdbc.update("INSERT INTO case_asset_catalog VALUES('catalog','Catalog','org','root',false)");
  var hub=mock(DefaultHubProjectService.class);when(hub.getDefaultProjectId()).thenReturn("hub");var resolver=mock(DefaultHubModuleResolver.class);when(resolver.listDescendantModuleIds("hub",List.of("root"))).thenReturn(List.of("root","nested"));
  var cases=mock(FunctionalCaseService.class);when(cases.getFunctionalCasePage(any(),eq(false),eq(true))).thenReturn(List.of());
  ReflectionTestUtils.setField(service,"jdbcTemplate",jdbc);ReflectionTestUtils.setField(service,"defaultHubProjectService",hub);ReflectionTestUtils.setField(service,"defaultHubModuleResolver",resolver);ReflectionTestUtils.setField(service,"functionalCaseService",cases);
  try(var sessions=mockStatic(SessionUtils.class)) {
   sessions.when(SessionUtils::getCurrentOrganizationId).thenReturn("org");
   var request=new CaseAssetPageRequest();request.setDimension("SYSTEM");request.setProjectId("foreign");request.setWorkspaceId("foreign");request.setProjectIds(List.of("foreign"));request.setModuleIds(List.of("foreign"));request.setCurrent(1);request.setPageSize(20);
   service.pageCases(request);assertEquals("hub",request.getProjectId());assertEquals("org",request.getWorkspaceId());assertNull(request.getProjectIds());assertEquals(Set.of("root","nested"),new HashSet<>(request.getModuleIds()));
   jdbc.update("DELETE FROM case_asset_catalog");service.pageCases(request);assertFalse(request.getModuleIds().isEmpty(),"empty catalog scope must never remove SQL restriction");assertFalse(request.getModuleIds().contains("root"));
   request.setDimension("PROJECT");assertThrows(Exception.class,()->service.pageCases(request));
  }
 }
}
