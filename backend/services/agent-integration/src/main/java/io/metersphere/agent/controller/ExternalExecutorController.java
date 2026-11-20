package io.metersphere.agent.controller;

import io.metersphere.agent.service.ExternalExecutorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/external-executors", "/api/external-executors"})
public class ExternalExecutorController {
  private final ExternalExecutorService service;

  public ExternalExecutorController(ExternalExecutorService service) {
    this.service = service;
  }

  public record Connect(
      @NotBlank String projectId,
      @NotBlank @Size(max = 64) String executorId,
      @NotBlank @Size(max = 128) String requestKey) {}

  public record Event(
      @NotBlank String projectId,
      @NotBlank @Size(max = 128) String requestKey,
      @NotBlank String type,
      @NotBlank String stage,
      @NotBlank String errorCategory,
      @NotBlank String outcome,
      @Size(max = 2000) String summary,
      @Size(max = 64) String evidenceId) {}

  public record Correction(
      @NotBlank String projectId, @NotBlank @Size(max = 5000) String instruction) {}

  public record Response(
      @NotBlank String projectId, @NotBlank String outcome, @NotBlank String category) {}

  public record Evidence(
      @NotBlank String projectId,
      @NotBlank @Size(max = 128) String requestKey,
      @NotBlank @Size(max = 2800000) String content,
      boolean redacted) {}

  public record ContextUpdate(
      @NotBlank String projectId,
      @NotBlank String assetId,
      @NotBlank String versionId,
      @NotNull String expectedVersionId,
      @NotBlank @Size(max = 500) String reason) {}

  @PostMapping("/{id}/context")
  public void refresh(@PathVariable String id, @Valid @RequestBody ContextUpdate r) {
    service.refreshContext(
        r.projectId(), id, r.assetId(), r.versionId(), r.expectedVersionId(), r.reason());
  }

  @GetMapping("/{id}/context/{asset}")
  public Map<String, Object> contextAsset(
      @PathVariable String id, @PathVariable String asset, @RequestParam String projectId) {
    return service.readContext(projectId, id, asset);
  }

  @PostMapping("/{id}/evidence")
  public Map<String, String> upload(@PathVariable String id, @RequestBody @Valid Evidence r) {
    return service.uploadEvidence(r.projectId(), id, r.requestKey(), r.content(), r.redacted());
  }

  @GetMapping("/evidence/{id}")
  public org.springframework.http.ResponseEntity<Map<String, String>> evidence(
      @PathVariable String id, @RequestParam String projectId) {
    return org.springframework.http.ResponseEntity.ok()
        .cacheControl(org.springframework.http.CacheControl.noStore())
        .body(service.evidence(projectId, id));
  }

  @PostMapping
  public Map<String, Object> connect(@RequestBody @Valid Connect r) {
    return service.connect(r.projectId(), r.executorId(), r.requestKey());
  }

  @GetMapping
  public List<Map<String, Object>> sessions(@RequestParam String projectId) {
    return service.sessions(projectId);
  }

  @GetMapping("/{id}")
  public Map<String, Object> monitor(@RequestParam String projectId, @PathVariable String id) {
    return service.monitor(projectId, id);
  }

  @GetMapping("/{id}/context")
  public List<Map<String, Object>> context(
      @RequestParam String projectId, @PathVariable String id) {
    return service.context(projectId, id);
  }

  @PostMapping("/{id}/events")
  public void event(@PathVariable String id, @RequestBody @Valid Event r) {
    service.event(
        r.projectId(),
        id,
        r.requestKey(),
        r.type(),
        r.stage(),
        r.errorCategory(),
        r.outcome(),
        r.summary(),
        r.evidenceId());
  }

  @GetMapping("/{id}/command-history")
  public Map<String, Object> history(
      @PathVariable String id,
      @RequestParam String projectId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "100") int size) {
    return service.commandHistory(projectId, id, page, size);
  }

  @GetMapping("/{id}/event-history")
  public Map<String, Object> events(
      @PathVariable String id,
      @RequestParam String projectId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "100") int size) {
    return service.eventHistory(projectId, id, page, size);
  }

  @GetMapping("/{id}/commands")
  public List<Map<String, Object>> commands(
      @PathVariable String id, @RequestParam String projectId) {
    return service.commands(projectId, id);
  }

  @PostMapping("/{id}/commands")
  public Map<String, String> correct(@PathVariable String id, @RequestBody @Valid Correction r) {
    return Map.of("commandId", service.correct(r.projectId(), id, r.instruction()));
  }

  @PostMapping("/{id}/commands/{command}/response")
  public void response(
      @PathVariable String id, @PathVariable String command, @RequestBody @Valid Response r) {
    service.acknowledge(r.projectId(), id, command, r.outcome(), r.category());
  }
}
