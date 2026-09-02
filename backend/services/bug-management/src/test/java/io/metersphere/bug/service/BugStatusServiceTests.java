package io.metersphere.bug.service;

import io.metersphere.plugin.platform.dto.SelectOption;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;

class BugStatusServiceTests {

    @Test
    void listFilterGroupsSynonymousAndDuplicateStatuses() {
        BugStatusService service = spy(new BugStatusService());
        doReturn(List.of(
                new SelectOption("新建", "status-1"),
                new SelectOption("创建", "status-2"),
                new SelectOption("新建", "status-1"),
                new SelectOption("已关闭", "status-3")))
                .when(service).getHeaderStatusOption("project");

        List<SelectOption> result = service.getGroupedHeaderStatusOption("project");

        assertEquals(2, result.size());
        assertEquals("新建", result.getFirst().getText());
        assertEquals("status-1|status-2", result.getFirst().getValue());
    }
}
