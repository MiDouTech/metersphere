package io.metersphere.functional.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiCaseConversationPageResponse {
    private long total;
    private List<AiCaseConversationDTO> records = new ArrayList<>();
}
