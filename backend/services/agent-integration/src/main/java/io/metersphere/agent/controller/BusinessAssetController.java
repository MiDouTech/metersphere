package io.metersphere.agent.controller;

import io.metersphere.agent.dto.BusinessAssetSubmission;
import io.metersphere.agent.service.BusinessAssetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** Authorization lives in the application service for both browser and Agent Token callers. */
@RestController
@RequestMapping({"/business-assets", "/api/business-assets"})
public class BusinessAssetController {
  private final BusinessAssetService service;

  public BusinessAssetController(BusinessAssetService service) {
    this.service = service;
  }

  @GetMapping
  public List<Map<String, Object>> list(
      @RequestParam String projectId,
      @RequestParam(defaultValue = "") String keyword,
      @RequestParam(defaultValue = "false") boolean archived) {
    return service.list(projectId, keyword, archived);
  }

  @GetMapping("/legacy-unassigned")
  public List<Map<String, Object>> legacy(@RequestParam String projectId) {
    return service.unassignedLegacy(projectId);
  }

  public record LegacyImport(@NotBlank String projectId) {}

  @PostMapping("/legacy/{id}/import")
  public Map<String, String> legacyImport(
      @PathVariable String id, @Valid @RequestBody LegacyImport r) {
    return service.importUnassignedLegacy(r.projectId(), id);
  }

  @GetMapping("/{id}")
  public Map<String, Object> detail(@RequestParam String projectId, @PathVariable String id) {
    return service.detail(projectId, id);
  }

  @GetMapping("/{id}/versions")
  public List<Map<String, Object>> versions(
      @RequestParam String projectId, @PathVariable String id) {
    return service.versions(projectId, id);
  }

  @PostMapping
  public Map<String, Object> submit(@Valid @RequestBody BusinessAssetSubmission request) {
    return service.submit(request);
  }

  public record Merge(
      @Min(0) int sourceRevision,
      @Valid @jakarta.validation.constraints.NotNull BusinessAssetSubmission target) {}

  @PostMapping("/{id}/merge")
  public Map<String, Object> merge(@PathVariable String id, @Valid @RequestBody Merge request) {
    return service.merge(id, request.sourceRevision(), request.target());
  }

  public record Selection(
      @NotBlank String projectId,
      @NotBlank String versionId,
      @Min(0) int revision,
      @NotBlank @Size(max = 500) String reason) {}

  @PostMapping("/{id}/current-version")
  public void select(@PathVariable String id, @Valid @RequestBody Selection request) {
    service.select(
        request.projectId(), id, request.versionId(), request.revision(), request.reason());
  }

  public record Archive(
      @NotBlank String projectId,
      boolean archived,
      @Min(0) int revision,
      @NotBlank @Size(max = 500) String reason) {}

  @PostMapping("/{id}/archive")
  public void archive(@PathVariable String id, @Valid @RequestBody Archive request) {
    service.archive(
        request.projectId(), id, request.archived(), request.revision(), request.reason());
  }
}
