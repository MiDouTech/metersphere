package io.metersphere.agent.service;

import io.metersphere.agent.dto.TestAssetVersionDTO;
import io.metersphere.agent.mapper.TestAssetMapper;
import io.metersphere.system.uid.IDGenerator;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestAssetVersionServiceTests {
    @Mock
    private TestAssetMapper mapper;
    @InjectMocks
    private TestAssetVersionService service;

    @Test
    void concurrentPublicationReturnsVersionInsertedByOtherRequest() {
        String content = "{\"name\":\"orders\"}";
        String hash = DigestUtils.sha256Hex(content);
        TestAssetVersionDTO concurrent = new TestAssetVersionDTO();
        concurrent.setId("concurrent-version");
        when(mapper.selectByHash("project", "DATASET", "asset", hash)).thenReturn(null, concurrent);
        when(mapper.selectMaxVersion("project", "DATASET", "asset")).thenReturn(0);
        doThrow(new DuplicateKeyException("concurrent insert")).when(mapper).insertVersion(any());

        TestAssetVersionDTO result;
        try (MockedStatic<IDGenerator> ids = Mockito.mockStatic(IDGenerator.class)) {
            ids.when(IDGenerator::nextStr).thenReturn("generated-version");
            result = service.publish("project", "DATASET", "asset", "1", content, "user");
        }

        assertEquals("concurrent-version", result.getId());
    }

    @Test
    void concurrentDifferentContentRetriesWithNextVersionNumber() {
        String content = "{\"name\":\"changed\"}";
        String hash = DigestUtils.sha256Hex(content);
        when(mapper.selectByHash("project", "DATASET", "asset", hash)).thenReturn(null);
        when(mapper.selectMaxVersion("project", "DATASET", "asset")).thenReturn(1, 2);
        doThrow(new DuplicateKeyException("version collision")).doNothing().when(mapper).insertVersion(any());

        TestAssetVersionDTO result;
        try (MockedStatic<IDGenerator> ids = Mockito.mockStatic(IDGenerator.class)) {
            ids.when(IDGenerator::nextStr).thenReturn("generated-version-1", "generated-version-2");
            result = service.publish("project", "DATASET", "asset", "2", content, "user");
        }

        assertEquals(3, result.getVersionNo());
        verify(mapper, org.mockito.Mockito.times(2)).insertVersion(any());
    }
}
