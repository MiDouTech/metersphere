package io.metersphere.project.service;

import io.metersphere.project.domain.FileMetadata;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.Translator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileMetadataDownloadFailureTests {
    private final FileManagementService storage = mock(FileManagementService.class);
    private final FileMetadataService service = new FileMetadataService();
    private final FileMetadata metadata = new FileMetadata();

    FileMetadataDownloadFailureTests() {
        ReflectionTestUtils.setField(service, "fileManagementService", storage);
        metadata.setId("managed-file");
    }

    @Test
    void bytesAreReturnedWithoutTemporaryFileRoundTrip() throws Exception {
        byte[] bytes = {1, 2, 3};
        when(storage.getFile(metadata)).thenReturn(bytes);
        assertArrayEquals(bytes, service.getFileByte(metadata));
    }

    @Test
    void missingMetadataIsAUserSafeBusinessError() {
        try (var translator = mockStatic(Translator.class)) {
            translator.when(() -> Translator.get("file.not.exist")).thenReturn("File does not exist");
            assertEquals("File does not exist", assertThrows(MSException.class,
                    () -> service.getFileByte(null)).getMessage());
        }
        verifyNoInteractions(storage);
    }

    @Test
    void existingBusinessErrorIsNotSwallowed() throws Exception {
        MSException expected = new MSException("File is unavailable");
        when(storage.getFile(metadata)).thenThrow(expected);
        assertSame(expected, assertThrows(MSException.class, () -> service.getFileByte(metadata)));
    }

    @Test
    void transportFailureRetainsOriginalCauseInsteadOfNullPathException() throws Exception {
        IOException failure = new IOException("test-storage-unavailable");
        when(storage.getFile(metadata)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> service.getFileByte(metadata)).getCause());
    }

    @Test
    void missingObjectIsAUserSafeBusinessError() throws Exception {
        var failure = mock(io.minio.errors.ErrorResponseException.class);
        var response = mock(io.minio.messages.ErrorResponse.class);
        when(failure.errorResponse()).thenReturn(response);
        when(response.code()).thenReturn("NoSuchKey");
        when(storage.getFile(metadata)).thenThrow(failure);
        try (var translator = mockStatic(Translator.class)) {
            translator.when(() -> Translator.get("file.not.exist")).thenReturn("File does not exist");
            assertEquals("File does not exist", assertThrows(MSException.class,
                    () -> service.getFileByte(metadata)).getMessage());
        }
    }

    @Test
    void storageAuthorizationFailureIsNotMisreportedAsMissingObject() throws Exception {
        var failure = mock(io.minio.errors.ErrorResponseException.class);
        var response = mock(io.minio.messages.ErrorResponse.class);
        when(failure.errorResponse()).thenReturn(response);
        when(response.code()).thenReturn("AccessDenied");
        when(storage.getFile(metadata)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> service.getFileByte(metadata)).getCause());
    }
}
