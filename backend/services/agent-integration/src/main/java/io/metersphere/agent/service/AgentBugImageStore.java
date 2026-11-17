package io.metersphere.agent.service;

import io.metersphere.bug.domain.BugLocalAttachment;
import io.metersphere.project.domain.FileMetadata;
import io.metersphere.project.service.FileManagementService;
import io.metersphere.sdk.constants.DefaultRepositoryDir;
import io.metersphere.sdk.file.FileCenter;
import io.metersphere.sdk.file.FileRequest;
import io.metersphere.sdk.file.MinioRepository;
import org.springframework.stereotype.Service;

/** Resolves storage exclusively from server-owned records. All image streams are bounded. */
@Service
public class AgentBugImageStore {
    private final FileManagementService files;
    public AgentBugImageStore(FileManagementService files) { this.files = files; }

    FileRequest local(String project, String bug, BugLocalAttachment attachment) {
        return new FileRequest(DefaultRepositoryDir.getBugDir(project, bug) + "/" + attachment.getFileId(),
                "MINIO", attachment.getFileName());
    }

    FileRequest managed(FileMetadata metadata) { return files.imageReadRequest(metadata); }

    String revision(FileRequest request) throws Exception {
        if ("MINIO".equals(request.getStorage()))
            return ((MinioRepository) FileCenter.getRepository("MINIO")).objectRevision(request);
        if ("LOCAL".equals(request.getStorage()))
            return ((io.metersphere.sdk.file.LocalFileRepository) FileCenter.getRepository("LOCAL")).objectRevision(request);
        // Managed Git metadata is pinned to an immutable commit and bound into the image reference.
        return "GIT:" + request.getGitRevision();
    }

    byte[] read(FileRequest request) throws Exception {
        try (var stream = FileCenter.getRepository(request.getStorage()).getFileAsStream(request)) {
            if (stream == null) throw BugImageValidator.failure("RESOURCE_UNAVAILABLE");
            return stream.readNBytes(BugImageValidator.MAX_BYTES + 1);
        }
    }

    byte[] head(FileRequest request) throws Exception {
        String generation="LOCAL".equals(request.getStorage())?revision(request):null;
        try (var stream = FileCenter.getRepository(request.getStorage()).getFileAsStream(request)) {
            byte[] header=stream == null ? new byte[0] : stream.readNBytes(16);
            if(generation!=null&&!generation.equals(revision(request)))throw new java.io.IOException("Managed local header changed while indexing");
            return header;
        }
    }
}
