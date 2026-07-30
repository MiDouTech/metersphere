package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentMcpManifestDTO;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import jakarta.annotation.PostConstruct;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class AgentMcpBundleService {
    private static final String MANIFEST_PATH = "mcp/manifest.json";
    private static final String ZIP_PREFIX = "mcp/";

    private AgentMcpManifestDTO manifest;
    private byte[] zipBytes;

    @PostConstruct
    public void init() {
        try {
            ClassPathResource manifestRes = new ClassPathResource(MANIFEST_PATH);
            if (!manifestRes.exists()) {
                AgentMcpManifestDTO empty = new AgentMcpManifestDTO();
                empty.setAvailable(false);
                empty.setDescription("MCP bundle not packaged; run scripts/pack-metersphere-mcp.ps1");
                this.manifest = empty;
                return;
            }
            String json = IOUtils.toString(manifestRes.getInputStream(), StandardCharsets.UTF_8);
            if (json.startsWith("\uFEFF")) {
                json = json.substring(1);
            }
            AgentMcpManifestDTO dto = JSON.parseObject(json, AgentMcpManifestDTO.class);
            if (dto == null) {
                dto = new AgentMcpManifestDTO();
                dto.setAvailable(false);
                this.manifest = dto;
                return;
            }
            if (StringUtils.isBlank(dto.getFileName())) {
                dto.setAvailable(false);
                this.manifest = dto;
                return;
            }
            ClassPathResource zipRes = new ClassPathResource(ZIP_PREFIX + dto.getFileName());
            if (!zipRes.exists()) {
                dto.setAvailable(false);
                this.manifest = dto;
                return;
            }
            try (InputStream in = zipRes.getInputStream()) {
                this.zipBytes = IOUtils.toByteArray(in);
            }
            dto.setAvailable(true);
            this.manifest = dto;
        } catch (Exception e) {
            AgentMcpManifestDTO empty = new AgentMcpManifestDTO();
            empty.setAvailable(false);
            empty.setDescription("Failed to load MCP bundle: " + e.getMessage());
            this.manifest = empty;
        }
    }

    public AgentMcpManifestDTO getManifest() {
        return manifest;
    }

    public ResponseEntity<byte[]> download() {
        if (manifest == null || !manifest.isAvailable() || zipBytes == null) {
            throw new MSException("MCP 包不可用，请联系管理员重新打包部署");
        }
        String fileName = StringUtils.defaultIfBlank(manifest.getFileName(), "metersphere-mcp.zip");
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);
        headers.setContentLength(zipBytes.length);
        return ResponseEntity.ok().headers(headers).body(zipBytes);
    }
}
