package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class ImportFollowupRequest {
    @NotBlank private String policy;
    private String targetGroupId;
    private Long expectedGroupRevision;
    @Size(max=255) private String planName;
    @Size(max=255) private String reviewName;
    @Size(max=100) private List<String> reviewers;
    private String reviewPassRule = "SINGLE";
}
