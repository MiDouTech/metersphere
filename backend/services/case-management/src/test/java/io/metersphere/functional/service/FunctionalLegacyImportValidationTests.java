package io.metersphere.functional.service;

import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class FunctionalLegacyImportValidationTests {
    @Test void rejectsExcessiveArchiveEntriesBeforeWorkbookParsing() throws Exception {
        var bytes=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(bytes)) {
            for(int i=0;i<3001;i++){zip.putNextEntry(new ZipEntry("part-"+i));zip.closeEntry();}
        }
        assertThrows(MSException.class,()->FunctionalLegacyImportService.validateArchive(new MockMultipartFile("file","input.xlsx","application/zip",bytes.toByteArray())));
    }
    @Test void permitsSmallArchiveAndNoProgressCallbackOutsideTrackedRequest() throws Exception {
        var bytes=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(bytes)){zip.putNextEntry(new ZipEntry("test"));zip.write("small".getBytes());zip.closeEntry();}
        assertDoesNotThrow(()->FunctionalLegacyImportService.validateArchive(new MockMultipartFile("file","input.xlsx","application/zip",bytes.toByteArray())));
        assertDoesNotThrow(()->FunctionalLegacyImportService.stage("PARSING",100));
    }
}
