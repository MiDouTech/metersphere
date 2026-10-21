package io.metersphere.agent.service;
import io.metersphere.sdk.exception.MSException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AgentBugTransferTests {
    private Map<String,Object> file(String[] headers, String[] values, boolean formula)throws Exception {
        try(var book=new XSSFWorkbook();var out=new ByteArrayOutputStream()){
            var sheet=book.createSheet("import");var head=sheet.createRow(0);var row=sheet.createRow(1);
            for(int i=0;i<headers.length;i++){head.createCell(i).setCellValue(headers[i]);row.createCell(i).setCellValue(values[i]);}
            if(formula)row.getCell(0).setCellFormula("1+1");book.write(out);
            return Map.of("fileName","bugs.xlsx","base64",Base64.getEncoder().encodeToString(out.toByteArray()));
        }
    }
    @Test void parsesRealXlsxAndMapsCustomFieldIds()throws Exception {
        var rows=AgentBugTransferService.readRows(file(new String[]{"title","description","handleUser","custom:degree"},new String[]{"Checkout fails","Actual error","user-1","severity"},false));
        assertEquals(1,rows.size());assertEquals("Checkout fails",rows.getFirst().get("title"));assertEquals(Map.of("degree","severity"),rows.getFirst().get("customFields"));
    }
    @Test void rejectsFormulaRatherThanExecutingIt()throws Exception {var args=file(new String[]{"title"},new String[]{"error"},true);assertEquals("BUG_IMPORT_FILE_INVALID",assertThrows(MSException.class,()->AgentBugTransferService.readRows(args)).getMessage());}
    @Test void rejectsDuplicateHeaders()throws Exception {var args=file(new String[]{"title","title"},new String[]{"a","b"},false);assertThrows(MSException.class,()->AgentBugTransferService.readRows(args));}
    @Test void requiresTitleHeader()throws Exception {var args=file(new String[]{"description"},new String[]{"a"},false);assertThrows(MSException.class,()->AgentBugTransferService.readRows(args));}
    @Test void rejectsAmbiguousInput(){assertThrows(MSException.class,()->AgentBugTransferService.readRows(Map.of("rows",List.of(Map.of("title","a")),"base64","AA==")));}
    @Test void rejectsMoreThan100Rows(){assertThrows(MSException.class,()->AgentBugTransferService.readRows(Map.of("rows",Collections.nCopies(101,Map.of("title","a")))));}
    @Test void rejectsMalformedWorkbook(){assertThrows(MSException.class,()->AgentBugTransferService.readRows(Map.of("fileName","a.xlsx","base64","aGVsbG8=")));}
    @Test void rejectsOldBinaryWorkbookDisguisedAsXlsx()throws Exception {
        try(var book=new org.apache.poi.hssf.usermodel.HSSFWorkbook();var out=new ByteArrayOutputStream()){
            var sheet=book.createSheet();sheet.createRow(0).createCell(0).setCellValue("title");sheet.createRow(1).createCell(0).setCellValue("a");book.write(out);
            var args=Map.<String,Object>of("fileName","bugs.xlsx","base64",Base64.getEncoder().encodeToString(out.toByteArray()));
            assertEquals("BUG_IMPORT_FILE_INVALID",assertThrows(MSException.class,()->AgentBugTransferService.readRows(args)).getMessage());
        }
    }
    @Test void acceptsStructuredRows(){assertEquals(1,AgentBugTransferService.readRows(Map.of("rows",List.of(Map.of("title","a")))).size());}
}
