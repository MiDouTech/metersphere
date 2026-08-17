package io.metersphere.functional.asset.service;

import io.metersphere.functional.dto.response.FunctionalCaseImportResponse;
import io.metersphere.functional.request.FunctionalCaseImportRequest;
import io.metersphere.functional.service.FunctionalCaseFileService;
import io.metersphere.system.dto.sdk.SessionUser;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
public class CaseAssetImportWorker {
    @Resource private FunctionalCaseFileService functionalCaseFileService;
    @Resource private CaseAssetImportJobService jobService;

    @Async
    public void submit(String jobId, String type, FunctionalCaseImportRequest request, SessionUser user,
                       String fileName, String contentType, byte[] content) {
        try {
            MultipartFile file = new InMemoryMultipartFile(fileName, contentType, content);
            FunctionalCaseImportResponse response = "xmind".equalsIgnoreCase(type)
                    ? functionalCaseFileService.importXMind(request, user, file)
                    : functionalCaseFileService.importExcel(request, user, file);
            jobService.complete(jobId, response);
        } catch (Exception e) {
            RuntimeException failure = e instanceof RuntimeException runtime ? runtime
                    : new io.metersphere.sdk.exception.MSException("资产用例导入失败: " + e.getMessage(), e);
            jobService.fail(jobId, failure);
        }
    }

    private record InMemoryMultipartFile(String originalFilename, String contentType, byte[] content)
            implements MultipartFile {
        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }
        @Override public void transferTo(java.io.File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), content);
        }
    }
}
