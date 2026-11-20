package io.metersphere.agent.dto;

import jakarta.validation.constraints.*;

public record SharedConfigurationSave(
    @NotBlank String projectId,
    @Size(max = 64) String id,
    @NotBlank @Size(max = 128) String systemKey,
    @NotBlank @Size(max = 128) String environmentKey,
    @NotBlank @Size(max = 128) String name,
    @NotBlank String kind,
    @NotBlank @Size(max = 2800000) String value,
    @Size(max = 255) String fileName,
    @Min(0) int expectedVersion) {}
