package io.metersphere.agent.controller;

import io.metersphere.agent.dto.TestAssetVersionDTO;
import io.metersphere.agent.service.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Publish an immutable global data version for use by a particular execution project. */
@RestController
@RequestMapping({"/test-data-assets", "/api/test-data-assets"})
public class DataAssetPublicationController {
  private final DataAssetService data;
  private final GlobalAssetAccess global;
  private final AgentBusinessAccess access;
  private final TestAssetVersionService versions;

  public DataAssetPublicationController(
      DataAssetService data,
      GlobalAssetAccess global,
      AgentBusinessAccess access,
      TestAssetVersionService versions) {
    this.data = data;
    this.global = global;
    this.access = access;
    this.versions = versions;
  }

  @PostMapping("/{id}/publish")
  @Transactional(rollbackFor = Exception.class)
  public TestAssetVersionDTO publish(
      @PathVariable String id,
      @RequestParam String projectId,
      @RequestParam(required = false) String versionId) {
    var scope = global.authorize(projectId, "TEST_DATA_ASSET:READ");
    if (!access.userHasPermission(scope.actorId(), projectId, "TEST_ASSET_VERSION:MANAGE"))
      throw AgentBusinessAccess.denied();
    var resolved = data.resolveForProject(projectId, id, versionId);
    var snapshot = new java.util.LinkedHashMap<String, Object>();
    snapshot.put("format", resolved.get("format"));
    snapshot.put("content", resolved.get("content"));
    snapshot.put("dataVersionId", resolved.get("versionId"));
    return versions.publish(
        projectId,
        "DATASET",
        id,
        resolved.get("versionId").toString(),
        io.metersphere.sdk.util.JSON.toJSONString(snapshot),
        scope.actorId());
  }
}
