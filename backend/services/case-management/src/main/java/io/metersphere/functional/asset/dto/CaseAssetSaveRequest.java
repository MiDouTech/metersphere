package io.metersphere.functional.asset.dto;

import io.metersphere.functional.dto.CaseCustomFieldDTO;
import io.metersphere.functional.dto.FunctionalCaseAttachmentDTO;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CaseAssetSaveRequest {
    private String id;
    @NotBlank
    private String catalogId;
    @NotBlank
    private String name;
    private String caseEditType = "STEP";
    private String prerequisite;
    private String steps;
    private String textDescription;
    private String expectedResult;
    private String description;
    private List<String> tags;
    private List<CaseCustomFieldDTO> customFields;
    private List<FunctionalCaseAttachmentDTO> attachments;
}
