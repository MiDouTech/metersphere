package io.metersphere.bug.service;

import io.metersphere.bug.domain.Bug;
import io.metersphere.bug.dto.request.BugDeleteFileRequest;
import io.metersphere.bug.mapper.BugMapper;
import io.metersphere.project.domain.FileAssociation;
import io.metersphere.project.domain.FileMetadata;
import io.metersphere.project.mapper.FileAssociationMapper;
import io.metersphere.project.mapper.FileMetadataMapper;
import io.metersphere.project.service.FileAssociationService;
import io.metersphere.sdk.util.FileAssociationSourceUtil;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.Translator;
import org.apache.shiro.authz.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BugAttachmentScopeTests {
    private final BugAttachmentService service = new BugAttachmentService();
    private final BugMapper bugs = mock(BugMapper.class);
    private final FileAssociationMapper associations = mock(FileAssociationMapper.class);
    private final FileMetadataMapper files = mock(FileMetadataMapper.class);
    private final FileAssociationService writes = mock(FileAssociationService.class);
    private final BugDeleteFileRequest request = new BugDeleteFileRequest();

    BugAttachmentScopeTests() {
        ReflectionTestUtils.setField(service, "bugMapper", bugs);
        ReflectionTestUtils.setField(service, "fileAssociationMapper", associations);
        ReflectionTestUtils.setField(service, "fileMetadataMapper", files);
        ReflectionTestUtils.setField(service, "fileAssociationService", writes);
        var bug = new Bug(); bug.setId("bug-a"); bug.setProjectId("project-a");
        when(bugs.selectByPrimaryKey("bug-a")).thenReturn(bug);
        request.setBugId("bug-a"); request.setProjectId("project-a"); request.setRefId("ref");
    }

    @Test
    void missingBugAndForgedProjectFailBeforeAttachmentLookup() {
        request.setBugId("missing");
        assertThrows(MSException.class, () -> service.upgrade(request, "user"));
        request.setBugId("bug-a"); request.setProjectId("other-project");
        assertThrows(UnauthorizedException.class, () -> service.upgrade(request, "user"));
        verifyNoInteractions(associations, files, writes);
    }

    @Test
    void missingDeletedOrForeignAssociationCannotBeUpgraded() {
        rejected();
        var relation = relation(); relation.setSourceId("other-bug");
        when(associations.selectByPrimaryKey("ref")).thenReturn(relation);
        rejected();
        relation.setSourceId("bug-a"); relation.setDeleted(true);
        rejected();
        verifyNoInteractions(files, writes);
    }

    @Test
    void missingAndCrossProjectFileCannotBeUpgraded() {
        when(associations.selectByPrimaryKey("ref")).thenReturn(relation());
        rejected();
        var file = new FileMetadata(); file.setId("file"); file.setProjectId("other-project");
        when(files.selectByPrimaryKey("file")).thenReturn(file);
        rejected();
        verifyNoInteractions(writes);
    }

    private FileAssociation relation() {
        var relation = new FileAssociation();
        relation.setId("ref"); relation.setSourceId("bug-a"); relation.setSourceType(FileAssociationSourceUtil.SOURCE_TYPE_BUG);
        relation.setFileId("file"); relation.setDeleted(false);
        return relation;
    }

    private void rejected() {
        try (var translator = mockStatic(Translator.class)) {
            translator.when(() -> Translator.get(anyString())).thenAnswer(call -> call.getArgument(0));
            assertThrows(MSException.class, () -> service.upgrade(request, "user"));
        }
    }
}
