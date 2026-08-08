package io.metersphere.functional.service;

import io.metersphere.functional.dto.CaseGenerationCaseDTO;
import io.metersphere.functional.dto.FunctionalCaseStepDTO;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionalCaseAiDraftAgentToolTests {
    private final FunctionalCaseAiDraftService service = new FunctionalCaseAiDraftService();

    @Test
    void rejectsUnknownLevelInsteadOfSilentlyFallingBack() {
        CaseGenerationCaseDTO item = validStepCase();
        item.setLevel("critical");

        MSException error = assertThrows(MSException.class, () -> create(item));

        assertTrue(error.getMessage().contains("level"));
    }

    @Test
    void rejectsStepCaseWithoutSteps() {
        CaseGenerationCaseDTO item = validStepCase();
        item.setSteps(List.of());

        MSException error = assertThrows(MSException.class, () -> create(item));

        assertTrue(error.getMessage().contains("steps"));
    }

    @Test
    void rejectsTextCaseWithoutDescription() {
        CaseGenerationCaseDTO item = validStepCase();
        item.setEditType("TEXT");
        item.setSteps(List.of());
        item.setTextDescription(" ");

        MSException error = assertThrows(MSException.class, () -> create(item));

        assertTrue(error.getMessage().contains("textDescription"));
    }

    private void create(CaseGenerationCaseDTO item) {
        service.createDraftsFromAgent("project-1", "conversation-1", "request-1", "model-1",
                List.of(item), "user-1");
    }

    private CaseGenerationCaseDTO validStepCase() {
        CaseGenerationCaseDTO item = new CaseGenerationCaseDTO();
        item.setName("登录成功");
        item.setLevel("P1");
        item.setEditType("STEP");
        FunctionalCaseStepDTO step = new FunctionalCaseStepDTO();
        step.setDesc("输入正确账号密码并登录");
        step.setResult("登录成功");
        item.setSteps(List.of(step));
        return item;
    }
}
