package io.metersphere.bug.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BugTransitionBatchExecuteRequest {
    @NotEmpty
    @Size(max = 100)
    private List<@Valid Item> items;

    @Data
    public static class Item extends BugTransitionRequest {
        @NotBlank
        private String bugId;
    }
}
