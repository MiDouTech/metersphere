package io.metersphere.functional.service;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.excel.domain.*;
import io.metersphere.functional.request.FunctionalCaseExportRequest;
import io.metersphere.system.mapper.UserMapper;
import io.metersphere.system.domain.User;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class Msp38AssetExportTests {
 @Test void rejectsAssetFileLargerThan15MbBeforeUploading() throws Exception {
  var service=new FunctionalCaseFileService();
  var file=java.io.File.createTempFile("msp38-limit", ".xlsx");
  try(var out=new java.io.RandomAccessFile(file,"rw")) {
   out.setLength(15_000_001L);
   var error=assertThrows(io.metersphere.sdk.exception.MSException.class,()->ReflectionTestUtils.invokeMethod(service,"validateAssetExportSize",file));
   assertTrue(error.getMessage().contains("15"));
   out.setLength(15_000_000L);
   assertDoesNotThrow(()->ReflectionTestUtils.invokeMethod(service,"validateAssetExportSize",file));
  } finally { java.nio.file.Files.deleteIfExists(file.toPath()); }
 }
 @Test void assetExportResolvesOnlyReferencedAuthorsOutsideHubMembership() {
  var service=new FunctionalCaseFileService();var users=mock(UserMapper.class);
  ReflectionTestUtils.setField(service,"userMapper",users);
  var user=new User();user.setName("author");when(users.selectByPrimaryKey("creator")).thenReturn(user);
  var request=new FunctionalCaseExportRequest();request.setProjectId("hub");request.setAssetCatalogId("catalog");
  var create=new FunctionalCaseHeader();create.setId("create_user");var update=new FunctionalCaseHeader();update.setId("update_user");request.setOtherFields(List.of(create,update));
  var source=new FunctionalCase();source.setCreateUser("creator");source.setUpdateUser("missing");var data=new FunctionalCaseExcelData();
  var taskAuthors=new HashMap<String,String>();
  for(int i=0;i<3;i++) ReflectionTestUtils.invokeMethod(service,"buildExportOtherField",source,data,Map.of(),Map.of(),Map.of(),request,taskAuthors);
  assertEquals("author",data.getOtherFields().get("create_user"));assertEquals("",data.getOtherFields().get("update_user"));
  verify(users).selectByPrimaryKey("creator");verify(users).selectByPrimaryKey("missing");verifyNoMoreInteractions(users);
 }
 @Test void mergedAssetTextWorkbookShowsOriginalInsteadOfTransport(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
  var service=new FunctionalCaseFileService();ReflectionTestUtils.setField(service,"exportTaskService",mock(FunctionalCaseExportTaskService.class));
  String raw="<p>literal<br>and <b>rich</b></p>";
  var request=new FunctionalCaseExportRequest();request.setAssetCatalogId("catalog");request.setIsMerge(true);request.setFileId("case");
  var batchType=Class.forName(FunctionalCaseFileService.class.getName()+"$ExcelBatch");var batchCtor=batchType.getDeclaredConstructors()[0];batchCtor.setAccessible(true);
  var batch=batchCtor.newInstance(List.of(List.of(io.metersphere.functional.excel.util.CaseAssetExcelStepMetadata.encodeText(raw))),Map.of());
  var snapshotType=Class.forName(FunctionalCaseFileService.class.getName()+"$ExcelSnapshot");var snapshotCtor=snapshotType.getDeclaredConstructors()[0];snapshotCtor.setAccessible(true);
  var snapshot=snapshotCtor.newInstance(List.of(),List.of(),List.of(List.of("Text description")),Map.of(),Map.of(),List.of(batch));
  try(var translations=mockStatic(io.metersphere.sdk.util.Translator.class)) {
   translations.when(()->io.metersphere.sdk.util.Translator.get(anyString())).thenReturn("cases");
   List<java.io.File> files=ReflectionTestUtils.invokeMethod(service,"generateCaseExportExcel",snapshot,directory.toString(),request,"task");
   try(var wb=org.apache.poi.ss.usermodel.WorkbookFactory.create(files.getFirst())){assertEquals(raw,wb.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());assertNotNull(wb.getSheetAt(0).getRow(1).getCell(0).getCellComment());}
  }
 }

 static byte[] exportRows(List<List<Object>> rows,Map<Integer,Integer> merges,List<List<String>> heads,java.nio.file.Path directory) throws Exception {
  var service=new FunctionalCaseFileService();ReflectionTestUtils.setField(service,"exportTaskService",mock(FunctionalCaseExportTaskService.class));var request=new FunctionalCaseExportRequest();request.setAssetCatalogId("catalog");request.setIsMerge(true);request.setFileId("merged");
  var batchCtor=Class.forName(FunctionalCaseFileService.class.getName()+"$ExcelBatch").getDeclaredConstructors()[0];batchCtor.setAccessible(true);var batch=batchCtor.newInstance(rows,merges);
  var snapshotCtor=Class.forName(FunctionalCaseFileService.class.getName()+"$ExcelSnapshot").getDeclaredConstructors()[0];snapshotCtor.setAccessible(true);var snapshot=snapshotCtor.newInstance(List.of(),List.of(),heads,Map.of(),Map.of(),List.of(batch));
  List<java.io.File> files=ReflectionTestUtils.invokeMethod(service,"generateCaseExportExcel",snapshot,directory.toString(),request,"task");return java.nio.file.Files.readAllBytes(files.getFirst().toPath());
 }

}
