package io.metersphere.functional.service;
import io.metersphere.functional.excel.util.CaseStepSplitUtils;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class Msp38StepRoundTripTests {
 @Test void assetStepExportRetainsIndependentExpectedAndOriginalNumbers() {
  var blob=new io.metersphere.functional.domain.FunctionalCaseBlob();
  blob.setSteps("[{\"num\":7,\"desc\":\"amount20.50\",\"result\":\"step expected\"}]".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  blob.setExpectedResult("independent<br>\nresult".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  var data=new io.metersphere.functional.excel.domain.FunctionalCaseExcelData();
  var descriptions=new java.util.ArrayList<String>();var results=new java.util.ArrayList<String>();
  org.springframework.test.util.ReflectionTestUtils.invokeMethod(new FunctionalCaseFileService(),"buildExportStep",data,blob,"STEP",descriptions,results,true);
  assertEquals("independent<br>\nresult",data.getExpectedResult());
 }
 @Test void exportedNumberedStepsRetainDecimalAndRemoveTransportNumbering() {
  assertEquals(List.of("打开订单并输入2"),CaseStepSplitUtils.splitCell("[1]打开订单并输入2\n"));
  assertEquals(List.of("金额为20.50，状态未提交"),CaseStepSplitUtils.splitCell("[1]金额为20.50，状态未提交\n"));
 }
 @Test void numberedStepsKeepMultilineBodiesAndEmptyPositions() {
  assertEquals(List.of("第一行\n第二行", "" ,"第三步"),CaseStepSplitUtils.splitCell("[1]第一行\n第二行\n[2]\n[3]第三步\n"));
 }
 @Test void decimalsAndVersionsAreNotStepNumbers() {
  assertEquals(List.of("金额20.50，版本1.2.3"),CaseStepSplitUtils.splitCell("金额20.50，版本1.2.3"));
 }
 @Test void framedAssetExportPreservesEmbeddedNumberingWhitespaceAndEmptySteps() {
  var bodies=List.of("第一行\n[2]正文内编号", " 末尾空白 \n", "", "1.正文\n[1]原文");
  var service=new FunctionalCaseFileService();
  String cell=org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"parseAssetStepData",bodies);
  assertEquals(bodies,CaseStepSplitUtils.splitCell(cell));
 }
 @Test void exportRetainsEntirelyBlankMiddleStep() {
  var blob=new io.metersphere.functional.domain.FunctionalCaseBlob();
  blob.setSteps("[{\"desc\":\"first\",\"result\":\"ok\"},{\"desc\":\" \",\"result\":\"\"},{\"desc\":\"third\",\"result\":\"ok\"}]".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  var descriptions=new java.util.ArrayList<String>();var expected=new java.util.ArrayList<String>();
  org.springframework.test.util.ReflectionTestUtils.invokeMethod(new FunctionalCaseFileService(),"buildExportStep",new io.metersphere.functional.excel.domain.FunctionalCaseExcelData(),blob,"STEP",descriptions,expected);
  assertEquals(List.of("first"," ","third"),descriptions);assertEquals(List.of("ok","","ok"),expected);
 }
 @Test void legacyInlineNumberedImportsRemainSupported() {
  assertEquals(List.of("[1]first","[2]second"),CaseStepSplitUtils.splitCell("[1]first[2]second"));
  assertTrue(CaseStepSplitUtils.requiresExportFraming(List.of("literal [2] body")));
 }
}
