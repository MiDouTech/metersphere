package io.metersphere.functional.service;
import com.alibaba.excel.EasyExcel;
import io.metersphere.functional.excel.util.FunctionalCaseWorkbookSheets;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FunctionalCaseWorkbookSheetsTests {
    private MockMultipartFile workbook(String... headers) {
        return workbook(com.alibaba.excel.support.ExcelTypeEnum.XLSX,headers);
    }
    private MockMultipartFile workbook(com.alibaba.excel.support.ExcelTypeEnum type,String... headers) {
        var output=new ByteArrayOutputStream();
        try(var writer=EasyExcel.write(output).excelType(type).build()) {
            for(int i=0;i<headers.length;i++) writer.write(List.of(List.of(headers[i],"其他列"),List.of("example","value")),EasyExcel.writerSheet(i,"sheet"+i).build());
        }
        return new MockMultipartFile("file","cases.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",output.toByteArray());
    }
    @Test void caseWorkbookWithQualityReportSelectsOnlyTheCaseSheet() throws Exception {
        assertEquals(List.of(0),FunctionalCaseWorkbookSheets.select(workbook("用例名称","项目/覆盖项"),Long.MAX_VALUE));
    }
    @Test void reportCanComeFirstAndAllSupportedCaseSheetsArePreserved() throws Exception {
        assertEquals(List.of(1,2,3),FunctionalCaseWorkbookSheets.select(workbook("说明","用例名称","用例名稱","Name","质量报告"),Long.MAX_VALUE));
    }
    @Test void auxiliaryOnlyWorkbookReturnsAnActionableStableError() {
        var failure=assertThrows(MSException.class,()->FunctionalCaseWorkbookSheets.select(workbook("项目/覆盖项","说明"),Long.MAX_VALUE));
        assertEquals("IMPORT_CASE_SHEET_NOT_FOUND",failure.getMessage());
    }
    @Test void aSingleSheetKeepsItsExistingImportScope() throws Exception {
        assertEquals(List.of(0),FunctionalCaseWorkbookSheets.select(workbook("用例名称"),Long.MAX_VALUE));
    }
    @Test void deadlineIsStillEnforcedBeforeReadingWorksheetHeaders() {
        assertEquals("IMPORT_PARSE_LIMIT",assertThrows(MSException.class,()->FunctionalCaseWorkbookSheets.select(workbook("用例名称"),0)).getMessage());
    }
    @Test void sendingAQualityReportToTheOldParserReproducesTheMissingHeaderFailure() throws Exception {
        var parser=org.mockito.Mockito.mock(io.metersphere.functional.excel.listener.FunctionalCaseImportEventListener.class,org.mockito.Mockito.CALLS_REAL_METHODS);
        org.springframework.test.util.ReflectionTestUtils.setField(parser,"excelDataClass",io.metersphere.functional.excel.domain.FunctionalCaseExcelDataCn.class);
        org.springframework.test.util.ReflectionTestUtils.setField(parser,"excelHeadToFieldNameDic",new java.util.HashMap<>());
        try(var translator=org.mockito.Mockito.mockStatic(io.metersphere.sdk.util.Translator.class)) {
            translator.when(()->io.metersphere.sdk.util.Translator.get("case_import_table_header_missing")).thenReturn("missing case-name header");
            var failure=assertThrows(Exception.class,()->EasyExcel.read(workbook("项目/覆盖项").getInputStream(),parser).sheet(0).doRead());
            Throwable root=failure;while(root.getCause()!=null)root=root.getCause();
            assertInstanceOf(MSException.class,root);assertEquals("missing case-name header",root.getMessage());
        }
    }
    @Test void legacyXlsAlsoExcludesReportSheets() throws Exception {
        assertEquals(List.of(1),FunctionalCaseWorkbookSheets.select(workbook(com.alibaba.excel.support.ExcelTypeEnum.XLS,"说明","用例名称"),Long.MAX_VALUE));
    }
}
