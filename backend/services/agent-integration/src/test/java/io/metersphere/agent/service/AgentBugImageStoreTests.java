package io.metersphere.agent.service;

import io.metersphere.project.service.FileManagementService;
import io.metersphere.sdk.file.*;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentBugImageStoreTests {
    @Test void rawReadStopsAtLimitPlusOneAndClosesTheStream() throws Exception {
        var repository=mock(FileRepository.class); var request=new FileRequest("server","MINIO","file");
        final boolean[] closed={false};
        var stream=new ByteArrayInputStream(new byte[BugImageValidator.MAX_BYTES+100]){
            @Override public void close(){closed[0]=true;}
        };
        when(repository.getFileAsStream(request)).thenReturn(stream);
        try(var center=mockStatic(FileCenter.class)){
            center.when(()->FileCenter.getRepository("MINIO")).thenReturn(repository);
            var store=new AgentBugImageStore(mock(FileManagementService.class));
            assertEquals(BugImageValidator.MAX_BYTES+1,store.read(request).length);
            assertEquals(99,stream.available()); assertTrue(closed[0]);
        }
    }
    @Test void indexReadsOnlyASmallHeaderAndClosesTheStream() throws Exception {
        var repository=mock(FileRepository.class); var request=new FileRequest("server","MINIO","file");
        var stream=new ByteArrayInputStream(new byte[1000]); when(repository.getFileAsStream(request)).thenReturn(stream);
        try(var center=mockStatic(FileCenter.class)){
            center.when(()->FileCenter.getRepository("MINIO")).thenReturn(repository);
            assertEquals(16,new AgentBugImageStore(mock(FileManagementService.class)).head(request).length);
            assertEquals(984,stream.available());
        }
    }
}
