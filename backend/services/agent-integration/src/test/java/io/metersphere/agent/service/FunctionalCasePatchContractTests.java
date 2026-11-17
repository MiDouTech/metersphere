package io.metersphere.agent.service;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FunctionalCasePatchContractTests {
    @Test void fieldErrorsRetainRepairHintsAndNeverEchoUnknownInput() {
        var error=assertThrows(AgentFieldValidationException.class,()->FunctionalCasePatchContract.validate(
                Map.of("lastExecuteResult","SUCCESS","secret-key-with-payload","do not expose")));
        var safe=new AgentSafeErrorMapper().toApiError(error,"trace-test");
        assertEquals("CASE_PATCH_FIELD_UNSUPPORTED",safe.getCode());
        assertEquals("metersphere.functional.execution.submit",safe.getDetails().get("recommendedTool"));
        assertEquals("FIX_INPUT",safe.getDetails().get("nextAction"));
        assertEquals(false,safe.getDetails().get("retryable"));
        assertFalse(safe.toString().contains("secret-key"));
        assertFalse(safe.toString().contains("do not expose"));
        assertEquals("trace-test",safe.getTraceId());
    }

    @Test void definitionTypesAreValidatedWithoutRejectingLegacyStepFields() {
        assertDoesNotThrow(()->FunctionalCasePatchContract.validate(Map.of("name","valid","steps",List.of(
                Map.of("num",1,"desc","action","expected","result","actualResult","legacy","executeResult","SUCCESS")))));
        assertDoesNotThrow(()->FunctionalCasePatchContract.validate(Map.of("customFields",Map.of("numeric",3,"multi",List.of("a","b")))));
        var clears=new HashMap<String,Object>();
        for(String field:List.of("moduleId","prerequisite","description","tags","steps","customFields","priority")) clears.put(field,null);
        assertDoesNotThrow(()->FunctionalCasePatchContract.validate(clears));
        for(Object invalid:List.of(Map.of(),Map.of("name",5),Map.of("tags",List.of(5)),
                Map.of("steps",List.of(Map.of("num",1.5))),Map.of("steps",List.of(Map.of("unknown",true))),
                Map.of("customFields",List.of("wrong"))))
            assertThrows(AgentFieldValidationException.class,()->FunctionalCasePatchContract.validate(invalid));
    }

    @Test void nativeDiscoveryPublishesTheSameSupportedFields() {
        var schema=FunctionalCasePatchContract.schema();
        assertEquals(false,schema.get("additionalProperties"));
        assertEquals(1,schema.get("minProperties"));
        assertEquals(new HashSet<>(FunctionalCasePatchContract.FIELDS),((Map<?,?>)schema.get("properties")).keySet());
    }
}
