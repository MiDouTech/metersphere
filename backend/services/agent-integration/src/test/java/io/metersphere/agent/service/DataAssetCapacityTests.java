package io.metersphere.agent.service;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class DataAssetCapacityTests {
 @Test void exactThirtyMiBCsvParsesAndSavesDespiteJsonEnvelope() throws Exception {
  byte[] bytes=new byte[DataAssetLimits.MAX_BYTES];Arrays.fill(bytes,(byte)'a');
  var parsed=new DataAssetParser().parse(bytes,"CSV");
  var fixture=new DataAssetServiceTests();fixture.setup();
  var result=fixture.service.save(null,Map.of("name","capacity","format","CSV","content",parsed));
  assertNotNull(result.get("id"));
  assertThrows(io.metersphere.sdk.exception.MSException.class,()->new DataAssetParser().parse(Arrays.copyOf(bytes,bytes.length+1),"CSV"));
 }
 @Test void exactHundredThousandRowsAcceptedNextRowRejected() {
  var parser=new DataAssetParser();
  var parsed=(Map<?,?>)parser.parse("a\n".repeat(100000).getBytes(java.nio.charset.StandardCharsets.UTF_8),"CSV");
  assertEquals(100000,((List<?>)parsed.get("rows")).size());
  assertThrows(io.metersphere.sdk.exception.MSException.class,()->parser.parse("a\n".repeat(100001).getBytes(java.nio.charset.StandardCharsets.UTF_8),"CSV"));
 }
}
