package io.metersphere.agent.service;

import org.junit.jupiter.api.Test;
import io.metersphere.sdk.exception.MSException;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentBugExportDownloadTests {
    @Test void reconstructsFileAcrossChunksWithStableDigestAndEof() throws Exception {
        byte[] bytes=new byte[160123]; new Random(42).nextBytes(bytes);
        var output=new ByteArrayOutputStream(); long offset=0;
        while (offset<bytes.length) {
            var result=AgentBugExportDownloadService.readChunk(new ByteArrayInputStream(bytes),bytes.length,offset,32768);
            output.write(Base64.getDecoder().decode((String) result.get("base64")));
            assertEquals(org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes), result.get("sha256"));
            offset=(Long) result.get("nextOffset");
            assertEquals(offset==bytes.length,result.get("eof"));
        }
        assertArrayEquals(bytes,output.toByteArray());
        var eof=AgentBugExportDownloadService.readChunk(new ByteArrayInputStream(bytes),bytes.length,bytes.length,1);
        assertEquals("",eof.get("base64")); assertEquals(true,eof.get("eof"));
    }
    @Test void rejectsInvalidRangesAndChangedFiles() {
        assertThrows(MSException.class,()->AgentBugExportDownloadService.readChunk(new ByteArrayInputStream(new byte[1]),1,-1,1));
        assertThrows(MSException.class,()->AgentBugExportDownloadService.readChunk(new ByteArrayInputStream(new byte[1]),1,2,1));
        assertThrows(MSException.class,()->AgentBugExportDownloadService.readChunk(new ByteArrayInputStream(new byte[1]),1,0,65537));
        assertThrows(MSException.class,()->AgentBugExportDownloadService.readChunk(new ByteArrayInputStream(new byte[1]),2,0,1));
        assertThrows(MSException.class,()->AgentBugExportDownloadService.readChunk(new ByteArrayInputStream(new byte[2]),1,0,1));
    }
    @Test void denialPreventsStorageRead() {
        var attachments=org.mockito.Mockito.mock(AgentTempAttachmentService.class);
        org.mockito.Mockito.when(attachments.requireBugExportDownload("p","f")).thenThrow(new MSException("SCOPE_DENIED"));
        var service=org.mockito.Mockito.spy(new AgentBugExportDownloadService());
        org.springframework.test.util.ReflectionTestUtils.setField(service,"attachments",attachments);
        assertThrows(MSException.class,()->service.download("p","f",0,32768));
        try { org.mockito.Mockito.verify(service,org.mockito.Mockito.never()).open(org.mockito.ArgumentMatchers.any()); }
        catch(Exception failure) { throw new AssertionError(failure); }
    }
    @Test void emptyFileHasStableDigest() throws Exception {
        var result=AgentBugExportDownloadService.readChunk(new ByteArrayInputStream(new byte[0]),0,0,1);
        assertEquals(true,result.get("eof")); assertEquals("",result.get("base64"));
        assertEquals(org.apache.commons.codec.digest.DigestUtils.sha256Hex(new byte[0]),result.get("sha256"));
    }
}
