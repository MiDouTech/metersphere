package io.metersphere.functional.asset.service;
import io.metersphere.functional.dto.response.FunctionalCaseImportResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
class CaseAssetImportJobServiceTests {
 @Test void emptyParsedWorkbookIsFailedNotSuccessful(){
  var jdbc=mock(JdbcTemplate.class);var service=new CaseAssetImportJobService();ReflectionTestUtils.setField(service,"jdbcTemplate",jdbc);
  service.complete("job",new FunctionalCaseImportResponse());
  verify(jdbc).update(anyString(),eq("FAILED"),eq(1),eq(0),eq(1),any(),anyLong(),eq("job"));
 }
 @Test void allRowsInvalidIsFailedNotPartial(){
  var jdbc=mock(JdbcTemplate.class);var service=new CaseAssetImportJobService();ReflectionTestUtils.setField(service,"jdbcTemplate",jdbc);
  var response=new FunctionalCaseImportResponse();response.setFailCount(2);service.complete("job",response);
  verify(jdbc).update(anyString(),eq("FAILED"),eq(2),eq(0),eq(2),any(),anyLong(),eq("job"));
 }
 @Test void intentionalSkipOnlyRemainsSuccessful(){
  var jdbc=mock(JdbcTemplate.class);var service=new CaseAssetImportJobService();ReflectionTestUtils.setField(service,"jdbcTemplate",jdbc);
  var response=new FunctionalCaseImportResponse();response.setSkippedCount(2);service.complete("job",response);
  verify(jdbc).update(anyString(),eq("SUCCESS"),eq(2),eq(0),eq(0),any(),anyLong(),eq("job"));
 }
}
