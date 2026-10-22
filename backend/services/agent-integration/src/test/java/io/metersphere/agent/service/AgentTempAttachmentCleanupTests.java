package io.metersphere.agent.service;

import io.metersphere.system.domain.AgentTempAttachment;
import io.metersphere.system.mapper.AgentTempAttachmentMapper;
import io.metersphere.system.service.CommonFileService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.IOException;
import java.util.List;
import static org.mockito.Mockito.*;

class AgentTempAttachmentCleanupTests {
    @Test void storageFailureRetainsMetadataAndCanBeRetried() throws Exception {
        var service = new AgentTempAttachmentService();
        var files = mock(CommonFileService.class);
        var mapper = mock(AgentTempAttachmentMapper.class);
        var jdbc = mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(service, "commonFileService", files);
        ReflectionTestUtils.setField(service, "agentTempAttachmentMapper", mapper);
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        var attachment = new AgentTempAttachment();
        attachment.setId("expired"); attachment.setFileId("file"); attachment.setFileName("image.png");
        when(mapper.selectExpiredUnlinked(anyLong(), eq(200))).thenReturn(List.of(attachment));
        doThrow(new IOException("storage unavailable")).doNothing().when(files).deleteExpiredTempUpload("file", "image.png");
        service.cleanupExpired();
        verify(mapper, never()).deleteByPrimaryKey(anyString());
        verifyNoInteractions(jdbc);
        service.cleanupExpired();
        var order = inOrder(files, mapper);
        order.verify(files, times(2)).deleteExpiredTempUpload("file", "image.png");
        order.verify(mapper).deleteByPrimaryKey("expired");
    }
}
