package io.metersphere.functional.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FunctionalCaseModuleServiceTests {

    @Test
    void importedModulePathsUseNaturalNameOrder() {
        List<String> paths = new ArrayList<>(List.of(
                "/root/F10", "/root/F2", "/root/F01", "/root/F1", "/root/F02"));

        paths.sort(FunctionalCaseModuleService::compareNaturalPath);

        assertEquals(List.of("/root/F1", "/root/F01", "/root/F2", "/root/F02", "/root/F10"), paths);
    }
}
