package io.metersphere.system.service.ai.agent;

import io.metersphere.sdk.constants.StorageType;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.file.FileRequest;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.dto.ai.agent.AiAgentBridgePackageDTO;
import io.metersphere.system.dto.ai.agent.AiAgentBridgePackageUploadRequest;
import io.metersphere.system.service.FileService;
import io.metersphere.system.service.ai.AiAuditService;
import io.metersphere.system.uid.IDGenerator;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

@Service
public class AiAgentBridgePackageService {
    private static final long MAX_PACKAGE_SIZE = 500L * 1024L * 1024L;
    private static final String STORAGE_ROOT = "system/agent-bridge/packages";
    @org.springframework.beans.factory.annotation.Value("${ms.ai.agent-package.storage:MINIO}")
    private String storage = StorageType.MINIO.name();

    private final AiAgentBridgePackageRepository repository;
    private final FileService fileService;
    private final AiAuditService auditService;

    public AiAgentBridgePackageService(AiAgentBridgePackageRepository repository, FileService fileService,
                                       AiAuditService auditService) {
        this.repository = repository;
        this.fileService = fileService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<AiAgentBridgePackageDTO> list() {
        return repository.list();
    }

    @Transactional(readOnly = true)
    public AiAgentBridgePackageDTO active(String osType, String architecture) {
        return repository.findActive(normalizeOs(osType), normalizeArchitecture(architecture));
    }

    @Transactional(rollbackFor = Exception.class)
    public AiAgentBridgePackageDTO upload(AiAgentBridgePackageUploadRequest request, MultipartFile file, String userId) {
        validateFile(request, file);
        String id = IDGenerator.nextStr();
        String fileName = safeFileName(file.getOriginalFilename());
        String osType = normalizeOs(request.getOsType());
        String architecture = normalizeArchitecture(request.getArchitecture());
        String folder = STORAGE_ROOT + "/" + id;
        String sha256 = sha256(file);
        long now = System.currentTimeMillis();

        FileRequest fileRequest = new FileRequest(folder, storage, fileName);
        try {
            fileService.upload(file, fileRequest);
            AiAgentBridgePackageDTO value = new AiAgentBridgePackageDTO();
            value.setId(id);
            value.setVersion(StringUtils.trim(request.getVersion()));
            value.setOsType(osType);
            value.setArchitecture(architecture);
            value.setFileName(fileName);
            value.setStorage(storage);
            value.setStorageFolder(folder);
            value.setSha256(sha256);
            value.setSizeBytes(file.getSize());
            value.setStatus(Boolean.TRUE.equals(request.getActivate()) ? "ACTIVE" : "INACTIVE");
            value.setDescription(StringUtils.abbreviate(StringUtils.trimToNull(request.getDescription()), 1000));
            value.setCreateUser(userId);
            value.setCreateTime(now);
            value.setUpdateUser(userId);
            value.setUpdateTime(now);
            if ("ACTIVE".equals(value.getStatus())) {
                repository.deactivatePlatform(osType, architecture, userId, now);
            }
            repository.insert(value);
            audit("AGENT_PACKAGE_UPLOAD", value, userId);
            return value;
        } catch (DuplicateKeyException ex) {
            deleteStoredFile(fileRequest);
            throw new MSException("同版本、操作系统和架构的安装包已存在");
        } catch (Exception ex) {
            deleteStoredFile(fileRequest);
            if (ex instanceof MSException msException) {
                throw msException;
            }
            throw new MSException("Agent 安装包上传失败", ex);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public AiAgentBridgePackageDTO activate(String id, String userId) {
        AiAgentBridgePackageDTO value = require(id);
        long now = System.currentTimeMillis();
        repository.deactivatePlatform(value.getOsType(), value.getArchitecture(), userId, now);
        if (repository.updateStatus(id, "ACTIVE", userId, now) != 1) {
            throw new MSException("Agent 安装包状态更新失败");
        }
        value.setStatus("ACTIVE");
        value.setUpdateUser(userId);
        value.setUpdateTime(now);
        audit("AGENT_PACKAGE_ACTIVATE", value, userId);
        return value;
    }

    @Transactional(rollbackFor = Exception.class)
    public AiAgentBridgePackageDTO deactivate(String id, String userId) {
        AiAgentBridgePackageDTO value = require(id);
        long now = System.currentTimeMillis();
        if (repository.updateStatus(id, "INACTIVE", userId, now) != 1) {
            throw new MSException("Agent 安装包状态更新失败");
        }
        value.setStatus("INACTIVE");
        value.setUpdateUser(userId);
        value.setUpdateTime(now);
        audit("AGENT_PACKAGE_DEACTIVATE", value, userId);
        return value;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String id, String userId) {
        AiAgentBridgePackageDTO value = require(id);
        if ("ACTIVE".equals(value.getStatus())) {
            throw new MSException("启用中的安装包不能删除，请先停用");
        }
        if (repository.delete(id) != 1) {
            throw new MSException("Agent 安装包删除冲突");
        }
        try {
            fileService.deleteFile(fileRequest(value));
        } catch (Exception ex) {
            throw new MSException("Agent 安装包存储文件删除失败", ex);
        }
        audit("AGENT_PACKAGE_DELETE", value, userId);
    }

    @Transactional(readOnly = true)
    public Download openDownload(String id, boolean requireActive) {
        AiAgentBridgePackageDTO value = require(id);
        if (requireActive && !"ACTIVE".equals(value.getStatus())) {
            throw new MSException("Agent 安装包未启用");
        }
        try {
            return new Download(value, fileService.getFileAsStream(fileRequest(value)));
        } catch (Exception ex) {
            throw new MSException("Agent 安装包文件不存在或无法读取", ex);
        }
    }

    public void recordDownload(AiAgentBridgePackageDTO value, String userId) {
        repository.incrementDownloadCount(value.getId());
        audit("AGENT_PACKAGE_DOWNLOAD", value, userId);
    }

    private void validateFile(AiAgentBridgePackageUploadRequest request, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new MSException("请选择 Agent ZIP 安装包");
        }
        if (file.getSize() > MAX_PACKAGE_SIZE) {
            throw new MSException("Agent 安装包不能超过 500 MB");
        }
        String fileName = safeFileName(file.getOriginalFilename());
        if (!StringUtils.endsWithIgnoreCase(fileName, ".zip")) {
            throw new MSException("Agent 安装包必须为 ZIP 文件");
        }
        boolean hasInstaller = false;
        boolean hasMain = false;
        PackageManifest manifest = null;
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            int entries = 0;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 100_000) {
                    throw new MSException("Agent 安装包文件数量异常");
                }
                String name = StringUtils.replaceChars(entry.getName(), '\\', '/');
                if (StringUtils.equalsIgnoreCase(name, "Install-MeterSphere-Agent.cmd")) {
                    hasInstaller = true;
                }
                if (StringUtils.equalsIgnoreCase(name, "src/main.mjs")) {
                    hasMain = true;
                }
                if (StringUtils.equalsIgnoreCase(name, "agent-manifest.json")) {
                    ByteArrayOutputStream content = new ByteArrayOutputStream();
                    zip.transferTo(content);
                    if (content.size() > 16_384) {
                        throw new MSException("Agent 安装包 manifest 过大");
                    }
                    try {
                        manifest = JSON.parseObject(content.toString(StandardCharsets.UTF_8), PackageManifest.class);
                    } catch (RuntimeException error) {
                        throw new MSException("Agent 安装包 manifest 格式不正确");
                    }
                }
            }
        } catch (IOException ex) {
            throw new MSException("无法读取 Agent ZIP 安装包", ex);
        }
        if (!hasInstaller || !hasMain || manifest == null) {
            throw new MSException("ZIP 不是有效的 MeterSphere Agent 安装包");
        }
        if (!StringUtils.equals(request.getVersion(), manifest.version)
                || !StringUtils.equalsIgnoreCase(request.getOsType(), manifest.osType)
                || !StringUtils.equalsIgnoreCase(request.getArchitecture(), manifest.architecture)) {
            throw new MSException("上传参数与 Agent 安装包 manifest 不一致");
        }
    }

    private String sha256(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return DigestUtils.sha256Hex(input);
        } catch (IOException ex) {
            throw new MSException("计算 Agent 安装包 SHA-256 失败", ex);
        }
    }

    private AiAgentBridgePackageDTO require(String id) {
        AiAgentBridgePackageDTO value = repository.findById(id);
        if (value == null) {
            throw new MSException("Agent 安装包不存在");
        }
        return value;
    }

    private FileRequest fileRequest(AiAgentBridgePackageDTO value) {
        return new FileRequest(value.getStorageFolder(), value.getStorage(), value.getFileName());
    }

    private void deleteStoredFile(FileRequest request) {
        try {
            fileService.deleteFile(request);
        } catch (Exception ignored) {
            // The database remains authoritative; orphan cleanup can retry storage deletion.
        }
    }

    private String normalizeOs(String value) {
        return StringUtils.upperCase(StringUtils.defaultIfBlank(value, "WINDOWS"), Locale.ROOT);
    }

    private String normalizeArchitecture(String value) {
        return StringUtils.upperCase(StringUtils.defaultIfBlank(value, "X64"), Locale.ROOT);
    }

    private String safeFileName(String value) {
        String fileName = StringUtils.trimToEmpty(value);
        if (StringUtils.isBlank(fileName) || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new MSException("Agent 安装包文件名不合法");
        }
        return StringUtils.abbreviate(fileName, 255);
    }

    private void audit(String action, AiAgentBridgePackageDTO value, String userId) {
        auditService.record(null, null, userId, value.getId(), "AGENT_PACKAGE", action,
                "/ai/agent-bridge/packages", "POST", Map.of(
                        "version", value.getVersion(), "osType", value.getOsType(),
                        "architecture", value.getArchitecture(), "sha256", value.getSha256()));
    }

    public record Download(AiAgentBridgePackageDTO metadata, InputStream stream) {
    }

    public static class PackageManifest {
        public String version;
        public String osType;
        public String architecture;
    }
}
