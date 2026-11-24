package io.metersphere.functional.asset.service;
import io.metersphere.functional.hub.service.DefaultHubModuleResolver;
import io.metersphere.functional.request.FunctionalCaseImportRequest;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
class CaseAssetImportIdentityResolverTests {
 @Test void realDatabaseScopesNameMatchingAndRefusesAmbiguousTargets(){
  var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+java.util.UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
  jdbc.execute("CREATE TABLE functional_case(id varchar primary key,project_id varchar,module_id varchar,name varchar,deleted int,latest int)");
  jdbc.update("INSERT INTO functional_case VALUES('own','hub','root','same',0,1),('foreign-catalog','hub','other','same',0,1),('foreign-project','other','root','same',0,1)");
  var resolver=new CaseAssetImportIdentityResolver();var modules=mock(DefaultHubModuleResolver.class);when(modules.listDescendantModuleIds("hub",List.of("root"))).thenReturn(List.of("root","child"));
  ReflectionTestUtils.setField(resolver,"jdbcTemplate",jdbc);ReflectionTestUtils.setField(resolver,"moduleResolver",modules);
  var request=new FunctionalCaseImportRequest();request.setProjectId("hub");request.setModuleId("root");
  assertEquals("own",resolver.resolve(request,"same"));assertNull(resolver.resolve(request,"new"));
  jdbc.update("INSERT INTO functional_case VALUES('ambiguous','hub','child','same',0,1)");assertThrows(MSException.class,()->resolver.resolve(request,"same"));
  assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM functional_case",Integer.class));
 }
}
