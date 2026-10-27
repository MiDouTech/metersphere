package io.metersphere.functional.service;

import io.metersphere.functional.excel.constants.FunctionalCaseImportFiled;
import io.metersphere.functional.excel.domain.FunctionalCaseExcelData;
import io.metersphere.functional.excel.domain.FunctionalCaseHeader;
import io.metersphere.functional.request.FunctionalCaseExportRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Export field identities are independent of translated display titles. */
public final class FunctionalCaseExportRowMapper {
    private FunctionalCaseExportRowMapper() { }

    public static List<Object> row(FunctionalCaseExportRequest request, FunctionalCaseExcelData data) {
        List<Object> values = new ArrayList<>();
        for (FunctionalCaseHeader header : request.getSystemFields()) {
            String id = switch (header.getId()) {
                case "num" -> "id";
                case "text_description" -> "textDescription";
                case "expected_result" -> "expectedResult";
                default -> header.getId();
            };
            Object value = null;
            for (FunctionalCaseImportFiled field : FunctionalCaseImportFiled.values()) {
                if (field.getValue().equals(id)) {
                    value = "name".equals(id) && data.getHyperLinkName() != null ? data.getHyperLinkName() : field.parseExcelDataValue(data);
                    break;
                }
            }
            if (value == null) value = lookup(data.getCustomData(), header);
            values.add(value == null ? "" : value);
        }
        for (FunctionalCaseHeader header : request.getCustomFields()) values.add(lookup(data.getCustomData(), header));
        for (FunctionalCaseHeader header : request.getOtherFields()) values.add(lookup(data.getOtherFields(), header));
        if (request.getSystemFields().stream().anyMatch(field -> "text_description".equals(field.getId()))) values.add(data.getCaseEditType());
        return values;
    }

    private static Object lookup(Map<String, ?> values, FunctionalCaseHeader header) {
        if (values == null) return "";
        Object value = values.get(header.getId());
        if (value == null) value = values.get(header.getName());
        return value == null ? "" : value;
    }
}
