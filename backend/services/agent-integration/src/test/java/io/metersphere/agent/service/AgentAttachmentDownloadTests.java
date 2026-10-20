package io.metersphere.agent.service;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.file.FileCenter;
import io.metersphere.sdk.file.FileRepository;
import io.metersphere.sdk.file.FileRequest;
import io.metersphere.system.service.CommonFileService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentAttachmentDownloadTests {
    @Test void storageReceivesTheNormalizedUploadName() {
        var service = new AgentTempAttachmentService();
        var files = mock(CommonFileService.class);
        var projects = mock(AgentProjectService.class);
        var metadata = mock(io.metersphere.system.mapper.AgentTempAttachmentMapper.class);
        ReflectionTestUtils.setField(service, "commonFileService", files);
        ReflectionTestUtils.setField(service, "agentProjectService", projects);
        ReflectionTestUtils.setField(service, "agentTempAttachmentMapper", metadata);
        when(projects.resolveProjectId("project")).thenReturn("project");
        when(files.uploadTempImgFile(any())).thenReturn("file");
        var token = new io.metersphere.system.domain.AgentToken();
        token.setId("token"); token.setUserId("user");
        io.metersphere.agent.security.AgentTokenContext.set(token);
        try (var session = mockStatic(io.metersphere.system.utils.SessionUtils.class);
             var ids = mockStatic(io.metersphere.system.uid.IDGenerator.class)) {
            session.when(io.metersphere.system.utils.SessionUtils::getUserId).thenReturn("user");
            ids.when(io.metersphere.system.uid.IDGenerator::nextStr).thenReturn("attachment");
            var upload = new org.springframework.mock.web.MockMultipartFile("file", "../../payload.txt",
                    "text/plain", new byte[]{1, 2, 3});
            var result = service.upload(upload, "project", "CASE_DETAIL", null);
            var captured = ArgumentCaptor.forClass(org.springframework.web.multipart.MultipartFile.class);
            verify(files).uploadTempImgFile(captured.capture());
            assertEquals("payload.txt", captured.getValue().getOriginalFilename());
            assertEquals("payload.txt", result.getFileName());
        } finally {
            io.metersphere.agent.security.AgentTokenContext.clear();
        }
    }

    @Test void downloadsTheStoredFilenameInsteadOfTheFolder() throws Exception {
        var files = mock(CommonFileService.class);
        var repository = mock(FileRepository.class);
        var service = new AgentAttachmentService();
        ReflectionTestUtils.setField(service, "commonFileService", files);
        when(files.getTempFileNameByFileId("file")).thenReturn("evidence.txt");
        when(repository.getFile(any())).thenReturn(new byte[]{1, 2, 3});
        try (var center = mockStatic(FileCenter.class)) {
            center.when(FileCenter::getDefaultRepository).thenReturn(repository);
            assertArrayEquals(new byte[]{1, 2, 3}, service.download("project", "file").getBody());
            var request = ArgumentCaptor.forClass(FileRequest.class);
            verify(repository).getFile(request.capture());
            assertEquals("evidence.txt", request.getValue().getFileName());
            assertTrue(request.getValue().getFolder().endsWith("/file"));
        }
    }

    @Test void missingStoredFilenameDoesNotReadAnArbitraryObject() {
        var files = mock(CommonFileService.class);
        var service = new AgentAttachmentService();
        ReflectionTestUtils.setField(service, "commonFileService", files);
        assertThrows(MSException.class, () -> service.download("project", "missing"));
    }
}
