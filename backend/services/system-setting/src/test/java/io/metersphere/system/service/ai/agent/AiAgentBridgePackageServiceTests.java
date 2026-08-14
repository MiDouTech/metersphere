package io.metersphere.system.service.ai.agent;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.dto.ai.agent.AiAgentBridgePackageDTO;
import io.metersphere.system.dto.ai.agent.AiAgentBridgePackageUploadRequest;
import io.metersphere.system.service.FileService;
import io.metersphere.system.service.ai.AiAuditService;
import io.metersphere.system.uid.IDGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAgentBridgePackageServiceTests {
    @Mock private AiAgentBridgePackageRepository repository;
    @Mock private FileService fileService;
    @Mock private AiAuditService auditService;
    private AiAgentBridgePackageService service;

    @BeforeEach
    void setup() {
        service = new AiAgentBridgePackageService(repository, fileService, auditService);
    }

    @Test
    void rejectsArbitraryZipBeforeStorageWrite() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "other.zip", "application/zip",
                zip("readme.txt", "not an agent"));
        assertThrows(MSException.class, () -> service.upload(request(), file, "admin"));
        verify(fileService, never()).upload(any(MockMultipartFile.class), any());
    }

    @Test
    void rejectsManifestThatDoesNotMatchUploadMetadata() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "agent.zip", "application/zip",
                zipEntries(Map.of(
                        "Install-MeterSphere-Agent.cmd", "install",
                        "src/main.mjs", "main",
                        "agent-manifest.json", "{\"version\":\"9.9.9\",\"osType\":\"WINDOWS\",\"architecture\":\"X64\"}")));
        assertThrows(MSException.class, () -> service.upload(request(), file, "admin"));
        verify(fileService, never()).upload(any(MockMultipartFile.class), any());
    }

    @Test
    void acceptsOfficialPackageAndPersistsVerifiedMetadata() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "metersphere-agent-windows-x64.zip", "application/zip",
                zipEntries(Map.of(
                        "Install-MeterSphere-Agent.cmd", "install",
                        "src/main.mjs", "main",
                        "agent-manifest.json", "{\"version\":\"0.1.0\",\"osType\":\"WINDOWS\",\"architecture\":\"X64\"}")));

        AiAgentBridgePackageDTO result;
        try (MockedStatic<IDGenerator> ids = Mockito.mockStatic(IDGenerator.class)) {
            ids.when(IDGenerator::nextStr).thenReturn("package-1");
            result = service.upload(request(), file, "admin");
        }

        assertEquals("0.1.0", result.getVersion());
        assertEquals("ACTIVE", result.getStatus());
        assertEquals(file.getSize(), result.getSizeBytes());
        verify(fileService).upload(eq(file), any());
        verify(repository).insert(result);
    }

    @Test
    void activatingPackageDeactivatesPreviousPlatformSlot() {
        AiAgentBridgePackageDTO value = new AiAgentBridgePackageDTO();
        value.setId("package-1");
        value.setVersion("0.1.0");
        value.setOsType("WINDOWS");
        value.setArchitecture("X64");
        value.setSha256("abc");
        value.setStatus("INACTIVE");
        when(repository.findById("package-1")).thenReturn(value);
        when(repository.updateStatus(eq("package-1"), eq("ACTIVE"), eq("admin"), anyLong())).thenReturn(1);

        AiAgentBridgePackageDTO result = service.activate("package-1", "admin");

        assertEquals("ACTIVE", result.getStatus());
        verify(repository).deactivatePlatform(eq("WINDOWS"), eq("X64"), eq("admin"), anyLong());
    }

    private AiAgentBridgePackageUploadRequest request() {
        AiAgentBridgePackageUploadRequest request = new AiAgentBridgePackageUploadRequest();
        request.setVersion("0.1.0");
        request.setOsType("WINDOWS");
        request.setArchitecture("X64");
        request.setActivate(true);
        return request;
    }

    private byte[] zip(String name, String content) throws Exception {
        return zipEntries(java.util.Map.of(name, content));
    }

    private byte[] zipEntries(java.util.Map<String, String> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
