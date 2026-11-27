package io.metersphere.agent.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
class DataAssetRecordServiceTests {
 @Test void thirtyMiBSingleFieldsSurviveParsePersistenceRecordsCopyAndUsage() throws Exception {
 var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""));
 var root=java.nio.file.Path.of("").toAbsolutePath();while(!java.nio.file.Files.exists(root.resolve("backend/framework/domain")))root=root.getParent();
 for(String file:List.of("160__data_assets","167__data_asset_record_usage"))for(String sql:java.nio.file.Files.readString(root.resolve("backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_"+file+".sql")).split(";"))if(!sql.isBlank())jdbc.execute(sql);
 var access=mock(GlobalAssetAccess.class);when(access.authorize(any(),anyString())).thenReturn(new GlobalAssetAccess.Scope("org","user"));
 var assets=new DataAssetService(jdbc,access);var records=new DataAssetRecordService(jdbc,assets,access);var parser=new DataAssetParser();
 for(String format:List.of("TXT","CSV","JSON")) {
   String field="x".repeat(DataAssetLimits.MAX_BYTES-(format.equals("JSON")?2:0));
   byte[] bytes=(format.equals("JSON")?"\""+field+"\"":field).getBytes(java.nio.charset.StandardCharsets.UTF_8);
   Object parsed=parser.parse(bytes,format);
   var created=assets.save(null,Map.of("name",format,"format",format,"content",parsed));String id=created.get("id").toString();
   assertEquals(parsed,assets.content(id,null).get("content"));
   var page=records.page(null,id,null,1,100);var items=(List<Map<String,Object>>)page.get("items");assertEquals(1,items.size());
   var keys=List.of(items.getFirst().get("recordKey").toString());assertEquals(field,records.copy(null,id,null,keys).get("content"));
   records.used(null,id,null,keys,"large-"+format);assertEquals(true,((List<Map<String,Object>>)records.page(null,id,null,1,100).get("items")).getFirst().get("used"));
 }
 }
 @Test void assetMapperKeepsFiniteStringAndDepthConstraints() throws Exception {
 var reader=DataAssetJson.mapper();
 assertEquals(DataAssetLimits.MAX_CONTENT_BYTES,reader.getFactory().streamReadConstraints().getMaxStringLength());
 assertEquals(com.fasterxml.jackson.core.StreamReadConstraints.defaults().getMaxNestingDepth(),reader.getFactory().streamReadConstraints().getMaxNestingDepth());
 assertThrows(com.fasterxml.jackson.core.exc.StreamConstraintsException.class,()->reader.readValue("\""+"x".repeat(DataAssetLimits.MAX_CONTENT_BYTES+1000000)+"\"",Object.class));
 }
 @Test void txtQuotesPreservePlainTokensAndCsvEscapes() {
 assertEquals(List.of(List.of("abc\"123\"", "x\"y")),DataAssetRecordService.project("TXT","abc\"123\",x\"y\n"));
 assertEquals(List.of(List.of("a,b", "a，b", "say \"hi\"", "", "")),DataAssetRecordService.project("TXT","\"a,b\"，\"a，b\",\"say \"\"hi\"\"\",,\r\n"));
 assertEquals(List.of(List.of("", ""),List.of("")),DataAssetRecordService.project("TXT",",\n\n"));
 }
 @Test void terminalNewlinesDoNotCreatePhantomRecords() {
 assertEquals(List.of(),DataAssetRecordService.project("TXT",""));
 assertEquals(List.of(List.of("")),DataAssetRecordService.project("TXT","\n"));
 assertEquals(List.of(List.of(""),List.of("")),DataAssetRecordService.project("TXT","\n\n"));
 assertEquals(100000,DataAssetRecordService.project("TXT","x\n".repeat(100000)).size());
 assertEquals(100000,DataAssetRecordService.project("TXT","x\r\n".repeat(100000)).size());
 assertEquals(List.of(List.of("a"),List.of("")),DataAssetRecordService.project("TXT","a\n\n"));
 }
 @Test void formatProjectionPreservesJsonAndTxtEscaping() {
 var object=Map.of("rows",List.of(1),"sheets",List.of(2));
 assertEquals(List.of(object),DataAssetRecordService.project("JSON",object));
 assertEquals(List.of(List.of("a,b","c")),DataAssetRecordService.project("TXT","\"a,b\"，c\n"));
 assertEquals(100000,DataAssetRecordService.project("TXT","x\n".repeat(99999)+"x").size());
 }

 @Test void persistentUsageSurvivesVersionsAndRejectsPayloadReplay() throws Exception {
 var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""));
 var root=java.nio.file.Path.of("").toAbsolutePath();while(!java.nio.file.Files.exists(root.resolve("backend/framework/domain")))root=root.getParent();
 for(String file:List.of("160__data_assets","167__data_asset_record_usage"))for(String sql:java.nio.file.Files.readString(root.resolve("backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_"+file+".sql")).split(";"))if(!sql.isBlank())jdbc.execute(sql);
 var access=mock(GlobalAssetAccess.class);when(access.authorize(any(),anyString())).thenReturn(new GlobalAssetAccess.Scope("org","user"));
 var assets=new DataAssetService(jdbc,access);var service=new DataAssetRecordService(jdbc,assets,access);
 for(String input:List.of("\"\"","\"\"\n\"\"",",，")) {
   var parser=new DataAssetParser();var parsed=parser.parse(input.getBytes(java.nio.charset.StandardCharsets.UTF_8),"CSV");
   var asset=assets.save(null,Map.of("name","empty","format","CSV","content",parsed));String emptyId=asset.get("id").toString();
   var rows=(List<Map<String,Object>>)service.page(null,emptyId,null,1,100).get("items");
   var selected=rows.stream().map(row->row.get("recordKey").toString()).toList();
   String copied=service.copy(null,emptyId,null,selected).get("content").toString();
   assertEquals(parsed,parser.parse(copied.getBytes(java.nio.charset.StandardCharsets.UTF_8),"CSV"));
   assertEquals(((Map<?,?>)parsed).get("rows"),DataAssetRecordService.project("TXT",copied));
 }
 var created=assets.save(null,Map.of("name","one","format","TXT","content","a，b\na，b\n"));String id=created.get("id").toString();
 var page=service.page(null,id,null,1,100);var items=(List<Map<String,Object>>)page.get("items");assertEquals(2,items.size());assertNotEquals(items.get(0).get("recordKey"),items.get(1).get("recordKey"));
 var keys=List.of(items.get(0).get("recordKey").toString());service.used(null,id,null,keys,"request");service.used(null,id,null,keys,"request");
 assertThrows(io.metersphere.sdk.exception.MSException.class,()->service.used(null,id,null,List.of(items.get(1).get("recordKey").toString()),"request"));
 assertFalse(service.copy(null,id,null,keys).get("content").toString().isBlank());
 assets.save(id,Map.of("expectedRevision",1,"name","renamed","format","TXT","content","a，b\na，b\n"));
 var again=new DataAssetRecordService(jdbc,assets,access);assertEquals(true,((List<Map<String,Object>>)again.page(null,id,null,1,100).get("items")).getFirst().get("used"));
 assets.save(id,Map.of("expectedRevision",2,"name","quoted","format","TXT","content","\"a，b\",\"say \"\"hi\"\"\",,plain\"quote\n"));
 var roundtripPage=service.page(null,id,null,1,100);
 var roundtripItems=(List<Map<String,Object>>)roundtripPage.get("items");
 var roundtripKeys=roundtripItems.stream().map(row->row.get("recordKey").toString()).toList();
 String copied=service.copy(null,id,roundtripPage.get("versionId").toString(),roundtripKeys).get("content").toString();
 assertEquals(roundtripItems.stream().map(row->row.get("content")).toList(),DataAssetRecordService.project("TXT",copied));

 }
}
