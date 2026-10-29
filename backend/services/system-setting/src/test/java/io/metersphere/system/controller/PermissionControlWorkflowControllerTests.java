package io.metersphere.system.controller;

import io.metersphere.system.base.BaseTest;
import io.metersphere.system.domain.WorkflowDefinition;
import io.metersphere.system.domain.WorkflowRole;
import io.metersphere.system.dto.permission.control.WorkflowDesignerDTO;
import io.metersphere.system.dto.permission.control.WorkflowValidationDTO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PermissionControlWorkflowControllerTests extends BaseTest {
    @Resource JdbcTemplate jdbcTemplate;

    // All requests are synchronous MockMvc calls in the test transaction. Rollback restores
    // the previous active workflow and removes this test's draft/status/transition data.
    @Test
    @Transactional
    void currentWorkflowApiSupportsDesignPublishActivateAndProtectedDeletion() throws Exception {
        WorkflowDefinition request = new WorkflowDefinition();
        request.setId(UUID.randomUUID().toString());
        request.setCode("CI_" + request.getId());
        request.setName("CI workflow " + request.getId());
        request.setScopeType("SYSTEM");
        request.setScopeId("system");
        request.setScene("BUG");
        WorkflowDefinition flow = getResultData(requestPostWithOkAndReturn("/permission-control/flow/add", request), WorkflowDefinition.class);
        assertEquals("DRAFT", flow.getLifecycle());
        String base = "/permission-control/flow/" + flow.getId();
        WorkflowRole role = new WorkflowRole();
        role.setFlowId(flow.getId());
        role.setCode("CREATOR");
        role.setName("Creator");
        role.setRoleType("FIELD_USER");
        role.setFieldKey("create_user");
        requestPostWithOk("/permission-control/flow/role/add", role);

        WorkflowDesignerDTO design = new WorkflowDesignerDTO();
        var initial = node("OPEN", true, false);
        var terminal = node("CLOSED", false, true);
        design.setStatuses(List.of(initial, terminal));
        WorkflowDesignerDTO.Transition edge = new WorkflowDesignerDTO.Transition();
        edge.setFromId(initial.getId());
        edge.setToId(terminal.getId());
        edge.setEnabled(true);
        design.setTransitions(List.of(edge));
        requestPostWithOk(base + "/designer", design);
        WorkflowDesignerDTO stored = getResultData(requestGetWithOkAndReturn(base + "/designer"), WorkflowDesignerDTO.class);
        assertEquals(2, stored.getStatuses().size());
        assertEquals(1, stored.getTransitions().size());
        WorkflowValidationDTO validation = getResultData(requestPostWithOkAndReturn(base + "/validate", null), WorkflowValidationDTO.class);
        assertTrue(validation.isValid(), () -> validation.getErrors().toString());
        requestPost(base + "/publish?expectedVersion=" + (flow.getVersion() + 1), null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(100409));
        WorkflowDefinition published = getResultData(requestPostWithOkAndReturn(base + "/publish?expectedVersion=" + flow.getVersion(), null), WorkflowDefinition.class);
        assertEquals("PUBLISHED", published.getLifecycle());
        WorkflowDefinition active = getResultData(requestPostWithOkAndReturn(base + "/activate", null), WorkflowDefinition.class);
        assertTrue(active.getActiveForNew());
        requestPost("/permission-control/flow/delete?flowId=" + flow.getId(), null)
                .andExpect(businessError("当前使用中的流程不可删除，请先开启替代流程"));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM workflow_definition WHERE id=?", Integer.class, flow.getId()));
        WorkflowDefinition disabled = new WorkflowDefinition();
        disabled.setId(flow.getId());
        disabled.setEnabled(false);
        requestPostWithOk("/permission-control/flow/enable", disabled);
        requestPostWithOk("/permission-control/flow/delete?flowId=" + flow.getId(), null);
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM workflow_definition WHERE id=?", Integer.class, flow.getId()));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM status_item WHERE flow_id=?", Integer.class, flow.getId()));
    }

    private WorkflowDesignerDTO.Status node(String code, boolean initial, boolean terminal) {
        var status = new WorkflowDesignerDTO.Status();
        status.setId(UUID.randomUUID().toString());
        status.setCode(code);
        status.setName(code);
        status.setInitial(initial);
        status.setTerminal(terminal);
        status.setEnabled(true);
        return status;
    }
}
