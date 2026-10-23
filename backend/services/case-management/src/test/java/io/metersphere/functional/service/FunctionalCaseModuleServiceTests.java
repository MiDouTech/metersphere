package io.metersphere.functional.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FunctionalCaseModuleServiceTests {

    @Test
    void missingOrStaleDirectoryRevisionCannotOverwriteNewerChanges() {
        var module = new io.metersphere.functional.domain.FunctionalCaseModule();
        module.setUpdateTime(200L);
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> FunctionalCaseModuleService.requireRevision(module, 200L));
        for (Long revision : java.util.Arrays.asList(null, 199L)) {
            var error = org.junit.jupiter.api.Assertions.assertThrows(io.metersphere.sdk.exception.MSException.class,
                    () -> FunctionalCaseModuleService.requireRevision(module, revision));
            assertEquals(io.metersphere.system.controller.handler.result.MsHttpResultCode.CONFLICT, error.getErrorCode());
        }
    }

    @Test
    void importedModulePathsUseNaturalNameOrder() {
        List<String> paths = new ArrayList<>(List.of(
                "/root/F10", "/root/F2", "/root/F01", "/root/F1", "/root/F02"));

        paths.sort(FunctionalCaseModuleService::compareNaturalPath);

        assertEquals(List.of("/root/F1", "/root/F01", "/root/F2", "/root/F02", "/root/F10"), paths);
    }
}
