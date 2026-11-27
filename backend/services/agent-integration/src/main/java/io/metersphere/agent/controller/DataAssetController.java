package io.metersphere.agent.controller;

import io.metersphere.agent.service.DataAssetService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/test-data-assets", "/api/test-data-assets"})
public class DataAssetController {
  @GetMapping("/limits")
  public Map<String, Object> limits() {
    return Map.of(
        "maxBytes",
        io.metersphere.agent.service.DataAssetLimits.MAX_BYTES,
        "maxRows",
        io.metersphere.agent.service.DataAssetLimits.MAX_ROWS,
        "formats",
        List.of("CSV", "JSON", "TXT", "XLS", "XLSX"));
  }

  @GetMapping("/categories")
  public List<Map<String, Object>> categories() {
    return service.categories();
  }

  private final io.metersphere.agent.service.DataAssetBatchService batch;
  private final io.metersphere.agent.service.DataAssetLegacyService legacy;
  private final DataAssetService service;
  private final io.metersphere.agent.service.DataAssetParseJobService parseJobs;
  private final io.metersphere.agent.service.DataAssetParser parser;

  public DataAssetController(
      DataAssetService service,
      io.metersphere.agent.service.DataAssetParser parser,
      io.metersphere.agent.service.DataAssetBatchService batch,
      io.metersphere.agent.service.DataAssetLegacyService legacy,
      io.metersphere.agent.service.DataAssetParseJobService parseJobs) {
    this.parseJobs = parseJobs;
    this.legacy = legacy;
    this.service = service;
    this.parser = parser;
    this.batch = batch;
  }

  @PostMapping("/legacy/{id}/import")
  public Map<String, Object> legacy(@PathVariable String id, @RequestParam String projectId) {
    return legacy.importFile(projectId, id);
  }

  @GetMapping("/legacy/inventory")
  public List<Map<String, Object>> inventory(@RequestParam String projectId) {
    return legacy.inventory(projectId);
  }

  @GetMapping("/jobs/{id}")
  public Map<String, Object> job(@PathVariable String id) {
    return parseJobs.exists(id) ? parseJobs.get(id) : batch.get(id);
  }

  @PostMapping("/jobs/{id}/retry")
  public Map<String, Object> retry(@PathVariable String id) {
    return parseJobs.exists(id) ? parseJobs.retry(id) : batch.retry(id);
  }

  @PostMapping("/batches")
  public Map<String, Object> batch(@RequestBody Map<String, List<Map<String, Object>>> request) {
    return batch.submit(request.get("items"));
  }

  @PostMapping("/uploads")
  public Map<String, Object> upload(
      @RequestParam org.springframework.web.multipart.MultipartFile file,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String categoryId,
      @RequestParam(required = false) String requestId)
      throws java.io.IOException {
    String filename = Objects.toString(file.getOriginalFilename(), "");
    if (file.getSize() > io.metersphere.agent.service.DataAssetLimits.MAX_BYTES)
      throw new io.metersphere.sdk.exception.MSException("文件超过2MB限制");
    return parseJobs.upload(file.getBytes(), filename, name, categoryId, requestId);
  }

  @PutMapping("/{id}/uploads")
  public Map<String, Object> replace(
      @PathVariable String id,
      @RequestParam int expectedRevision,
      @RequestParam String requestId,
      @RequestParam org.springframework.web.multipart.MultipartFile file)
      throws java.io.IOException {
    if (file.getSize() > io.metersphere.agent.service.DataAssetLimits.MAX_BYTES)
      throw new io.metersphere.sdk.exception.MSException("文件超过2MB限制");
    return parseJobs.replace(
        null,
        id,
        expectedRevision,
        file.getBytes(),
        Objects.toString(file.getOriginalFilename(), ""),
        requestId);
  }

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(defaultValue = "") String keyword,
      @RequestParam(defaultValue = "") String categoryId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return service.list(keyword, categoryId, page, pageSize);
  }

  @GetMapping("/{id}")
  public Map<String, Object> detail(@PathVariable String id) {
    return service.detail(id);
  }

  @GetMapping("/{id}/content")
  public Map<String, Object> content(
      @PathVariable String id,
      @RequestParam(required = false) String versionId,
      @RequestParam(required = false) Integer page,
      @RequestParam(defaultValue = "100") int pageSize,
      @RequestParam(defaultValue = "0") int sheet) {
    return page == null
        ? service.content(id, versionId)
        : service.preview(id, versionId, page, pageSize, sheet);
  }

  @GetMapping("/{id}/file")
  public org.springframework.http.ResponseEntity<byte[]> file(
      @PathVariable String id, @RequestParam(required = false) String versionId) {
    return org.springframework.http.ResponseEntity.ok()
        .header(
            "Content-Disposition",
            org.springframework.http.ContentDisposition.attachment()
                .filename(service.fileName(id, versionId), java.nio.charset.StandardCharsets.UTF_8)
                .build()
                .toString())
        .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
        .body(service.file(id, versionId));
  }

  @GetMapping("/{id}/versions")
  public List<Map<String, Object>> versions(@PathVariable String id) {
    return service.versions(id);
  }

  @PostMapping
  public Map<String, Object> create(@RequestBody Map<String, Object> request) {
    return service.save(null, request);
  }

  @PutMapping("/{id}")
  public Map<String, Object> update(
      @PathVariable String id, @RequestBody Map<String, Object> request) {
    return service.save(id, request);
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable String id, @RequestParam int expectedRevision) {
    service.delete(id, expectedRevision);
  }
}
