package io.metersphere.agent.service;

import io.metersphere.sdk.dto.FileMetadataRepositoryDTO;
import io.metersphere.sdk.dto.FileModuleRepositoryDTO;
import io.metersphere.sdk.file.FileRequest;
import io.metersphere.sdk.file.GitRepository;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class BugImageGitStorageTests {
    @TempDir Path directory;
    @Test void readsTheRequestedFileAtThePinnedCommitRatherThanTheRepositoryUrl() throws Exception {
        byte[] original=BugImageValidatorTests.image("png",2,2);
        String commit;
        try(var git=Git.init().setDirectory(directory.toFile()).call()){
            Files.write(directory.resolve("proof.png"),original);git.add().addFilepattern("proof.png").call();
            commit=git.commit().setAuthor("fixture","fixture@example.invalid").setMessage("original").call().getName();
            Files.writeString(directory.resolve("proof.png"),"replacement");git.add().addFilepattern("proof.png").call();
            git.commit().setAuthor("fixture","fixture@example.invalid").setMessage("replacement").call();
        }
        var repository=new FileModuleRepositoryDTO();repository.setUrl(directory.toUri().toString());repository.setUserName("fixture");repository.setToken("");
        var metadata=new FileMetadataRepositoryDTO();metadata.setCommitId(commit);metadata.setBranch("master");
        var request=new FileRequest("server-owned","GIT","proof.png");request.setGitFileRequest(repository,metadata);
        try(var stream=new GitRepository().getFileAsStream(request)){assertArrayEquals(original,stream.readAllBytes());}
        request.setFileName("missing.png");assertThrows(java.io.FileNotFoundException.class,()->new GitRepository().getFileAsStream(request));
    }
}
