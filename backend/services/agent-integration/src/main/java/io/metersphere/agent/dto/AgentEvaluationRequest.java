package io.metersphere.agent.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AgentEvaluationRequest {
    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    private BigDecimal score;
    @Size(max = 2000)
    private String comment;
}
