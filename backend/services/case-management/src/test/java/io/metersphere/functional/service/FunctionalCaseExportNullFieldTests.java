package io.metersphere.functional.service;

import io.metersphere.functional.domain.FunctionalCaseCustomField;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FunctionalCaseExportNullFieldTests {
    @Test void realExportFieldBuilderHandlesNullAndEmptyOptionalValues(){
        var service=new FunctionalCaseFileService();
        var header=new io.metersphere.system.dto.sdk.TemplateCustomFieldDTO();header.setFieldId("optional");header.setFieldName("Optional");header.setType("INPUT");
        var request=new io.metersphere.functional.request.FunctionalCaseExportRequest();request.setProjectId("p");
        try(var factory=mockStatic(io.metersphere.functional.excel.validate.CustomFieldValidatorFactory.class)){
            factory.when(()->io.metersphere.functional.excel.validate.CustomFieldValidatorFactory.getValidatorMap("p")).thenReturn(new HashMap<>());
            var data=new io.metersphere.functional.excel.domain.FunctionalCaseExcelData();
            org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"buildExportCustomField",List.of(header),List.of(field("optional",null)),data,request);
            assertTrue(data.getCustomData().isEmpty());
            org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"buildExportCustomField",List.of(header),List.of(field("optional","")),data,request);
            assertEquals("",data.getCustomData().get("optional"));
        }
    }
    private FunctionalCaseCustomField field(String id,String value){var f=new FunctionalCaseCustomField();f.setFieldId(id);f.setValue(value);return f;}
    @Test void sqlNullOptionalValueDoesNotAbortEntireExport(){
        var values=FunctionalCaseFileService.exportFieldValues(List.of(field("optional",null),field("priority","P2")));
        assertFalse(values.containsKey("optional"));assertEquals("P2",values.get("priority"));
    }
    @Test void preservesEmptyAndOrdinaryValuesWithoutInventingDefaults(){
        var values=FunctionalCaseFileService.exportFieldValues(List.of(field("empty",""),field("text","测试")));
        assertEquals("",values.get("empty"));assertEquals("测试",values.get("text"));assertFalse(values.containsKey("priority"));
    }
    @Test void duplicateIdenticalValuesAreSafeButConflictsAreExplicit(){
        assertEquals(Map.of("x","same"),FunctionalCaseFileService.exportFieldValues(List.of(field("x","same"),field("x","same"),field("x",null))));
        assertEquals("CASE_EXPORT_FIELD_VALUE_CONFLICT",assertThrows(MSException.class,()->FunctionalCaseFileService.exportFieldValues(List.of(field("x","a"),field("x","b")))).getMessage());
    }
    @Test void toleratesAbsentHistoricalFieldRows(){assertTrue(FunctionalCaseFileService.exportFieldValues(null).isEmpty());assertTrue(FunctionalCaseFileService.exportFieldValues(Arrays.asList(null,field(null,"value"))).isEmpty());}
}
