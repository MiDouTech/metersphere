package io.metersphere.functional.edit;

import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.domain.FunctionalCaseBlob;
import io.metersphere.functional.domain.FunctionalCaseCustomField;
import io.metersphere.functional.dto.CaseCustomFieldDTO;
import io.metersphere.functional.mapper.FunctionalCaseBlobMapper;
import io.metersphere.functional.mapper.FunctionalCaseMapper;
import io.metersphere.functional.request.FunctionalCaseEditRequest;
import io.metersphere.functional.service.FunctionalCaseCustomFieldService;
import io.metersphere.functional.service.FunctionalCaseService;
import io.metersphere.sdk.constants.ResourceEditConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.edit.ResourceEditAdapter;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 功能用例整单正文快照（不含附件）
 */
@Component
public class FunctionalCaseResourceEditAdapter implements ResourceEditAdapter {

    @Resource
    private FunctionalCaseMapper functionalCaseMapper;
    @Resource
    private FunctionalCaseBlobMapper functionalCaseBlobMapper;
    @Resource
    private FunctionalCaseCustomFieldService functionalCaseCustomFieldService;
    @Lazy
    @Resource
    private FunctionalCaseService functionalCaseService;

    @Override
    public String resourceType() {
        return ResourceEditConstants.TYPE_FUNCTIONAL_CASE;
    }

    @Override
    public String loadPayload(String resourceId) {
        FunctionalCase functionalCase = functionalCaseMapper.selectByPrimaryKey(resourceId);
        if (functionalCase == null || Boolean.TRUE.equals(functionalCase.getDeleted())) {
            throw new MSException("case_not_exist");
        }
        FunctionalCaseEditRequest request = toEditRequest(functionalCase);
        return JSON.toJSONString(request);
    }

    @Override
    public void applyPayload(String resourceId, String payloadJson, String operator) {
        FunctionalCaseEditRequest request = JSON.parseObject(payloadJson, FunctionalCaseEditRequest.class);
        if (request == null || StringUtils.isBlank(request.getId())) {
            throw new MSException("invalid snapshot payload");
        }
        request.setId(resourceId);
        // 附件相关字段不回放
        request.setDeleteFileMetaIds(null);
        request.setUnLinkFilesIds(null);
        request.setRelateFileMetaIds(null);
        request.setCaseDetailFileIds(null);
        request.setAttachments(null);
        functionalCaseService.updateFunctionalCase(request, Collections.emptyList(), operator);
    }

    private FunctionalCaseEditRequest toEditRequest(FunctionalCase functionalCase) {
        FunctionalCaseEditRequest request = new FunctionalCaseEditRequest();
        request.setId(functionalCase.getId());
        request.setProjectId(functionalCase.getProjectId());
        request.setTemplateId(functionalCase.getTemplateId());
        request.setName(functionalCase.getName());
        request.setCaseEditType(functionalCase.getCaseEditType());
        request.setModuleId(functionalCase.getModuleId());
        request.setVersionId(functionalCase.getVersionId());
        request.setTags(functionalCase.getTags());
        request.setLastExecuteResult(functionalCase.getLastExecuteResult());
        request.setPublicCase(Boolean.TRUE.equals(functionalCase.getPublicCase()) ? "true" : "false");

        FunctionalCaseBlob blob = functionalCaseBlobMapper.selectByPrimaryKey(functionalCase.getId());
        if (blob != null) {
            request.setSteps(bytesToStr(blob.getSteps()));
            request.setTextDescription(bytesToStr(blob.getTextDescription()));
            request.setExpectedResult(bytesToStr(blob.getExpectedResult()));
            request.setPrerequisite(bytesToStr(blob.getPrerequisite()));
            request.setDescription(bytesToStr(blob.getDescription()));
        }

        Map<String, List<FunctionalCaseCustomField>> fieldMap =
                functionalCaseCustomFieldService.getCustomFieldMapByCaseIds(List.of(functionalCase.getId()));
        List<FunctionalCaseCustomField> fields = fieldMap.get(functionalCase.getId());
        if (CollectionUtils.isNotEmpty(fields)) {
            List<CaseCustomFieldDTO> dtos = new ArrayList<>();
            for (FunctionalCaseCustomField field : fields) {
                CaseCustomFieldDTO dto = new CaseCustomFieldDTO();
                dto.setFieldId(field.getFieldId());
                dto.setValue(field.getValue());
                dtos.add(dto);
            }
            request.setCustomFields(dtos);
        }
        return request;
    }

    private String bytesToStr(byte[] data) {
        return data == null ? "" : new String(data, StandardCharsets.UTF_8);
    }
}
