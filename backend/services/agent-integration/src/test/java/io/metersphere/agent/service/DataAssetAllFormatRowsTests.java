package io.metersphere.agent.service;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class DataAssetAllFormatRowsTests {
 @Test void textCounterMatchesRecordSeparatorsAndTrailingBoundary() {
  assertEquals(0,DataAssetLimits.textRecords(""));assertEquals(1,DataAssetLimits.textRecords("\n"));
  assertEquals(2,DataAssetLimits.textRecords("a\r\n\r\n"));assertEquals(3,DataAssetLimits.textRecords("a\u2028b\u0085c"));
 }
 @Test void txtAndJsonRootArraysEnforceBoundaryAcrossParserAndManualSave() throws Exception {
  var parser=new DataAssetParser();var fixture=new DataAssetServiceTests();fixture.setup();
  for(String format:List.of("TXT","JSON")) {
   Object value=format.equals("TXT")?"value\r\n".repeat(100000):Collections.nCopies(100000,"value");
   byte[] bytes=format.equals("TXT")?value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8):new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(value);
   assertNotNull(parser.parse(bytes,format));
   var saved=fixture.service.save(null,Map.of("name",format,"format",format,"content",value));
   var edited=fixture.service.save(saved.get("id").toString(),Map.of("name",format+" renamed","expectedRevision",saved.get("revision")));
   assertEquals(value,fixture.service.content(edited.get("id").toString(),null).get("content"));
   Object oversized=format.equals("TXT")?"value\n".repeat(100001):Collections.nCopies(100001,"value");
   byte[] invalid=format.equals("TXT")?oversized.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8):new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(oversized);
   assertThrows(io.metersphere.sdk.exception.MSException.class,()->parser.parse(invalid,format));
   assertThrows(io.metersphere.sdk.exception.MSException.class,()->fixture.service.save(null,Map.of("name","invalid","format",format,"content",oversized)));
  }
 }
 @Test void workbookCountsAllSheetsWhileJsonObjectRemainsOneRecord() throws Exception {
  var fixture=new DataAssetServiceTests();fixture.setup();
  var rows=Collections.nCopies(50001,List.of("value"));
  var sheets=Map.of("sheets",List.of(Map.of("name","one","rows",rows),Map.of("name","two","rows",rows)));
  assertThrows(io.metersphere.sdk.exception.MSException.class,()->fixture.service.save(null,Map.of("name","book","format","XLSX","content",sheets)));
  assertNotNull(fixture.service.save(null,Map.of("name","object","format","JSON","content",Map.of("rows",Collections.nCopies(100001,"business")))));
 }
}
