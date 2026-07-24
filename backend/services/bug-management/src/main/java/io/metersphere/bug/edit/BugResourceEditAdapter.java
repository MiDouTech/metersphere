package io.metersphere.bug.edit;

import io.metersphere.bug.domain.Bug;
import io.metersphere.bug.domain.BugContent;
import io.metersphere.bug.dto.request.BugEditRequest;
import io.metersphere.bug.dto.response.BugCustomFieldDTO;
import io.metersphere.bug.enums.BugTemplateCustomField;
import io.metersphere.bug.mapper.BugContentMapper;
import io.metersphere.bug.mapper.BugMapper;
import io.metersphere.bug.mapper.ExtBugCustomFieldMapper;
import io.metersphere.bug.service.BugService;
import io.metersphere.sdk.constants.CustomFieldType;
import io.metersphere.sdk.constants.ResourceEditConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.edit.ResourceEditAdapter;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 缺陷整单正文快照（不含附件）
 */
@Component
public class BugResourceEditAdapter implements ResourceEditAdapter {

    @Resource
    private BugMapper bugMapper;
    @Resource
    private BugContentMapper bugContentMapper;
    @Resource
    private ExtBugCustomFieldMapper extBugCustomFieldMapper;
    @Lazy
    @Resource
    private BugService bugService;

    @Override
    public String resourceType() {
        return ResourceEditConstants.TYPE_BUG;
    }

    @Override
    public String loadPayload(String resourceId) {
        Bug bug = bugMapper.selectByPrimaryKey(resourceId);
        if (bug == null || Boolean.TRUE.equals(bug.getDeleted())) {
            throw new MSException("bug_not_exist");
        }
        return JSON.toJSONString(toEditRequest(bug));
    }

    @Override
    public void applyPayload(String resourceId, String payloadJson, String operator) {
        BugEditRequest request = JSON.parseObject(payloadJson, BugEditRequest.class);
        if (request == null || StringUtils.isBlank(request.getId())) {
            throw new MSException("invalid snapshot payload");
        }
        request.setId(resourceId);
        // 附件 / 关联用例不回放
        request.setDeleteLocalFileIds(null);
        request.setUnLinkRefIds(null);
        request.setLinkFileIds(null);
        request.setCopyFiles(null);
        request.setRichTextTmpFileIds(null);
        request.setCaseId(null);
        request.setCaseType(null);
        request.setTestPlanId(null);
        request.setTestPlanCaseId(null);
        String orgId = SessionUtils.getCurrentOrganizationId();
        bugService.addOrUpdate(request, Collections.emptyList(), operator, orgId, true);
    }

    private BugEditRequest toEditRequest(Bug bug) {
        BugEditRequest request = new BugEditRequest();
        request.setId(bug.getId());
        request.setProjectId(bug.getProjectId());
        request.setTemplateId(bug.getTemplateId());
        request.setTitle(bug.getTitle());
        request.setTags(bug.getTags());

        BugContent content = bugContentMapper.selectByPrimaryKey(bug.getId());
        if (content != null) {
            request.setDescription(content.getDescription());
        }

        List<BugCustomFieldDTO> fields = new ArrayList<>();
        List<BugCustomFieldDTO> dbFields = extBugCustomFieldMapper.getBugAllCustomFields(List.of(bug.getId()), bug.getProjectId());
        if (CollectionUtils.isNotEmpty(dbFields)) {
            fields.addAll(dbFields);
        }

        BugCustomFieldDTO status = new BugCustomFieldDTO();
        status.setId(BugTemplateCustomField.STATUS.getId());
        status.setValue(bug.getStatus());
        fields.add(status);

        BugCustomFieldDTO handleUser = new BugCustomFieldDTO();
        handleUser.setId(BugTemplateCustomField.HANDLE_USER.getId());
        handleUser.setType(CustomFieldType.MULTIPLE_MEMBER.name());
        handleUser.setValue(toHandleUserJson(bug.getHandleUser()));
        fields.add(handleUser);

        request.setCustomFields(fields);
        return request;
    }

    private String toHandleUserJson(String raw) {
        if (StringUtils.isBlank(raw)) {
            return "[]";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("[")) {
            return trimmed;
        }
        if (trimmed.contains(",")) {
            List<String> ids = new ArrayList<>();
            for (String part : trimmed.split(",")) {
                if (StringUtils.isNotBlank(part)) {
                    ids.add(part.trim());
                }
            }
            return JSON.toJSONString(ids);
        }
        return JSON.toJSONString(List.of(trimmed));
    }
}
