package io.metersphere.bug.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BugTransitionBatchRequest {
    @NotEmpty
    @Size(max = 200)
    private List<String> bugIds;
}
