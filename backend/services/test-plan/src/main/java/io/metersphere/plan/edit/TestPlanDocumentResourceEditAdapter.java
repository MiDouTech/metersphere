package io.metersphere.plan.edit;

import io.metersphere.plan.domain.TestPlan;
import io.metersphere.plan.domain.TestPlanDocument;
import io.metersphere.plan.dto.request.TestPlanDocumentSaveRequest;
import io.metersphere.plan.mapper.TestPlanDocumentMapper;
import io.metersphere.plan.mapper.TestPlanMapper;
import io.metersphere.plan.service.TestPlanDocumentService;
import io.metersphere.sdk.constants.ResourceEditConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.edit.ResourceEditAdapter;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 测试计划文档快照（resourceId = testPlanId）
 */
@Component
public class TestPlanDocumentResourceEditAdapter implements ResourceEditAdapter {

    private static final String DEFAULT_CONTENT_TYPE = "RICH_TEXT";

    @Resource
    private TestPlanMapper testPlanMapper;
    @Resource
    private TestPlanDocumentMapper testPlanDocumentMapper;
    @Lazy
    @Resource
    private TestPlanDocumentService testPlanDocumentService;

    @Override
    public String resourceType() {
        return ResourceEditConstants.TYPE_TEST_PLAN_DOCUMENT;
    }

    @Override
    public String loadPayload(String resourceId) {
        TestPlan plan = testPlanMapper.selectByPrimaryKey(resourceId);
        if (plan == null) {
            throw new MSException("test_plan_not_exist");
        }
        TestPlanDocument document = testPlanDocumentMapper.selectByTestPlanId(resourceId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("testPlanId", resourceId);
        payload.put("projectId", plan.getProjectId());
        if (document == null) {
            payload.put("content", "");
            payload.put("contentType", DEFAULT_CONTENT_TYPE);
        } else {
            payload.put("content", StringUtils.defaultString(document.getContent()));
            payload.put("contentType", StringUtils.defaultIfBlank(document.getContentType(), DEFAULT_CONTENT_TYPE));
        }
        return JSON.toJSONString(payload);
    }

    @Override
    public void applyPayload(String resourceId, String payloadJson, String operator) {
        Map<String, Object> map = JSON.parseMap(payloadJson);
        if (map == null) {
            throw new MSException("invalid snapshot payload");
        }
        TestPlanDocumentSaveRequest request = new TestPlanDocumentSaveRequest();
        Object content = map.get("content");
        Object contentType = map.get("contentType");
        request.setContent(content == null ? "" : String.valueOf(content));
        request.setContentType(contentType == null ? DEFAULT_CONTENT_TYPE : String.valueOf(contentType));
        testPlanDocumentService.saveDocument(resourceId, request);
    }
}
