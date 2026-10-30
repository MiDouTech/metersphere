package io.metersphere.agent.service;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.CustomFieldOption;
import io.metersphere.system.dto.sdk.TemplateCustomFieldDTO;
import io.metersphere.system.dto.sdk.TemplateDTO;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AgentCaseInputValidationTests {
    private TemplateDTO template(){
        var t=new TemplateDTO();t.setId("t");t.setScopeId("p");t.setScene("FUNCTIONAL");
        var f=new TemplateCustomFieldDTO();f.setInternalFieldKey("functional_priority");
        f.setOptions(List.of("P0","P1","P2","P3").stream().map(value->{var o=new CustomFieldOption();o.setValue(value);return o;}).toList());
        t.setCustomFields(List.of(f));return t;
    }
    @Test void rejectsEmptyAndOversizedNames(){
        for(String name:List.of("","  ","x".repeat(256)))assertEquals("CASE_NAME_INVALID",assertThrows(MSException.class,()->AgentCaseInputValidation.name(name)).getMessage());
        assertDoesNotThrow(()->AgentCaseInputValidation.name("正常用例"));
        assertDoesNotThrow(()->AgentCaseInputValidation.name("x".repeat(255)));
    }
    @Test void validatesAgainstActualTemplateOptions(){
        var t=template();assertEquals("P0",AgentCaseInputValidation.priority(t,null));
        assertEquals("P2",AgentCaseInputValidation.priority(t,"P2"));
        assertEquals("CASE_PRIORITY_INVALID",assertThrows(MSException.class,()->AgentCaseInputValidation.priority(t,"P9")).getMessage());
        t.getCustomFields().getFirst().setOptions(List.of());
        assertThrows(MSException.class,()->AgentCaseInputValidation.priority(t,"P0"));
    }
    @Test void explicitTemplateNeverFallsBackOrCrossesProject(){
        assertDoesNotThrow(()->AgentCaseInputValidation.template(template(),"t","p"));
        assertThrows(MSException.class,()->AgentCaseInputValidation.template(template(),"missing","p"));
        assertThrows(MSException.class,()->AgentCaseInputValidation.template(template(),"t","another"));
        var t=template();t.setScene("BUG");assertThrows(MSException.class,()->AgentCaseInputValidation.template(t,"t","p"));
    }
}
