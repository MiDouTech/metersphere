package io.metersphere.project.controller;

import io.metersphere.sdk.constants.*;
import io.metersphere.sdk.util.BeanUtils;
import io.metersphere.system.base.BaseTest;
import io.metersphere.system.controller.OrganizationStatusFlowSettingControllerTest;
import io.metersphere.system.controller.param.StatusDefinitionUpdateRequestDefinition;
import io.metersphere.system.controller.param.StatusFlowUpdateRequestDefinition;
import io.metersphere.system.controller.param.StatusItemAddRequestDefinition;
import io.metersphere.system.controller.param.StatusItemUpdateRequestDefinition;
import io.metersphere.system.domain.OrganizationParameter;
import io.metersphere.system.domain.StatusItem;
import io.metersphere.system.dto.StatusItemDTO;
import io.metersphere.system.dto.sdk.request.StatusDefinitionUpdateRequest;
import io.metersphere.system.dto.sdk.request.StatusFlowUpdateRequest;
import io.metersphere.system.dto.sdk.request.StatusItemAddRequest;
import io.metersphere.system.dto.sdk.request.StatusItemUpdateRequest;
import io.metersphere.system.log.constants.OperationLogType;
import io.metersphere.system.mapper.OrganizationParameterMapper;
import io.metersphere.system.mapper.StatusItemMapper;
import io.metersphere.system.service.BaseStatusDefinitionService;
import io.metersphere.system.service.BaseStatusFlowService;
import io.metersphere.system.service.BaseStatusItemService;
import jakarta.annotation.Resource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static io.metersphere.project.enums.result.ProjectResultCode.PROJECT_TEMPLATE_PERMISSION;
import static io.metersphere.system.controller.handler.result.CommonResultCode.*;
import static io.metersphere.system.controller.handler.result.MsHttpResultCode.NOT_FOUND;

/**
 * @author jianxing
 * @date : 2023-10-9
 */
@SpringBootTest(webEnvironment= SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProjectStatusFlowSettingControllerTest extends BaseTest {

    private static final String BASE_PATH = "/project/status/flow/setting/";
    private static final String GET = "get/{0}/{1}";
    private static final String STATUS_DEFINITION_UPDATE = "status/definition/update";
    private static final String STATUS_ADD = "status/add";
    private static final String STATUS_UPDATE = "status/update";
    private static final String STATUS_SORT = "/status/sort/{0}/{1}";
    private static final String STATUS_DELETE = "status/delete/{0}";
    private static final String STATUS_FLOW_UPDATE = "status/flow/update";

    private static final String LEGACY_CLOSED = "缺陷工作流已迁移至【系统设置 / 权限控制 / 流程控制】，旧入口不再允许写入";
    @Resource
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private String workflowSnapshot() {
        return io.metersphere.sdk.util.JSON.toJSONString(List.of(
                jdbcTemplate.queryForList("SELECT * FROM status_item ORDER BY id"),
                jdbcTemplate.queryForList("SELECT * FROM status_flow ORDER BY id"),
                jdbcTemplate.queryForList("SELECT * FROM status_definition ORDER BY status_id, definition_id")));
    }

    private static StatusItem addStatusItem;
    private static StatusItem anotherAddStatusItem;

    @Resource
    private StatusItemMapper statusItemMapper;
    @Resource
    private OrganizationParameterMapper organizationParameterMapper;
    @Resource
    private BaseStatusItemService baseStatusItemService;
    @Resource
    private BaseStatusFlowService baseStatusFlowService;
    @Resource
    private BaseStatusDefinitionService baseStatusDefinitionService;

    @Override
    protected String getBasePath() {
        return BASE_PATH;
    }

    @Test
    @Order(0)
    public void getStatusFlowSetting() throws Exception {
        // @@校验没有数据的情况
        MvcResult mvcResult = this.requestGetWithOkAndReturn(GET, DEFAULT_PROJECT_ID, TemplateScene.BUG.name());
        List<StatusItemDTO> statusItemDTOS = getResultDataArray(mvcResult, StatusItemDTO.class);
        OrganizationStatusFlowSettingControllerTest.assertDefaultStatusFlowSettingInit(statusItemDTOS);
        // @@校验权限
        requestGetPermissionTest(PermissionConstants.PROJECT_TEMPLATE_READ, GET, DEFAULT_PROJECT_ID, TemplateScene.BUG.name());
    }


    @Test
    @Order(1)
    public void addStatusItem() throws Exception {
        List<StatusItemDTO> before = getResultDataArray(
                requestGetWithOkAndReturn(GET, DEFAULT_PROJECT_ID, "BUG"), StatusItemDTO.class);
        String snapshot = workflowSnapshot();
        StatusItemAddRequest request = new StatusItemAddRequest();
        request.setName("retired-write-must-not-create");
        request.setScene("BUG");
        request.setScopeId(DEFAULT_PROJECT_ID);
        requestPost(STATUS_ADD, request).andExpect(businessError(LEGACY_CLOSED));
        Assertions.assertEquals(snapshot, workflowSnapshot());
    }

    @Test
    @Order(2)
    public void updateStatusItem() throws Exception {
        List<StatusItemDTO> before = getResultDataArray(
                requestGetWithOkAndReturn(GET, DEFAULT_PROJECT_ID, "BUG"), StatusItemDTO.class);
        String snapshot = workflowSnapshot();
        StatusItemUpdateRequest request = new StatusItemUpdateRequest();
        request.setId(before.getFirst().getId());
        request.setName("retired-write-must-not-update");
        requestPost(STATUS_UPDATE, request).andExpect(businessError(LEGACY_CLOSED));
        Assertions.assertEquals(snapshot, workflowSnapshot());
    }

    @Test
    @Order(3)
    public void updateStatusDefinition() throws Exception {
        List<StatusItemDTO> before = getResultDataArray(
                requestGetWithOkAndReturn(GET, DEFAULT_PROJECT_ID, "BUG"), StatusItemDTO.class);
        String snapshot = workflowSnapshot();
        StatusDefinitionUpdateRequest request = new StatusDefinitionUpdateRequest();
        request.setStatusId(before.getFirst().getId());
        request.setDefinitionId(BugStatusDefinitionType.END.name());
        request.setEnable(true);
        requestPost(STATUS_DEFINITION_UPDATE, request).andExpect(businessError(LEGACY_CLOSED));
        Assertions.assertEquals(snapshot, workflowSnapshot());
    }

    @Test
    @Order(4)
    public void updateStatusFlow() throws Exception {
        List<StatusItemDTO> before = getResultDataArray(
                requestGetWithOkAndReturn(GET, DEFAULT_PROJECT_ID, "BUG"), StatusItemDTO.class);
        String snapshot = workflowSnapshot();
        StatusFlowUpdateRequest request = new StatusFlowUpdateRequest();
        request.setFromId(before.getFirst().getId());
        request.setToId(before.getLast().getId());
        request.setEnable(true);
        requestPost(STATUS_FLOW_UPDATE, request).andExpect(businessError(LEGACY_CLOSED));
        Assertions.assertEquals(snapshot, workflowSnapshot());
    }

    @Test
    @Order(5)
    public void deleteStatusItem() throws Exception {
        List<StatusItemDTO> before = getResultDataArray(
                requestGetWithOkAndReturn(GET, DEFAULT_PROJECT_ID, "BUG"), StatusItemDTO.class);
        String snapshot = workflowSnapshot();
        requestGet(STATUS_DELETE, before.getFirst().getId()).andExpect(businessError(LEGACY_CLOSED));
        Assertions.assertEquals(snapshot, workflowSnapshot());
    }

    @Test
    @Order(6)
    public void sortStatusItem() throws Exception {
        List<StatusItemDTO> before = getResultDataArray(
                requestGetWithOkAndReturn(GET, DEFAULT_PROJECT_ID, "BUG"), StatusItemDTO.class);
        String snapshot = workflowSnapshot();
        requestPost(STATUS_SORT, before.reversed().stream().map(StatusItem::getId).toList(),
                DEFAULT_PROJECT_ID, "BUG").andExpect(businessError(LEGACY_CLOSED));
        Assertions.assertEquals(snapshot, workflowSnapshot());
    }

    private void changeOrgTemplateEnable(boolean enable) {
        if (enable) {
            organizationParameterMapper.deleteByPrimaryKey(DEFAULT_ORGANIZATION_ID, OrganizationParameterConstants.ORGANIZATION_BUG_TEMPLATE_ENABLE_KEY);
        } else {
            OrganizationParameter organizationParameter = new OrganizationParameter();
            organizationParameter.setOrganizationId(DEFAULT_ORGANIZATION_ID);
            organizationParameter.setParamKey(OrganizationParameterConstants.ORGANIZATION_BUG_TEMPLATE_ENABLE_KEY);
            organizationParameter.setParamValue(BooleanUtils.toStringTrueFalse(false));
            if (organizationParameterMapper.selectByPrimaryKey(DEFAULT_ORGANIZATION_ID,
                    OrganizationParameterConstants.ORGANIZATION_BUG_TEMPLATE_ENABLE_KEY) == null) {
                organizationParameterMapper.insert(organizationParameter);
            }
        }
    }
}