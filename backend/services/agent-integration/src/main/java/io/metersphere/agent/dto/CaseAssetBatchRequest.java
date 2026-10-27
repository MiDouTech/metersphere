package io.metersphere.agent.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;
@Data
public class CaseAssetBatchRequest {
 @NotBlank @Size(max=50) private String catalogId;
 @NotBlank @Size(max=64) private String requestId;
 @NotBlank private String action;
 @NotEmpty @Size(max=100) private List<@NotBlank String> ids;
 private String categoryId;
 private String priority;
 private String tagMode;
 @Size(max=100) private List<@NotBlank @Size(max=100) String> tags;
 private String sourceEditMode;
}
