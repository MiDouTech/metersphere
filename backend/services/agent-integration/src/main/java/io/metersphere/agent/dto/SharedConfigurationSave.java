package io.metersphere.agent.dto;

import jakarta.validation.constraints.*;

public record SharedConfigurationSave(
    @NotBlank String projectId,
    @Size(max = 64) String id,
    @NotBlank @Size(max = 128) String systemKey,
    @NotBlank @Size(max = 128) String environmentKey,
    @NotBlank @Size(max = 128) String name,
    @NotBlank String kind,
    @Size(max = 2800000) String value,
    @Size(max = 255) String fileName,
    @Min(0) int expectedVersion,
    @Size(max = 24) String secretType,
    boolean keepSecret) {
  @Override
  public String toString() {
    return "SharedConfigurationSave[redacted]";
  }

  public SharedConfigurationSave(
      String projectId,
      String id,
      String systemKey,
      String environmentKey,
      String name,
      String kind,
      String value,
      String fileName,
      int expectedVersion) {
    this(
        projectId,
        id,
        systemKey,
        environmentKey,
        name,
        kind,
        value,
        fileName,
        expectedVersion,
        null,
        false);
  }
}
