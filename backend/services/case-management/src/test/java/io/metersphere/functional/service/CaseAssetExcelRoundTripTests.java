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
 @Test void realExcelRetainsP1AndUpdatesSameAssetIdentity() throws Exception {
  var request=new FunctionalCaseImportRequest();request.setProjectId("hub");request.setModuleId("catalog");request.setAssetCatalogImport(true);request.setCover(true);
  var priority=new TemplateCustomFieldDTO();priority.setFieldName("Priority customized");priority.setInternalFieldKey("functional_priority");priority.setType("SELECT");priority.setFieldId("priority-id");priority.setDefaultValue("P0");var option=new io.metersphere.system.domain.CustomFieldOption();option.setValue("P1");option.setText("P1");priority.setOptions(List.of(option));
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
