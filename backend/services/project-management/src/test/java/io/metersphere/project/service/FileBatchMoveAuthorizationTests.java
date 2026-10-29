package io.metersphere.project.service;

import io.metersphere.project.domain.FileMetadata;
import io.metersphere.project.domain.Project;
import io.metersphere.project.dto.filemanagement.request.FileBatchMoveRequest;
import io.metersphere.project.mapper.FileMetadataMapper;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.sdk.constants.ModuleConstants;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.Translator;
import io.metersphere.system.utils.SessionUtils;
import org.apache.shiro.authz.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileBatchMoveAuthorizationTests {
    private final FileMetadataService service = new FileMetadataService();
    private final FileManagementService files = mock(FileManagementService.class);
    private final FileMetadataMapper writes = mock(FileMetadataMapper.class);
    private final FileBatchMoveRequest request = new FileBatchMoveRequest();

    FileBatchMoveAuthorizationTests() {
        ProjectMapper projects = mock(ProjectMapper.class);
        Project project = new Project();
        project.setId("project-a");
        project.setOrganizationId("organization-a");
        when(projects.selectByPrimaryKey("project-a")).thenReturn(project);
        ReflectionTestUtils.setField(service, "projectMapper", projects);
        ReflectionTestUtils.setField(service, "fileManagementService", files);
        ReflectionTestUtils.setField(service, "fileMetadataMapper", writes);
        request.setProjectId("project-a");
        request.setMoveModuleId(ModuleConstants.DEFAULT_NODE_ID);
        request.setSelectAll(true);
    }

    @Test
    void allPagesWithEmptyExplicitIdsUsesActualProjectPermission() {
        when(files.getProcessList(request)).thenReturn(List.of());
        try (var session = mockStatic(SessionUtils.class)) {
            session.when(() -> SessionUtils.hasPermission("organization-a", "project-a",
                    PermissionConstants.PROJECT_FILE_MANAGEMENT_READ_UPDATE)).thenReturn(true);
            assertDoesNotThrow(() -> service.batchMove(request, "member"));
            session.verify(() -> SessionUtils.hasPermission("organization-a", "project-a",
                    PermissionConstants.PROJECT_FILE_MANAGEMENT_READ_UPDATE));
        }
        verify(files).getProcessList(request);
        verifyNoInteractions(writes);
    }

    @Test
    void unauthorizedProjectIsRejectedBeforeAnyFileLookup() {
        try (var session = mockStatic(SessionUtils.class)) {
            assertThrows(UnauthorizedException.class, () -> service.batchMove(request, "member"));
        }
        verifyNoInteractions(files, writes);
    }

    @Test
    void permissionOnAnotherProjectDoesNotAuthorizeTarget() {
        try (var session = mockStatic(SessionUtils.class)) {
            session.when(() -> SessionUtils.hasPermission("organization-b", "project-b",
                    PermissionConstants.PROJECT_FILE_MANAGEMENT_READ_UPDATE)).thenReturn(true);
            assertThrows(UnauthorizedException.class, () -> service.batchMove(request, "member"));
        }
        verifyNoInteractions(files, writes);
    }

    @Test
    void explicitFileFromAnotherProjectCannotBeMovedEvenWithTargetPermission() {
        FileMetadata foreign = new FileMetadata();
        foreign.setProjectId("project-b");
        when(files.getProcessList(request)).thenReturn(List.of(foreign));
        try (var session = mockStatic(SessionUtils.class); var translator = mockStatic(Translator.class)) {
            session.when(() -> SessionUtils.hasPermission("organization-a", "project-a",
                    PermissionConstants.PROJECT_FILE_MANAGEMENT_READ_UPDATE)).thenReturn(true);
            translator.when(() -> Translator.get("check_owner_case")).thenReturn("Resource is unavailable");
            assertThrows(MSException.class, () -> service.batchMove(request, "member"));
        }
        verifyNoInteractions(writes);
    }
}
