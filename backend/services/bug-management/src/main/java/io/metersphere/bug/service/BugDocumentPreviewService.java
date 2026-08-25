package io.metersphere.bug.service;

import io.metersphere.bug.dto.request.BugFileSourceRequest;
import io.metersphere.project.service.FileMetadataService;
import io.metersphere.sdk.exception.MSException;
import jakarta.annotation.Resource;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class BugDocumentPreviewService {

    private static final long MAX_DOCUMENT_SIZE = 50L * 1024 * 1024;

    @Resource
    private BugAttachmentService bugAttachmentService;
    @Resource
    private FileMetadataService fileMetadataService;

    @Value("${document.preview.libreoffice-command:libreoffice}")
    private String libreOfficeCommand;

    @Value("${document.preview.timeout-seconds:45}")
    private long timeoutSeconds;

    public ResponseEntity<byte[]> preview(BugFileSourceRequest request) {
        String validatedFileName = bugAttachmentService.validatePreviewSource(request);
        String extension = FilenameUtils.getExtension(StringUtils.defaultString(validatedFileName))
                .toLowerCase(Locale.ROOT);
        if (!StringUtils.equals(extension, "doc")) {
            throw new MSException("仅支持将 .doc 文档转换为 PDF 预览");
        }
        ResponseEntity<byte[]> source = Boolean.TRUE.equals(request.getAssociated())
                ? fileMetadataService.downloadById(request.getFileId())
                : bugAttachmentService.downloadOrPreview(request);
        byte[] sourceBytes = source.getBody();
        if (sourceBytes == null || sourceBytes.length == 0) {
            throw new MSException("预览文件不存在或内容为空");
        }
        if (sourceBytes.length > MAX_DOCUMENT_SIZE) {
            throw new MSException("文档超过 50MB，请下载后查看");
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(convertDocToPdf(sourceBytes));
    }

    private byte[] convertDocToPdf(byte[] sourceBytes) {
        Path tempDirectory = null;
        try {
            tempDirectory = Files.createTempDirectory("ms-bug-doc-preview-");
            Path source = tempDirectory.resolve("source.doc");
            Files.write(source, sourceBytes);
            Process process = new ProcessBuilder(
                    libreOfficeCommand,
                    "--headless", "--nologo", "--nodefault", "--nolockcheck", "--nofirststartwizard",
                    "--convert-to", "pdf", "--outdir", tempDirectory.toString(), source.toString())
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new MSException("文档转换超时，请下载后查看");
            }
            Path pdf = tempDirectory.resolve("source.pdf");
            if (process.exitValue() != 0 || Files.notExists(pdf)) {
                throw new MSException("文档转换失败，请下载后查看");
            }
            return Files.readAllBytes(pdf);
        } catch (MSException e) {
            throw e;
        } catch (IOException e) {
            throw new MSException("文档预览服务不可用，请下载后查看");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MSException("文档转换被中断，请稍后重试");
        } finally {
            deleteTemporaryDirectory(tempDirectory);
        }
    }

    private void deleteTemporaryDirectory(Path directory) {
        if (directory == null || Files.notExists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Temporary files are isolated under the generated directory.
                }
            });
        } catch (IOException ignored) {
            // Conversion result has already been returned; cleanup will also be handled by the OS.
        }
    }
}
