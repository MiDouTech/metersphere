package io.metersphere.functional.service;
import org.junit.jupiter.api.Test;
import io.metersphere.sdk.exception.MSException;
import static org.junit.jupiter.api.Assertions.*;

class FunctionalCaseDefinitionStepsTests {
    @Test void legacyMissingIdsAreAssignedButMalformedDefinitionsAreNotSilentlyEmptied() {
        assertEquals(0,FunctionalCaseService.parseDefinitionSteps("", "fixture").size());
        assertNotNull(FunctionalCaseService.parseDefinitionSteps("[{\"description\":\"step\"}]","fixture").getFirst().getId());
        for(String invalid:new String[]{"null","[null]","{}","not-json"})
            assertThrows(MSException.class,()->FunctionalCaseService.parseDefinitionSteps(invalid,"fixture"));
    }
    @Test void writesRejectInvalidStepsBeforePersistence() {
        FunctionalCaseService.validateWriteSteps("STEP", "[]");
        FunctionalCaseService.validateWriteSteps("TEXT", "ordinary text");
        for (String invalid : new String[]{"PK binary", "{}", "[null]", "[1]", "[] {}", "[{\"description\":42}]"})
            assertThrows(MSException.class, () -> FunctionalCaseService.validateWriteSteps("STEP", invalid));
    }
    @Test void legacyMissingStepIdsAreStableAcrossContractReads() {
        String steps = "[{\"description\":\"step\"}]";
        String first = FunctionalCaseService.parseDefinitionSteps(steps, "one").getFirst().getId();
        assertEquals(first, FunctionalCaseService.parseDefinitionSteps(steps, "one").getFirst().getId());
        assertNotEquals(first, FunctionalCaseService.parseDefinitionSteps(steps, "two").getFirst().getId());
    }
    @Test void deletedCreatorDoesNotBreakHistoricalDetail() {
        var service=new FunctionalCaseService();
        var users=org.mockito.Mockito.mock(io.metersphere.system.mapper.UserMapper.class);
        var modules=org.mockito.Mockito.mock(FunctionalCaseModuleService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"userMapper",users);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"functionalCaseModuleService",modules);
        var detail=new io.metersphere.functional.dto.FunctionalCaseDetailDTO();
        detail.setCreateUser("deleted-user");detail.setModuleId("module");
        assertDoesNotThrow(()->org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"handDTO",detail));
        assertEquals("deleted-user",detail.getCreateUser());assertNull(detail.getCreateUserName());
    }
}
