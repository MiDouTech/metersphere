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
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"CASE_DETAIL,FUNCTIONAL_CASE:READ", "CASE_COMMENT,FUNCTIONAL_CASE:READ",
            "EXECUTION,FUNCTIONAL_CASE:READ", "BUG_DETAIL,PROJECT_BUG:READ", "BUG_COMMENT,PROJECT_BUG:READ", "BUG_EXPORT,PROJECT_BUG:READ+EXPORT"})
    void everyDownloadRechecksCurrentBusinessRole(String purpose,String permission) {
        var service=new AgentTempAttachmentService();var access=mock(AgentBusinessAccess.class);
        var projects=mock(AgentProjectService.class);var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(projects.resolveProjectId("project")).thenReturn("project");
        when(jdbc.queryForList(anyString(),eq("file"),eq("project"),eq("token"),eq("user"),anyLong()))
                .thenReturn(java.util.List.of(java.util.Map.of("purpose",purpose)));
        ReflectionTestUtils.setField(service,"agentProjectService",projects);
        ReflectionTestUtils.setField(service,"businessAccess",access);ReflectionTestUtils.setField(service,"jdbc",jdbc);
        var token=new io.metersphere.system.domain.AgentToken();token.setId("token");token.setUserId("user");
        token.setScopes("OLD_UNRELATED_SCOPE");io.metersphere.agent.security.AgentTokenContext.set(token);
        try {
            assertDoesNotThrow(()->service.requireDownload("project","file"));
            doThrow(AgentBusinessAccess.denied()).when(access).project("project",permission,false);
            assertThrows(MSException.class,()->service.requireDownload("project","file"));
            verify(access,times(2)).project("project",permission,false);
        } finally {io.metersphere.agent.security.AgentTokenContext.clear();}
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"CASE_DETAIL,FUNCTIONAL_CASE:READ", "CASE_COMMENT,FUNCTIONAL_CASE:READ",
            "EXECUTION,FUNCTIONAL_CASE:READ", "BUG_DETAIL,PROJECT_BUG:READ+UPDATE", "BUG_COMMENT,PROJECT_BUG:READ+UPDATE"})
    void uploadDoesNotTouchStorageWithoutCurrentProjectRole(String purpose,String permission) {
        var service=new AgentTempAttachmentService();var access=mock(AgentBusinessAccess.class);
        var projects=mock(AgentProjectService.class);var storage=mock(CommonFileService.class);
        when(projects.resolveProjectId("project")).thenReturn("project");
        doThrow(AgentBusinessAccess.denied()).when(access).project("project",permission,true);
        ReflectionTestUtils.setField(service,"agentProjectService",projects);
        ReflectionTestUtils.setField(service,"businessAccess",access);
        ReflectionTestUtils.setField(service,"commonFileService",storage);
        var token=new io.metersphere.system.domain.AgentToken();token.setId("token");token.setUserId("user");
        io.metersphere.agent.security.AgentTokenContext.set(token);
        try (var session=mockStatic(io.metersphere.system.utils.SessionUtils.class)) {
            session.when(io.metersphere.system.utils.SessionUtils::getUserId).thenReturn("user");
            var upload=new org.springframework.mock.web.MockMultipartFile("file","draft.txt","text/plain",new byte[]{1});
            assertThrows(MSException.class,()->service.upload(upload,"project",purpose,null));
            verify(access).project("project",permission,true);
            verifyNoInteractions(storage);
        } finally {io.metersphere.agent.security.AgentTokenContext.clear();}
    }
    @Test void storageReceivesTheNormalizedUploadName() {
        var service = new AgentTempAttachmentService();
        var files = mock(CommonFileService.class);
        var projects = mock(AgentProjectService.class);
        var metadata = mock(io.metersphere.system.mapper.AgentTempAttachmentMapper.class);
        var guard=mock(AgentUploadStorageGuard.class);
        ReflectionTestUtils.setField(service,"storageGuard",guard);
        when(guard.reserve("payload.txt")).thenReturn("file");
        ReflectionTestUtils.setField(service, "commonFileService", files);
        ReflectionTestUtils.setField(service, "agentProjectService", projects);
        ReflectionTestUtils.setField(service, "agentTempAttachmentMapper", metadata);
        ReflectionTestUtils.setField(service,"businessAccess",mock(AgentBusinessAccess.class));
        when(projects.resolveProjectId("project")).thenReturn("project");
        when(files.uploadTempImgFile(any(),eq("file"))).thenReturn("file");
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
            verify(files).uploadTempImgFile(captured.capture(),eq("file"));
            assertEquals("payload.txt", captured.getValue().getOriginalFilename());
            assertEquals("payload.txt", result.getFileName());
        } finally {
            io.metersphere.agent.security.AgentTokenContext.clear();
        }
    }

    @Test void clientsCannotUploadServerGeneratedBugExports() {
        var service=new AgentTempAttachmentService();var access=mock(AgentBusinessAccess.class);
        var storage=mock(CommonFileService.class);ReflectionTestUtils.setField(service,"businessAccess",access);
        ReflectionTestUtils.setField(service,"commonFileService",storage);
        var token=new io.metersphere.system.domain.AgentToken();token.setId("token");token.setUserId("user");
        io.metersphere.agent.security.AgentTokenContext.set(token);
        try {
            var file=new org.springframework.mock.web.MockMultipartFile("file","export.csv","text/csv",new byte[]{1});
            assertThrows(MSException.class,()->service.upload(file,"project","BUG_EXPORT",null));
            verifyNoInteractions(access,storage);
        } finally {io.metersphere.agent.security.AgentTokenContext.clear();}
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
