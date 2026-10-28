package io.metersphere.functional.excel.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import io.metersphere.functional.excel.constants.FunctionalCaseImportFiled;
import io.metersphere.sdk.exception.MSException;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Only worksheets with a supported case-name header enter the case parser. */
public final class FunctionalCaseWorkbookSheets {
    private FunctionalCaseWorkbookSheets() { }

    public static List<Integer> select(MultipartFile file, long deadline) throws IOException {
        List<Integer> sheets;
        try (var reader = EasyExcel.read(file.getInputStream()).build()) {
            sheets = reader.excelExecutor().sheetList().stream().map(sheet -> sheet.getSheetNo()).toList();
        }
        if (sheets.size() > 100) throw new MSException("IMPORT_WORKSHEET_LIMIT");
        var selected = new ArrayList<Integer>();
        for (int sheet : sheets) {
            if (System.nanoTime() > deadline) throw new MSException("IMPORT_PARSE_LIMIT");
            var header = new HeaderProbe();
            try (var input = file.getInputStream()) {
                EasyExcel.read(input, header).headRowNumber(1).sheet(sheet).doRead();
            }
            if (header.caseSheet) selected.add(sheet);
        }
        if (selected.isEmpty()) throw new MSException("IMPORT_CASE_SHEET_NOT_FOUND");
        return selected;
    }

    private static final class HeaderProbe extends AnalysisEventListener<Map<Integer,String>> {
        private boolean headerRead;
        private boolean caseSheet;
        @Override public void invokeHeadMap(Map<Integer,String> head, AnalysisContext context) {
            headerRead = true;
            caseSheet = head.values().stream().anyMatch(FunctionalCaseImportFiled.NAME.getFiledLangMap().values()::contains);
        }
        @Override public boolean hasNext(AnalysisContext context) { return !headerRead; }
        @Override public void invoke(Map<Integer,String> row, AnalysisContext context) { }
        @Override public void doAfterAllAnalysed(AnalysisContext context) { }
    }
}
