package io.metersphere.functional.service;
import com.alibaba.excel.EasyExcel;
import io.metersphere.functional.excel.domain.*;
import io.metersphere.functional.excel.listener.FunctionalCaseImportEventListener;
import io.metersphere.functional.excel.validate.CustomFieldValidatorFactory;
import io.metersphere.functional.asset.service.CaseAssetImportIdentityResolver;
import io.metersphere.functional.request.FunctionalCaseImportRequest;
import io.metersphere.sdk.util.CommonBeanFactory;
import io.metersphere.system.dto.sdk.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CaseAssetExcelRoundTripTests {
 @Test void rejectsTextRenamedToXlsxButAcceptsRealWorkbook() throws Exception {
  assertThrows(Exception.class,()->FunctionalCaseFileService.validateWorkbook(new MockMultipartFile("file","fake.xlsx",null,"not excel".getBytes())));
  var bytes=new ByteArrayOutputStream();try(var workbook=new org.apache.poi.xssf.usermodel.XSSFWorkbook()){workbook.createSheet("cases");workbook.write(bytes);}
  assertDoesNotThrow(()->FunctionalCaseFileService.validateWorkbook(new MockMultipartFile("file","real.xlsx",null,bytes.toByteArray())));
 }
 @Test void realExcelRetainsP1AndUpdatesSameAssetIdentity(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
  var request=new FunctionalCaseImportRequest();request.setProjectId("hub");request.setModuleId("catalog");request.setAssetCatalogImport(true);request.setCover(true);
  var priority=new TemplateCustomFieldDTO();priority.setFieldName("Priority customized");priority.setInternalFieldKey("functional_priority");priority.setType("SELECT");priority.setFieldId("priority-id");priority.setDefaultValue("P0");var option=new io.metersphere.system.domain.CustomFieldOption();option.setValue("P1");option.setText("P1");var p0=new io.metersphere.system.domain.CustomFieldOption();p0.setValue("P0");p0.setText("P0");priority.setOptions(List.of(option,p0));
  var resolver=mock(CaseAssetImportIdentityResolver.class);when(resolver.resolve(request,"existing case")).thenReturn("existing-id");
  var module=mock(FunctionalCaseModuleService.class);when(module.getTree("hub")).thenReturn(List.of());
  var service=mock(FunctionalCaseService.class);var user=mock(SessionUser.class);when(user.getId()).thenReturn("tester");
  Object previousValidator=org.springframework.test.util.ReflectionTestUtils.getField(io.metersphere.system.dto.excel.FunctionalCaseExcelValidateHelper.class,"excelValidateHelper");
  try(var validatorFactory=jakarta.validation.Validation.buildDefaultValidatorFactory();var beans=mockStatic(CommonBeanFactory.class);var validators=mockStatic(CustomFieldValidatorFactory.class);var translations=mockStatic(io.metersphere.sdk.util.Translator.class)){
   var helper=new io.metersphere.system.dto.excel.FunctionalCaseExcelValidateHelper();org.springframework.test.util.ReflectionTestUtils.setField(helper,"validator",validatorFactory.getValidator());helper.initialize();
   translations.when(()->io.metersphere.sdk.util.Translator.get(anyString())).thenAnswer(i->i.getArgument(0));
   beans.when(()->CommonBeanFactory.getBean(FunctionalCaseModuleService.class)).thenReturn(module);
   beans.when(()->CommonBeanFactory.getBean(FunctionalCaseService.class)).thenReturn(service);
   beans.when(()->CommonBeanFactory.getBean(CaseAssetImportIdentityResolver.class)).thenReturn(resolver);
   var validator=new io.metersphere.functional.excel.validate.CustomFieldSelectValidator();
   validators.when(()->CustomFieldValidatorFactory.getValidatorMap("hub")).thenReturn(new HashMap<>(Map.of("SELECT",validator)));
   var listener=new FunctionalCaseImportEventListener(request,FunctionalCaseExcelDataCn.class,List.of(priority),new TreeSet<>(),user,1L);
   var parsed=new ArrayList<FunctionalCaseExcelData>();var actions=new ArrayList<String>();listener.collectPreparedItems((action,row)->{actions.add(action);parsed.add(row);});
   var out=new ByteArrayOutputStream();EasyExcel.write(out).head(List.of(List.of("用例名称"),List.of("用例等级"),List.of("步骤描述"),List.of("预期结果"))).sheet().doWrite(List.of(List.of("existing case","P1","action","expected")));
   EasyExcel.read(new ByteArrayInputStream(out.toByteArray()),listener).sheet().doRead();
   var originalListener=new FunctionalCaseImportEventListener(request,FunctionalCaseExcelDataCn.class,List.of(priority),new TreeSet<>(),user,1L);
   var originalRows=new ArrayList<FunctionalCaseExcelData>();originalListener.collectPreparedItems((action,row)->originalRows.add(row));
   try(var original=getClass().getResourceAsStream("/msp38/export.xlsx")) { EasyExcel.read(original,originalListener).sheet().doRead(); }
   assertEquals(2,originalRows.size(), () -> originalListener.getErrList().toString());
   var originalSteps=io.metersphere.sdk.util.JSON.parseArray(originalRows.getFirst().getSteps(),java.util.Map.class);
   assertEquals(1,originalSteps.size());assertEquals("打开订单并输入2",originalSteps.getFirst().get("desc"));
   assertEquals("金额为20.50，状态未提交",originalSteps.getFirst().get("result"));
   assertEquals("TEXT",originalRows.get(1).getCaseEditType());
   assertEquals("操作A\n操作B",originalRows.get(1).getTextDescription());
   assertEquals("预期A\n预期B",originalRows.get(1).getExpectedResult());
   assertEquals("[]",originalRows.get(1).getSteps());
   var complexDescriptions=List.of("first line\n[2]literal marker", " ", " trailing whitespace \n", "input<br>then submit", "<p>check result</p>", "<P class=\"x\">rich<br/>text</P>");
   var complexExpected=List.of("amount20.50", "", "[1]result marker\nnext line", "<br>literal", "<p>expected</p>", "<P>rich<br />expected</P>");
   var exporter=new FunctionalCaseFileService();
   for (int count:List.of(0,1,3)) {
    var descriptions=new ArrayList<String>();var results=new ArrayList<String>();var numbers=new ArrayList<Object>();
    for(int i=0;i<count;i++){descriptions.add(i==1?"":"amount20.50\ninput<br>"+i);results.add("expected"+i);numbers.add(i+7);}
    String independent="independent<br>\nresult";
    String payload=io.metersphere.functional.excel.util.AssetStepSnapshot.encode(descriptions,results,numbers,independent);
    var file=new ByteArrayOutputStream();EasyExcel.write(file).head(List.of(List.of("用例名称"),List.of("用例等级"),List.of("步骤描述"),List.of("预期结果"),List.of("编辑模式"))).sheet().doWrite(List.of(List.of("snapshot case","P1",payload,io.metersphere.functional.excel.util.AssetStepSnapshot.visible(payload,"results"),"STEP")));
    try(var workbook=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new ByteArrayInputStream(file.toByteArray()))){io.metersphere.functional.excel.util.CaseAssetExcelStepMetadata.hideTransportCells(workbook);file.reset();workbook.write(file);}
    var snapshotListener=new FunctionalCaseImportEventListener(request,FunctionalCaseExcelDataCn.class,List.of(priority),new TreeSet<>(),user,1L);
    snapshotListener.setStepMetadata(io.metersphere.functional.excel.util.CaseAssetExcelStepMetadata.read(new ByteArrayInputStream(file.toByteArray()),0));var snapshotRows=new ArrayList<FunctionalCaseExcelData>();snapshotListener.collectPreparedItems((action,row)->snapshotRows.add(row));EasyExcel.read(new ByteArrayInputStream(file.toByteArray()),snapshotListener).sheet().doRead();
    assertEquals(1,snapshotRows.size(),()->snapshotListener.getErrList().toString());assertEquals(independent,snapshotRows.getFirst().getExpectedResult());
    var savedSteps=io.metersphere.sdk.util.JSON.parseArray(snapshotRows.getFirst().getSteps(),java.util.Map.class);assertEquals(count,savedSteps.size());
    for(int i=0;i<count;i++){assertEquals(numbers.get(i),savedSteps.get(i).get("num"));assertEquals(descriptions.get(i),savedSteps.get(i).get("desc"));assertEquals(results.get(i),savedSteps.get(i).get("result"));}
    if(count>0) {
     var mergedData=new FunctionalCaseExcelData();mergedData.setName("merged snapshot");mergedData.setAssetStepNumbers(numbers);mergedData.setAssetExpectedResult(independent);
     var mergeInfo=new HashMap<Integer,Integer>();var rows=new ArrayList<FunctionalCaseExcelData>();org.springframework.test.util.ReflectionTestUtils.invokeMethod(exporter,"buildExportMergeData",mergeInfo,rows,descriptions,results,mergedData,true);
     var values=new ArrayList<List<Object>>();for(int i=0;i<rows.size();i++)values.add(List.of(i==0?"merged snapshot":"",i==0?"P1":"",rows.get(i).getTextDescription(),rows.get(i).getExpectedResult(),i==0?"STEP":""));
     var heads=List.of(List.of("用例名称"),List.of("用例等级"),List.of("步骤描述"),List.of("预期结果"),List.of("编辑模式"));byte[] merged=Msp38AssetExportTests.exportRows(values,mergeInfo,heads,directory);
     var regions=new TreeSet<ExcelMergeInfo>();EasyExcel.read(new ByteArrayInputStream(merged),null,new io.metersphere.functional.excel.listener.FunctionalCasePretreatmentListener(regions)).extraRead(com.alibaba.excel.enums.CellExtraTypeEnum.MERGE).sheet().doRead();
     var mergedListener=new FunctionalCaseImportEventListener(request,FunctionalCaseExcelDataCn.class,List.of(priority),regions,user,1L);mergedListener.setStepMetadata(io.metersphere.functional.excel.util.CaseAssetExcelStepMetadata.read(new ByteArrayInputStream(merged),0));var imported=new ArrayList<FunctionalCaseExcelData>();mergedListener.collectPreparedItems((action,row)->imported.add(row));EasyExcel.read(new ByteArrayInputStream(merged),mergedListener).sheet().doRead();assertEquals(1,imported.size(),()->mergedListener.getErrList().toString());assertEquals(independent,imported.getFirst().getExpectedResult());var actual=io.metersphere.sdk.util.JSON.parseArray(imported.getFirst().getSteps(),java.util.Map.class);assertEquals(count,actual.size());for(int i=0;i<count;i++)assertEquals(numbers.get(i),actual.get(i).get("num"));
    }
   }
   var complexBlob=new io.metersphere.functional.domain.FunctionalCaseBlob();
   var rawSteps=new ArrayList<Map<String,String>>();for(int step=0;step<complexDescriptions.size();step++)rawSteps.add(Map.of("desc",complexDescriptions.get(step),"result",complexExpected.get(step)));
   complexBlob.setSteps(io.metersphere.sdk.util.JSON.toJSONString(rawSteps).getBytes(java.nio.charset.StandardCharsets.UTF_8));
   var exportedDescriptions=new ArrayList<String>();var exportedExpected=new ArrayList<String>();
   org.springframework.test.util.ReflectionTestUtils.invokeMethod(exporter,"buildExportStep",new FunctionalCaseExcelData(),complexBlob,"STEP",exportedDescriptions,exportedExpected);
   assertEquals(complexDescriptions,exportedDescriptions);assertEquals(complexExpected,exportedExpected);
   String encodedDesc=org.springframework.test.util.ReflectionTestUtils.invokeMethod(exporter,"parseAssetStepData",exportedDescriptions);
   String encodedExpected=org.springframework.test.util.ReflectionTestUtils.invokeMethod(exporter,"parseAssetStepData",exportedExpected);
   var complexWorkbook=new ByteArrayOutputStream();EasyExcel.write(complexWorkbook).head(List.of(List.of("用例名称"),List.of("用例等级"),List.of("步骤描述"),List.of("预期结果"))).sheet().doWrite(List.of(List.of("complex case","P1",encodedDesc,encodedExpected)));
   try(var visibleWorkbook=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new ByteArrayInputStream(complexWorkbook.toByteArray()))) {
    io.metersphere.functional.excel.util.CaseAssetExcelStepMetadata.hideTransportCells(visibleWorkbook);
    assertFalse(visibleWorkbook.getSheetAt(0).getRow(1).getCell(2).getStringCellValue().contains("MSP-STEPS"));
    complexWorkbook.reset();visibleWorkbook.write(complexWorkbook);
   }
   var complexListener=new FunctionalCaseImportEventListener(request,FunctionalCaseExcelDataCn.class,List.of(priority),new TreeSet<>(),user,1L);
   complexListener.setStepMetadata(io.metersphere.functional.excel.util.CaseAssetExcelStepMetadata.read(new ByteArrayInputStream(complexWorkbook.toByteArray()),0));
   var complexRows=new ArrayList<FunctionalCaseExcelData>();complexListener.collectPreparedItems((action,row)->complexRows.add(row));
   EasyExcel.read(new ByteArrayInputStream(complexWorkbook.toByteArray()),complexListener).sheet().doRead();
   assertEquals(1,complexRows.size(),()->complexListener.getErrList().toString());
   var complexSteps=io.metersphere.sdk.util.JSON.parseArray(complexRows.getFirst().getSteps(),java.util.Map.class);
   assertEquals(complexDescriptions.size(),complexSteps.size());
   for(int step=0;step<complexDescriptions.size();step++) { assertEquals(complexDescriptions.get(step),complexSteps.get(step).get("desc"));assertEquals(complexExpected.get(step),complexSteps.get(step).get("result")); }
   for(int count:List.of(1,complexDescriptions.size())) {
    var mergeInfo=new HashMap<Integer,Integer>();var mergeData=new ArrayList<FunctionalCaseExcelData>();
    org.springframework.test.util.ReflectionTestUtils.invokeMethod(exporter,"buildExportMergeData",mergeInfo,mergeData,complexDescriptions.subList(0,count),complexExpected.subList(0,count),new FunctionalCaseExcelData(),true);
    var mergeRows=new ArrayList<List<Object>>();for(int i=0;i<mergeData.size();i++)mergeRows.add(List.of(i==0?"merged case":"",i==0?"P1":"",mergeData.get(i).getTextDescription(),mergeData.get(i).getExpectedResult(),i==0?"STEP":""));
    var heads=List.of(List.of("\u7528\u4f8b\u540d\u79f0"),List.of("\u7528\u4f8b\u7b49\u7ea7"),List.of("\u6b65\u9aa4\u63cf\u8ff0"),List.of("\u9884\u671f\u7ed3\u679c"),List.of("\u7f16\u8f91\u6a21\u5f0f"));
    byte[] merged=Msp38AssetExportTests.exportRows(mergeRows,mergeInfo,heads,directory);
    var regions=new TreeSet<ExcelMergeInfo>();EasyExcel.read(new ByteArrayInputStream(merged),null,new io.metersphere.functional.excel.listener.FunctionalCasePretreatmentListener(regions)).extraRead(com.alibaba.excel.enums.CellExtraTypeEnum.MERGE).sheet().doRead();
    var mergeListener=new FunctionalCaseImportEventListener(request,FunctionalCaseExcelDataCn.class,List.of(priority),regions,user,1L);mergeListener.setStepMetadata(io.metersphere.functional.excel.util.CaseAssetExcelStepMetadata.read(new ByteArrayInputStream(merged),0));var imported=new ArrayList<FunctionalCaseExcelData>();mergeListener.collectPreparedItems((action,row)->imported.add(row));EasyExcel.read(new ByteArrayInputStream(merged),mergeListener).sheet().doRead();assertEquals(1,imported.size(),()->mergeListener.getErrList().toString());var actual=io.metersphere.sdk.util.JSON.parseArray(imported.getFirst().getSteps(),java.util.Map.class);assertEquals(count,actual.size());for(int i=0;i<count;i++){assertEquals(complexDescriptions.get(i),actual.get(i).get("desc"));assertEquals(complexExpected.get(i),actual.get(i).get("result"));}
   }
   String rawText="MSP-TEXT/1\n  <p>literal<br>and rich <b>body</b></p>\n ";
   var textBlob=new io.metersphere.functional.domain.FunctionalCaseBlob();textBlob.setTextDescription(rawText.getBytes(java.nio.charset.StandardCharsets.UTF_8));textBlob.setExpectedResult(rawText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
   var textData=new FunctionalCaseExcelData();
   org.springframework.test.util.ReflectionTestUtils.invokeMethod(exporter,"buildExportStep",textData,textBlob,"TEXT",new ArrayList<String>(),new ArrayList<String>(),true);
   var textWorkbook=new ByteArrayOutputStream();EasyExcel.write(textWorkbook).head(List.of(List.of("\u7528\u4f8b\u540d\u79f0"),List.of("\u7528\u4f8b\u7b49\u7ea7"),List.of("\u6b65\u9aa4\u63cf\u8ff0"),List.of("\u9884\u671f\u7ed3\u679c"),List.of("\u7f16\u8f91\u6a21\u5f0f"))).sheet().doWrite(List.of(List.of("text case","P1",textData.getTextDescription(),textData.getExpectedResult(),"TEXT")));
   try(var wb=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new ByteArrayInputStream(textWorkbook.toByteArray()))){io.metersphere.functional.excel.util.CaseAssetExcelStepMetadata.hideTransportCells(wb);assertEquals(rawText,wb.getSheetAt(0).getRow(1).getCell(2).getStringCellValue());textWorkbook.reset();wb.write(textWorkbook);}
   var textListener=new FunctionalCaseImportEventListener(request,FunctionalCaseExcelDataCn.class,List.of(priority),new TreeSet<>(),user,1L);textListener.setStepMetadata(io.metersphere.functional.excel.util.CaseAssetExcelStepMetadata.read(new ByteArrayInputStream(textWorkbook.toByteArray()),0));var textRows=new ArrayList<FunctionalCaseExcelData>();textListener.collectPreparedItems((action,row)->textRows.add(row));EasyExcel.read(new ByteArrayInputStream(textWorkbook.toByteArray()),textListener).sheet().doRead();assertEquals(1,textRows.size(),()->textListener.getErrList().toString());assertEquals("TEXT",textRows.getFirst().getCaseEditType());assertEquals(rawText,textRows.getFirst().getTextDescription());assertEquals(rawText,textRows.getFirst().getExpectedResult());assertEquals("[]",textRows.getFirst().getSteps());
   String literalPrefix="MSP-TEXT/1\nhello";var plainTextWorkbook=new ByteArrayOutputStream();EasyExcel.write(plainTextWorkbook).head(List.of(List.of("\u7528\u4f8b\u540d\u79f0"),List.of("\u7528\u4f8b\u7b49\u7ea7"),List.of("\u6b65\u9aa4\u63cf\u8ff0"),List.of("\u9884\u671f\u7ed3\u679c"),List.of("\u7f16\u8f91\u6a21\u5f0f"))).sheet().doWrite(List.of(List.of("literal prefix","P1",literalPrefix,literalPrefix,"TEXT")));var plainListener=new FunctionalCaseImportEventListener(request,FunctionalCaseExcelDataCn.class,List.of(priority),new TreeSet<>(),user,1L);var plainRows=new ArrayList<FunctionalCaseExcelData>();plainListener.collectPreparedItems((action,row)->plainRows.add(row));EasyExcel.read(new ByteArrayInputStream(plainTextWorkbook.toByteArray()),plainListener).sheet().doRead();assertEquals(1,plainRows.size());assertEquals(literalPrefix,plainRows.getFirst().getTextDescription());assertEquals(literalPrefix,plainRows.getFirst().getExpectedResult());
   assertEquals(List.of("UPDATE"),actions);assertEquals("existing-id",parsed.getFirst().getNum());assertEquals("P1",parsed.getFirst().getCustomData().get("Priority customized"));
   var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1","sa","");
   var jdbc=new org.springframework.jdbc.core.JdbcTemplate(ds);
   jdbc.execute("CREATE TABLE functional_case_custom_field(case_id varchar,field_id varchar,`value` varchar)");
   var factory=new org.mybatis.spring.SqlSessionFactoryBean();factory.setDataSource(ds);
   factory.setMapperLocations(new org.springframework.core.io.ClassPathResource("io/metersphere/functional/mapper/FunctionalCaseCustomFieldMapper.xml"));
   try(var sql=factory.getObject().openSession(true)){
    var mapper=sql.getMapper(io.metersphere.functional.mapper.FunctionalCaseCustomFieldMapper.class);
    org.springframework.test.util.ReflectionTestUtils.invokeMethod(new FunctionalCaseService(),"handleImportCustomField",parsed.getFirst(),"existing-id",mapper,Map.of(priority.getFieldName(),priority),"tester");
   }
   assertEquals("P1",jdbc.queryForObject("SELECT `value` FROM functional_case_custom_field WHERE case_id='existing-id' AND field_id='priority-id'",String.class));
   var logRoot=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);var safeLogs=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();safeLogs.start();logRoot.addAppender(safeLogs);
   try(var invalidValidator=mockStatic(io.metersphere.system.dto.excel.FunctionalCaseExcelValidateHelper.class)) {
    String secret="msp38-listener-secret-sentinel";invalidValidator.when(()->io.metersphere.system.dto.excel.FunctionalCaseExcelValidateHelper.validateEntity(any())).thenThrow(new NoSuchFieldException(secret));
    var brokenListener=new FunctionalCaseImportEventListener(request,FunctionalCaseExcelDataCn.class,List.of(priority),new TreeSet<>(),user,1L);var broken=new FunctionalCaseExcelData();broken.setName("broken schema");org.springframework.test.util.ReflectionTestUtils.invokeMethod(brokenListener,"buildUpdateOrErrorList",1,broken);
    assertFalse(safeLogs.list.stream().anyMatch(event->event.getFormattedMessage().contains(secret)));assertTrue(safeLogs.list.stream().allMatch(event->event.getThrowableProxy()==null));assertTrue(safeLogs.list.stream().anyMatch(event->event.getFormattedMessage().contains("phase=IMPORT_VALIDATION")));assertFalse(brokenListener.getErrList().isEmpty());
   } finally {logRoot.detachAppender(safeLogs);safeLogs.stop();}
   // A second import may commit after parsing: lock and resolve again inside the write transaction.
   for(boolean cover:List.of(true,false)){
    request.setCover(cover);reset(resolver,service);when(resolver.resolve(request,"existing case")).thenReturn(null,"concurrent-id");
    var deferred=new FunctionalCaseImportEventListener(request,FunctionalCaseExcelDataCn.class,List.of(priority),new TreeSet<>(),user,1L);deferred.deferWrites();
    EasyExcel.read(new ByteArrayInputStream(out.toByteArray()),deferred).sheet().doRead();deferred.writePrepared();
    verify(resolver).lockCatalog(request);verify(service,never()).saveImportData(anyList(),any(),anyList(),anyMap(),anyMap(),any(),any());
    assertEquals(cover?1:0,deferred.getSuccessCount());assertEquals(cover?0:1,deferred.getSkippedCount());
    if(cover) verify(service).updateImportData(argThat(rows->"concurrent-id".equals(rows.getFirst().getNum())),eq(request),anyList(),anyMap(),anyMap(),eq(user));
   }


  } finally {org.springframework.test.util.ReflectionTestUtils.setField(io.metersphere.system.dto.excel.FunctionalCaseExcelValidateHelper.class,"excelValidateHelper",previousValidator);}
 }
 @Test void xmindSkipOnlyDoesNotCountAsWrittenOrFailed(){
  var service=new FunctionalCaseFileService();var request=new FunctionalCaseImportRequest();request.setProjectId("hub");request.setVersionId("v");
  var cases=mock(FunctionalCaseService.class);when(cases.getNextOrder("hub")).thenReturn(1L);
  org.springframework.test.util.ReflectionTestUtils.setField(service,"functionalCaseService",cases);
  var templates=mock(io.metersphere.project.service.ProjectTemplateService.class);var template=new io.metersphere.system.dto.sdk.TemplateDTO();template.setCustomFields(List.of());
  when(templates.getDefaultTemplateDTO(anyString(),anyString())).thenReturn(template);org.springframework.test.util.ReflectionTestUtils.setField(service,"projectTemplateService",templates);
  try(var translations=mockStatic(io.metersphere.sdk.util.Translator.class);var parsers=mockConstruction(io.metersphere.functional.xmind.parser.XMindCaseParser.class,(parser,context)->{
   when(parser.parse(any())).thenReturn(new ArrayList<>());when(parser.getList()).thenReturn(List.of());when(parser.getUpdateList()).thenReturn(List.of());when(parser.getCheckSuccessList()).thenReturn(List.of(new FunctionalCaseExcelData()));
  })){
   var response=service.importXMind(request,mock(SessionUser.class),new MockMultipartFile("file","fixture.xmind",null,new byte[]{1}));
   assertEquals(0,response.getSuccessCount());assertEquals(0,response.getFailCount());assertEquals(1,response.getSkippedCount());
  }
 }
}
