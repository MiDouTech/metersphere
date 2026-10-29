package io.metersphere.bug.service;

import io.metersphere.bug.domain.Bug;
import io.metersphere.bug.domain.BugComment;
import io.metersphere.bug.dto.request.BugCommentEditRequest;
import io.metersphere.bug.mapper.BugCommentMapper;
import io.metersphere.bug.mapper.BugMapper;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.notice.constants.NoticeConstants;
import io.metersphere.system.uid.IDGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BugCommentScopeTests {
    private final BugCommentService service=new BugCommentService();
    private final BugMapper bugs=mock(BugMapper.class);
    private final BugCommentMapper comments=mock(BugCommentMapper.class);
    private final BugAttachmentService attachments=mock(BugAttachmentService.class);

    BugCommentScopeTests() {
        ReflectionTestUtils.setField(service,"bugMapper",bugs);
        ReflectionTestUtils.setField(service,"bugCommentMapper",comments);
        ReflectionTestUtils.setField(service,"bugAttachmentService",attachments);
    }

    @Test void absentAndDeletedBugsAreRejectedBeforeAttachmentTransfer() {
        var request=new BugCommentEditRequest();request.setBugId("bug");
        assertThrows(MSException.class,()->service.addComment(request,"user"));
        var deleted=new Bug();deleted.setId("bug");deleted.setDeleted(true);
        when(bugs.selectByPrimaryKey("bug")).thenReturn(deleted);
        assertThrows(MSException.class,()->service.addComment(request,"user"));
        verifyNoInteractions(comments,attachments);
    }

    @Test void replyCannotLinkACommentFromAnotherBugOrTransferFilesFirst() {
        var bug=new Bug();bug.setId("bug");bug.setProjectId("project");
        when(bugs.selectByPrimaryKey("bug")).thenReturn(bug);
        var parent=new BugComment();parent.setId("parent");parent.setBugId("other-bug");
        when(comments.selectByPrimaryKey("parent")).thenReturn(parent);
        var request=new BugCommentEditRequest();request.setBugId("bug");request.setParentId("parent");
        request.setEvent(NoticeConstants.Event.REPLY);request.setReplyUser("user");request.setContent("reply");
        try(var ids=mockStatic(IDGenerator.class)) {
            ids.when(IDGenerator::nextStr).thenReturn("new-comment");
            assertEquals("回复评论不属于当前缺陷",assertThrows(MSException.class,()->service.addComment(request,"user")).getMessage());
        }
        verifyNoInteractions(attachments);
        verify(comments,never()).insertSelective(any());
    }
}
