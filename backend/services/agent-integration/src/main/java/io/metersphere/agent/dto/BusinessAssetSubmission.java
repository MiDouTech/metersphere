package io.metersphere.agent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** Versioned JSON upload contract shared by human editors and external AI executors. */
public record BusinessAssetSubmission(
    @Size(max = 64) String projectId,
    @Pattern(regexp = "1\\.0") @NotNull String schemaVersion,
    @Pattern(regexp = "CREATE|UPDATE") @NotNull String operation,
    @Size(max = 64) String assetId,
    @NotNull @Min(0) Integer baseVersion,
    @NotBlank @Size(max = 128) String idempotencyKey,
    @Valid @NotNull Scope scope,
    @NotBlank @Size(max = 255) String title,
    @Valid @NotNull Document document,
    @NotNull @Size(max = 200) List<@Valid Branch> branches,
    @NotBlank @Size(max = 1000) String changeSummary,
    @Valid Source source) {
  public BusinessAssetSubmission {
    projectId = projectId == null ? "" : projectId;
  }

  public record Scope(
      @NotNull @Size(max = 128) String systemKey,
      @NotNull @Size(max = 128) String moduleKey,
      @NotNull @Size(max = 100) List<@NotBlank @Size(max = 128) String> pageKeys,
      @NotNull @Size(max = 128) String flowKey) {}

  public record Document(
      @Pattern(regexp = "markdown") @NotNull String format,
      @NotBlank @Size(max = 500000) String content) {}

  public record Branch(
      @NotBlank @Size(max = 128) String branchKey,
      @NotBlank @Size(max = 255) String title,
      @Pattern(regexp = "OBSERVED|INFERRED|UNCONFIRMED") @NotNull String understandingStatus,
      @NotNull @Size(max = 100) List<@NotBlank @Size(max = 64) String> evidenceRefs) {}

  public record Source(
      @NotBlank @Size(max = 64) String taskId, @NotBlank @Size(max = 64) String executorId) {}
}
