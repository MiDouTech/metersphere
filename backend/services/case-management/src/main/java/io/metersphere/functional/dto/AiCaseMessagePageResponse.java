package io.metersphere.functional.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiCaseMessagePageResponse {
    private List<AiCaseMessageDTO> records = new ArrayList<>();
    private Long nextBeforeTime;
    private String nextBeforeId;
    private boolean hasMore;
}
