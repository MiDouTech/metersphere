package io.metersphere.agent.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class DataAssetParserTests {
 @Test void csvQuotedEmptyTailPreservesRecordsAndEnforcesLimit() {
  var parser=new DataAssetParser();
  assertEquals(Map.of("rows",List.of()),parser.parse(new byte[0],"CSV"));
  assertEquals(Map.of("rows",List.of(List.of(""))),parser.parse("\"\"".getBytes(StandardCharsets.UTF_8),"CSV"));
  assertEquals(Map.of("rows",List.of(List.of(""),List.of(""))),parser.parse("\n\"\"".getBytes(StandardCharsets.UTF_8),"CSV"));
  assertEquals(Map.of("rows",List.of(List.of("","",""))),parser.parse(",，".getBytes(StandardCharsets.UTF_8),"CSV"));
  assertEquals(Map.of("rows",List.of(List.of("a\"b",""))),parser.parse("\"a\"\"b\",\"\"".getBytes(StandardCharsets.UTF_8),"CSV"));
  assertEquals(100000,((List<?>)((Map<?,?>)parser.parse(("x\n".repeat(99999)+"\"\"").getBytes(StandardCharsets.UTF_8),"CSV")).get("rows")).size());
  assertThrows(io.metersphere.sdk.exception.MSException.class,()->parser.parse(("x\n".repeat(100000)+"\"\"").getBytes(StandardCharsets.UTF_8),"CSV"));
 }

  @Test
  void excelDateRetainsSerialWindowAndOriginalFormat() throws Exception {
    byte[] bytes;
    try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        var output = new java.io.ByteArrayOutputStream()) {
      if (book.getCTWorkbook().getWorkbookPr() == null) book.getCTWorkbook().addNewWorkbookPr();
      book.getCTWorkbook().getWorkbookPr().setDate1904(true);
      var cell = book.createSheet("dates").createRow(0).createCell(0);
      cell.setCellValue(12345.67890123);
      var style = book.createCellStyle();
      style.setDataFormat(book.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
      cell.setCellStyle(style);
      book.write(output);
      bytes = output.toByteArray();
    }
    var parsed = (Map<?, ?>) parser.parse(bytes, "XLSX");
    var sheet = (Map<?, ?>) ((List<?>) parsed.get("sheets")).get(0);
    var date = (Map<?, ?>) ((List<?>) ((List<?>) sheet.get("rows")).get(0)).get(0);
    assertEquals("DATE", date.get("type"));
    assertEquals(12345.67890123, date.get("serial"));
    assertEquals(true, date.get("date1904"));
    assertEquals("yyyy-mm-dd hh:mm:ss", date.get("format"));
  }

  @Test
  void csvEnforcesSharedRowAndColumnBudgets() {
    assertThrows(
        io.metersphere.sdk.exception.MSException.class,
        () ->
            parser.parse(
                "x\n".repeat(DataAssetLimits.MAX_ROWS + 1).getBytes(StandardCharsets.UTF_8),
                "CSV"));
    assertThrows(
        io.metersphere.sdk.exception.MSException.class,
        () -> parser.parse(("x,".repeat(16384) + "x").getBytes(StandardCharsets.UTF_8), "CSV"));
  }

  @Test
  void workbookPreservesMissingRowsLeadingColumnsAndFormulaLiteral() throws Exception {
    byte[] bytes;
    try (var book = new org.apache.poi.hssf.usermodel.HSSFWorkbook();
        var out = new java.io.ByteArrayOutputStream()) {
      var sheet = book.createSheet("data");
      sheet.createRow(0).createCell(2).setCellValue("x");
      sheet.createRow(2).createCell(0).setCellFormula("1+2");
      book.write(out);
      bytes = out.toByteArray();
    }
    var result = (Map<?, ?>) parser.parse(bytes, "XLS");
    var sheets = (List<?>) result.get("sheets");
    var rows = (List<?>) ((Map<?, ?>) sheets.get(0)).get("rows");
    assertEquals(3, rows.size());
    assertEquals(Arrays.asList(null, null, "x"), rows.get(0));
    assertEquals(List.of(), rows.get(1));
    assertEquals(List.of("=1+2"), rows.get(2));
  }

  private final DataAssetParser parser = new DataAssetParser();

  @Test
  void quotedCsvPreservesNewlinesAndEscapedQuotes() {
    var result =
        (Map<?, ?>)
            parser.parse(
                "a,b\r\n\"x,y\",\"line1\nline2\"\r\n".getBytes(StandardCharsets.UTF_8), "CSV");
    var rows = (List<?>) result.get("rows");
    assertEquals(2, rows.size());
    assertEquals(List.of("x,y", "line1\nline2"), rows.get(1));
  }

  @Test
  void rejectsIncompleteCsv() {
    assertThrows(
        io.metersphere.sdk.exception.MSException.class,
        () -> parser.parse("\"unfinished".getBytes(StandardCharsets.UTF_8), "CSV"));
  }

  @Test
  void jsonPreservesNumericAndNullValues() {
    var result =
        (Map<?, ?>)
            parser.parse("{\"number\":12,\"empty\":null}".getBytes(StandardCharsets.UTF_8), "JSON");
    assertEquals(12, result.get("number"));
    assertTrue(result.containsKey("empty"));
    assertNull(result.get("empty"));
  }

  @Test
  void refusesOversizedInput() {
    assertThrows(
        io.metersphere.sdk.exception.MSException.class,
        () -> parser.parse(new byte[DataAssetLimits.MAX_BYTES + 1], "TXT"));
  }

  @Test
  void refusesMalformedUtf8() {
    assertThrows(
        io.metersphere.sdk.exception.MSException.class,
        () -> parser.parse(new byte[] {(byte) 0xc3, 0x28}, "TXT"));
  }
}
