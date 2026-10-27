package io.metersphere.functional.service;

import io.metersphere.functional.excel.domain.FunctionalCaseExcelData;
import io.metersphere.functional.excel.domain.FunctionalCaseHeader;
import io.metersphere.functional.request.FunctionalCaseExportRequest;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FunctionalCaseExportRowMapperTests {
    private FunctionalCaseHeader field(String id,String title) {
        var field=new FunctionalCaseHeader();field.setId(id);field.setName(title);return field;
    }

    @Test void translatedOrMissingDisplayTitlesNeverDecideExportContent() {
        var request=new FunctionalCaseExportRequest();
        request.setSystemFields(List.of(field("num","ID"),field("name","Not Support Key: name"),field("text_description","步骤"),field("expected_result","预期")));
        var data=new FunctionalCaseExcelData();data.setNum("100005");data.setName("订单用例");data.setTextDescription("1.提交");data.setExpectedResult("成功");data.setCaseEditType("STEP");
        assertEquals(List.of("100005","订单用例","1.提交","成功","STEP"),FunctionalCaseExportRowMapper.row(request,data));
        request.getSystemFields().forEach(header->header.setName("any locale"));
        assertEquals(List.of("100005","订单用例","1.提交","成功","STEP"),FunctionalCaseExportRowMapper.row(request,data));
    }

    @Test void dynamicAndOtherValuesUseFieldIdsInsteadOfSameNamedTitles() {
        var request=new FunctionalCaseExportRequest();
        request.setSystemFields(List.of(field("priority-field","translated priority")));
        request.setCustomFields(List.of(field("custom-field","same title")));
        request.setOtherFields(List.of(field("create_user","same title")));
        var data=new FunctionalCaseExcelData();data.setCustomData(Map.of("priority-field","P0","custom-field","custom"));data.setOtherFields(Map.of("create_user","Alice"));
        assertEquals(List.of("P0","custom","Alice"),FunctionalCaseExportRowMapper.row(request,data));
    }
}
