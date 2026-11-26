package io.metersphere.functional.excel.util;

import io.metersphere.functional.excel.constants.FunctionalCaseImportFiled;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.poi.ss.usermodel.*;

import java.io.InputStream;
import java.io.IOException;
import java.util.*;

/** Exact step boundaries live in invisible cell notes; visible Excel text remains readable/editable. */
public final class CaseAssetExcelStepMetadata {
    private static final String PREFIX = "MSP_STEP_METADATA/1\n";
    private static final String TEXT_PREFIX = "MSP-TEXT/1\n";
    public static String encodeText(String text) { return TEXT_PREFIX + CaseStepSplitUtils.encodeExportSteps(List.of(text)); }
    public static String decodeText(String encoded) {
        if (encoded == null || !encoded.startsWith(TEXT_PREFIX)) return encoded;
        String framed = encoded.substring(TEXT_PREFIX.length());
        if (!framed.startsWith("MSP-STEPS/1\n")) throw new MSException("Invalid text metadata; export the file again");
        List<String> parts = CaseStepSplitUtils.splitCell(framed);
        if (parts.size() != 1) throw new MSException("文本元数据无效，请重新导出后重试");
        return parts.getFirst();
    }
    private static boolean isTransport(String text) { return text.startsWith(TEXT_PREFIX) || text.startsWith("MSP-STEPS/1\n"); }
    private static String transportVisible(String encoded) { return encoded.startsWith(TEXT_PREFIX) ? decodeText(encoded) : visible(CaseStepSplitUtils.splitCell(encoded)); }
    public record CellValue(String visibleHash, String encoded) { }
    private CaseAssetExcelStepMetadata() { }

    public static void hideTransportCells(Workbook workbook) {
        for (Sheet sheet : workbook) {
            Row header = sheet.getRow(0);
            if (header == null) continue;
            Set<Integer> columns = new HashSet<>();
            for (Cell cell : header) {
                if (cell.getCellType() == CellType.STRING && isStepHeader(cell.getStringCellValue())) columns.add(cell.getColumnIndex());
            }
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                for (int column : columns) {
                    Cell cell = row.getCell(column);
                    if (cell == null || cell.getCellType() != CellType.STRING) continue;
                    String encoded = cell.getStringCellValue();
                    if (!isTransport(encoded) || !encoded.endsWith("MSP-STEPS-END")) continue;
                    String text = transportVisible(encoded);
                    var helper = workbook.getCreationHelper();
                    var anchor = helper.createClientAnchor();
                    anchor.setRow1(row.getRowNum());
                    anchor.setCol1(column);
                    var comment = sheet.createDrawingPatriarch().createCellComment(anchor);
                    comment.setAuthor("MSP");
                    comment.setString(helper.createRichTextString(PREFIX + hash(text) + "\n" + encoded));
                    comment.setVisible(false);
                    // EasyExcel emits inlineStr. POI otherwise leaves the old <is>
                    // value alongside the new <v>; clear it before rebuilding the value.
                    cell.setBlank();
                    cell.setCellValue(text);
                    cell.setCellComment(comment);
                }
            }
        }
    }

    public static Map<Integer, Map<Integer, CellValue>> read(InputStream input, int sheetIndex) throws IOException {
        try (input; Workbook workbook = WorkbookFactory.create(input)) {
            Map<Integer, Map<Integer, CellValue>> result = new HashMap<>();
            Sheet sheet = workbook.getSheetAt(sheetIndex);
            int size = 0;
            for (var entry : sheet.getCellComments().entrySet()) {
                String note = entry.getValue().getString().getString();
                if (!note.startsWith(PREFIX)) continue;
                size += note.length();
                if (size > 8 * 1024 * 1024) throw new MSException("步骤元数据过大，请拆分文件");
                int separator = note.indexOf('\n', PREFIX.length());
                if (separator < 0) continue;
                String digest = note.substring(PREFIX.length(), separator);
                String encoded = note.substring(separator + 1);
                if (!digest.matches("[a-f0-9]{64}") || !isTransport(encoded)) continue;
                var address = entry.getKey();
                Row header = sheet.getRow(0);
                Cell title = header == null ? null : header.getCell(address.getColumn());
                Row sourceRow = sheet.getRow(address.getRow());
                Cell cell = sourceRow == null ? null : sourceRow.getCell(address.getColumn());
                if (title == null || title.getCellType() != CellType.STRING || !isStepHeader(title.getStringCellValue())
                        || cell == null || cell.getCellType() != CellType.STRING || !hash(cell.getStringCellValue()).equals(digest)) continue;
                try {
                    if (!transportVisible(encoded).equals(cell.getStringCellValue()))
                        throw new MSException("步骤元数据与单元格内容不一致，请重新导出后重试");
                } catch (MSException invalid) { throw invalid; }
                catch (RuntimeException invalid) { throw new MSException("步骤元数据已损坏，请重新导出后重试"); }
                result.computeIfAbsent(address.getRow(), ignored -> new HashMap<>()).put(address.getColumn(), new CellValue(hash(cell.getStringCellValue().trim()), encoded));
            }
            return result;
        }
    }

    public static void restore(Map<Integer, String> row, int rowIndex, Map<Integer, Map<Integer, CellValue>> metadata) {
        metadata.getOrDefault(rowIndex, Map.of()).forEach((column, value) -> {
            if (hash(row.get(column) == null ? "" : row.get(column).trim()).equals(value.visibleHash())) row.put(column, value.encoded().startsWith(TEXT_PREFIX) ? decodeText(value.encoded()) : value.encoded());
        });
    }

    private static String visible(List<String> parts) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) text.append('[').append(i + 1).append(']').append(parts.get(i)).append('\n');
        return text.toString();
    }

    private static String hash(String text) { return DigestUtils.sha256Hex(text == null ? "" : text); }
    private static boolean isStepHeader(String name) {
        return FunctionalCaseImportFiled.TEXT_DESCRIPTION.getFiledLangMap().containsValue(name)
                || FunctionalCaseImportFiled.EXPECTED_RESULT.getFiledLangMap().containsValue(name);
    }
}
