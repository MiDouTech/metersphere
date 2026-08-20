package io.metersphere.plan.controller;

import io.metersphere.functional.request.FunctionalCasePageRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class TestPlanAssociateControllerUnitTests {

    @Test
    void functionalCaseSortQualifiesColumnsFromJoinedTables() {
        FunctionalCasePageRequest request = new FunctionalCasePageRequest();
        Assertions.assertEquals("functional_case.pos desc", TestPlanAssociateController.functionalCaseSort(request));

        request.setSort(Map.of("pos", "desc"));
        Assertions.assertEquals("functional_case.pos DESC,functional_case.id DESC",
                TestPlanAssociateController.functionalCaseSort(request));
    }
}
