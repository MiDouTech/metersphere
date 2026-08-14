package io.metersphere.system.controller;

import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.dto.ai.agent.AiAgentBridgePackageDTO;
import io.metersphere.system.dto.ai.agent.AiAgentBridgePackageUploadRequest;
import io.metersphere.system.service.ai.agent.AiAgentBridgePackageService;
import io.metersphere.system.utils.SessionUtils;
import jakarta.validation.Valid;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/ai/agent-bridge")
public class AiAgentBridgePackageController {
    private final AiAgentBridgePackageService service;

    public AiAgentBridgePackageController(AiAgentBridgePackageService service) {
        this.service = service;
    }

    @GetMapping("/packages")
    @RequiresPermissions(PermissionConstants.SYSTEM_AGENT_PACKAGE_READ)
    public List<AiAgentBridgePackageDTO> list() {
        return service.list();
    }

    @PostMapping("/packages")
    @RequiresPermissions(PermissionConstants.SYSTEM_AGENT_PACKAGE_ADD)
    public AiAgentBridgePackageDTO upload(@Valid @RequestPart("request") AiAgentBridgePackageUploadRequest request,
                                          @RequestPart("file") MultipartFile file) {
        return service.upload(request, file, SessionUtils.getUserId());
    }

    @PostMapping("/packages/{id}/activate")
    @RequiresPermissions(PermissionConstants.SYSTEM_AGENT_PACKAGE_UPDATE)
    public AiAgentBridgePackageDTO activate(@PathVariable String id) {
        return service.activate(id, SessionUtils.getUserId());
    }

    @PostMapping("/packages/{id}/deactivate")
    @RequiresPermissions(PermissionConstants.SYSTEM_AGENT_PACKAGE_UPDATE)
    public AiAgentBridgePackageDTO deactivate(@PathVariable String id) {
        return service.deactivate(id, SessionUtils.getUserId());
    }

    @DeleteMapping("/packages/{id}")
    @RequiresPermissions(PermissionConstants.SYSTEM_AGENT_PACKAGE_DELETE)
    public void delete(@PathVariable String id) {
        service.delete(id, SessionUtils.getUserId());
    }

    @GetMapping("/packages/{id}/download")
    @RequiresPermissions(PermissionConstants.SYSTEM_AGENT_PACKAGE_READ)
    public ResponseEntity<StreamingResponseBody> administratorDownload(@PathVariable String id) {
        return download(service.openDownload(id, false));
    }

    @GetMapping("/download")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_READ)
    public ResponseEntity<StreamingResponseBody> downloadActive(
            @RequestParam(defaultValue = "WINDOWS") String osType,
            @RequestParam(defaultValue = "X64") String architecture) {
        AiAgentBridgePackageDTO active = service.active(osType, architecture);
        if (active == null) {
            throw new io.metersphere.sdk.exception.MSException("管理员尚未发布可用的 Agent 安装包");
        }
        return download(service.openDownload(active.getId(), true));
    }

    private ResponseEntity<StreamingResponseBody> download(AiAgentBridgePackageService.Download download) {
        AiAgentBridgePackageDTO metadata = download.metadata();
        service.recordDownload(metadata, SessionUtils.getUserId());
        StreamingResponseBody body = output -> {
            try (var input = download.stream()) {
                input.transferTo(output);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(metadata.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(metadata.getFileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-SHA256", metadata.getSha256())
                .body(body);
    }
}
