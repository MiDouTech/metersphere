package io.metersphere.agent.controller;

import io.metersphere.agent.dto.SharedConfigurationSave;
import io.metersphere.agent.service.SharedConfigurationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/shared-configurations", "/api/shared-configurations"})
public class SharedConfigurationController {
  private final SharedConfigurationService service;

  public SharedConfigurationController(SharedConfigurationService service) {
    this.service = service;
  }

  public record Batch(
      @NotNull @Size(min = 1, max = 100) List<@Valid SharedConfigurationSave> entries) {}

  @PostMapping("/batch")
  public List<Map<String, Object>> batch(@RequestBody @Valid Batch r) {
    return service.batch(r.entries());
  }

  public record Select(
      @NotBlank String projectId,
      @Min(1) int version,
      @Min(1) int expectedVersion,
      @Size(max = 500) String reason) {}

  public record Retry(@NotBlank String projectId, @Min(1) int version) {}

  public record Resolve(@NotBlank String projectId, @NotBlank String sessionId) {}

  @PostMapping("/{id}/resolve")
  public org.springframework.http.ResponseEntity<Map<String, Object>> resolve(
      @PathVariable String id, @Valid @RequestBody Resolve r) {
    return org.springframework.http.ResponseEntity.ok()
        .cacheControl(org.springframework.http.CacheControl.noStore())
        .body(service.resolveForExecutor(r.projectId(), r.sessionId(), id));
  }

  @GetMapping
  public List<Map<String, Object>> list(@RequestParam String projectId) {
    return service.list(projectId);
  }

  @PostMapping
  public Map<String, Object> save(@RequestBody @Valid SharedConfigurationSave r) {
    return service.saveStructured(
        r.projectId(),
        r.id(),
        r.systemKey(),
        r.environmentKey(),
        r.name(),
        r.kind(),
        r.value(),
        r.fileName(),
        r.expectedVersion(),
        r.secretType(),
        r.keepSecret());
  }

  @GetMapping("/{id}/versions")
  public List<Map<String, Object>> versions(
      @PathVariable String id, @RequestParam String projectId) {
    return service.versions(projectId, id);
  }

  @GetMapping("/{id}/versions/{version}")
  public org.springframework.http.ResponseEntity<Map<String, Object>> read(
      @PathVariable String id,
      @PathVariable int version,
      @RequestParam String projectId,
      @RequestParam(defaultValue = "false") boolean document) {
    return org.springframework.http.ResponseEntity.ok()
        .cacheControl(org.springframework.http.CacheControl.noStore())
        .body(service.read(projectId, id, version, document));
  }

  @PostMapping("/{id}/current-version")
  public void select(@PathVariable String id, @Valid @RequestBody Select r) {
    service.select(r.projectId(), id, r.version(), r.expectedVersion(), r.reason());
  }

  @PostMapping("/{id}/retry-parse")
  public Map<String, String> retry(@PathVariable String id, @Valid @RequestBody Retry r) {
    return Map.of("parseStatus", service.retryParse(r.projectId(), id, r.version()));
  }
}
