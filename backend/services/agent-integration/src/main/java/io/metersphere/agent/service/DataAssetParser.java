package io.metersphere.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.metersphere.sdk.exception.MSException;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

@Component
public class DataAssetParser {
  public Object parse(byte[] bytes, String format) {
    if (bytes.length > io.metersphere.agent.service.DataAssetLimits.MAX_BYTES)
      throw new MSException("文件超过30MB限制");
    try {
      if (format.equals("XLS") || format.equals("XLSX")) {
        boolean ole =
            bytes.length >= 8
                && bytes[0] == (byte) 0xd0
                && bytes[1] == (byte) 0xcf
                && bytes[2] == (byte) 0x11
                && bytes[3] == (byte) 0xe0;
        boolean zip =
            bytes.length >= 4
                && bytes[0] == 'P'
                && bytes[1] == 'K'
                && bytes[2] == 3
                && bytes[3] == 4;
        if (("XLS".equals(format) && !ole) || ("XLSX".equals(format) && !zip))
          throw new MSException("文件实际格式与扩展名不一致");
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
          boolean date1904 =
              workbook instanceof Date1904Support support
                  ? support.isDate1904()
                  : workbook instanceof org.apache.poi.hssf.usermodel.HSSFWorkbook hssf
                      && hssf.getInternalWorkbook().isUsing1904DateWindowing();
          List<Object> sheets = new ArrayList<>();
          int count = 0;
          for (Sheet sheet : workbook) {
            List<Object> rows = new ArrayList<>();
            for (int rowIndex = 0;
                rowIndex <= sheet.getLastRowNum() && sheet.getPhysicalNumberOfRows() > 0;
                rowIndex++) {
              if (++count > DataAssetLimits.MAX_ROWS) throw new MSException("工作表超过100000行限制");
              Row row = sheet.getRow(rowIndex);
              if (row == null) {
                rows.add(List.of());
                continue;
              }
              List<Object> cells = new ArrayList<>();
              for (int i = 0; i < row.getLastCellNum(); i++) {
                Cell c = row.getCell(i);
                if (c == null) {
                  cells.add(null);
                  continue;
                }
                cells.add(
                    switch (c.getCellType()) {
                      case NUMERIC ->
                          DateUtil.isCellDateFormatted(c)
                              ? Map.of(
                                  "type",
                                  "DATE",
                                  "value",
                                  DateUtil.getLocalDateTime(c.getNumericCellValue(), date1904)
                                      .toString(),
                                  "serial",
                                  c.getNumericCellValue(),
                                  "date1904",
                                  date1904,
                                  "format",
                                  c.getCellStyle().getDataFormatString())
                              : c.getNumericCellValue();
                      case BOOLEAN -> c.getBooleanCellValue();
                      case BLANK -> null;
                      case STRING -> c.getStringCellValue();
                      case FORMULA -> "=" + c.getCellFormula();
                      default -> throw new MSException("请将错误单元格转换为值后上传");
                    });
              }
              rows.add(cells);
            }
            sheets.add(Map.of("name", sheet.getSheetName(), "rows", rows));
          }
          return Map.of("sheets", sheets);
        }
      }
      String text =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .decode(java.nio.ByteBuffer.wrap(bytes))
              .toString();
      if (text.startsWith("\uFEFF")) text = text.substring(1);
      if (format.equals("JSON")) {
        Object content = DataAssetJson.mapper()
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .readValue(text, Object.class);
        DataAssetLimits.checkRecords(content, format);
        return content;
      }
      if (format.equals("TXT")) {DataAssetLimits.checkRecords(text, format);return text;}
      if (format.equals("CSV")) {
        List<Object> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quote = false;
        boolean recordStarted = false;
        for (int i = 0; i < text.length(); i++) {
          char c = text.charAt(i);
          recordStarted = true;
          if (c == '"') {
            if (quote && i + 1 < text.length() && text.charAt(i + 1) == '"') {
              cell.append('"');
              i++;
            } else quote = !quote;
          } else if ((c == ',' || c == '\uFF0C') && !quote) {
            row.add(cell.toString());
            if (row.size() > 16384) throw new MSException("CSV超过16384列限制");
            cell.setLength(0);
          } else if ((c == '\n' || c == '\r') && !quote) {
            if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
            row.add(cell.toString());
            if (row.size() > 16384) throw new MSException("CSV超过16384列限制");
            cell.setLength(0);
            if (row.size() > 16384 || rows.size() >= DataAssetLimits.MAX_ROWS)
              throw new MSException("CSV超过100000行或16384列限制");
            rows.add(row);
            row = new ArrayList<>();
            recordStarted = false;
          } else cell.append(c);
        }
        if (quote) throw new MSException("CSV引号未闭合");
        if (recordStarted) {
          row.add(cell.toString());
          if (row.size() > 16384) throw new MSException("CSV超过16384列限制");
          if (row.size() > 16384 || rows.size() >= DataAssetLimits.MAX_ROWS)
            throw new MSException("CSV超过100000行或16384列限制");
          rows.add(row);
        }
        return Map.of("rows", rows);
      }
      throw new MSException("不支持的文件格式");
    } catch (MSException e) {
      org.slf4j.LoggerFactory.getLogger(DataAssetParser.class)
          .warn("data_asset parse failed format={} stage=parse rootType=ContentValidation", format);
      throw e;
    } catch (Exception e) {
      org.slf4j.LoggerFactory.getLogger(DataAssetParser.class)
          .warn(
              "data_asset parse failed format={} stage=parse rootType={}",
              format,
              e.getClass().getSimpleName());
      throw new MSException("文件解析失败，请检查格式及UTF-8编码");
    }
  }
}
