package io.metersphere.sdk.file;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.MsFileUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.util.FileUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.util.List;

@Component
public class LocalFileRepository implements FileRepository {

    @Override
    public String saveFile(MultipartFile multipartFile, FileRequest request) throws IOException {
        if (multipartFile == null || request == null || StringUtils.isEmpty(request.getFileName()) || StringUtils.isEmpty(request.getFolder())) {
            return null;
        }
        MsFileUtils.validateFileName(request.getFolder(), request.getFileName());
        createFileDir(request);
        File file = new File(getFilePath(request));
        try {
            return LocalObjectGeneration.mutate(file.toPath(),()->{
                FileUtils.copyInputStreamToFile(multipartFile.getInputStream(), file);return file.getPath();
            });
        } catch(IOException failure){throw failure;}
        catch(Exception failure){throw new IOException("Managed local write failed",failure);}
    }

    private void createFileDir(FileRequest request) {
        String dir = getFileDir(request);
        File fileDir = new File(dir);
        if (!fileDir.exists() && !fileDir.mkdirs()) {
            throw new RuntimeException("Failed to create directory: " + dir);
        }
    }

    @Override
    public String saveFile(byte[] bytes, FileRequest request) throws IOException {
        File file = new File(getFilePath(request));
        try {
            return LocalObjectGeneration.mutate(file.toPath(),()->{
                try(OutputStream ops=new FileOutputStream(file)){ops.write(bytes);return file.getPath();}
            });
        } catch(IOException failure){throw failure;}
        catch(Exception failure){throw new IOException("Managed local write failed",failure);}
    }

    @Override
    public String saveFile(InputStream inputStream, FileRequest request) throws Exception {
        File file = new File(getFilePath(request));
        return LocalObjectGeneration.mutate(file.toPath(),()->{
            FileUtils.copyInputStreamToFile(inputStream, file);return file.getPath();
        });
    }

    @Override
    public void delete(FileRequest request) throws Exception {
        String path = StringUtils.join(getFilePath(request));
        File file = new File(path);
        LocalObjectGeneration.mutate(file.toPath(),()->{
            FileUtil.deleteContents(file);
            if(file.exists()&&!file.delete())throw new IOException("Managed local delete failed");
            return null;
        });
    }

    @Override
    public void deleteFolder(FileRequest request) throws Exception {
        MsFileUtils.validateFileName(request.getFolder(), request.getFileName());
        this.delete(request);
    }

    @Override
    public byte[] getFile(FileRequest request) throws Exception {
        File file = new File(getFilePath(request));
        return Files.readAllBytes(file.toPath());
    }

    @Override
    public InputStream getFileAsStream(FileRequest request) throws Exception {
        return new FileInputStream(getFilePath(request));
    }

    @Override
    public void downloadFile(FileRequest request, String localPath) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getFolderFileNames(FileRequest request) {
        return null;
    }

    @Override
    public void copyFile(FileCopyRequest request) throws Exception {
        throw new MSException("Not support copy file");
    }

    @Override
    public long getFileSize(FileRequest request) throws Exception {
        File file = new File(getFilePath(request));
        return file.length();
    }

    /** Storage identity, never a caller-visible filesystem path. */
    public String objectRevision(FileRequest request) throws IOException {
        var path=new File(getFilePath(request)).toPath();String generation=LocalObjectGeneration.current(path);
        var attributes = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class);
        if(!generation.equals(LocalObjectGeneration.current(path)))throw new IOException("Managed local object changed during stat");
        return generation+":"+attributes.fileKey() + ":" + attributes.creationTime() + ":" + attributes.lastModifiedTime() + ":" + attributes.size();
    }

    private String getFilePath(FileRequest request) {
        MsFileUtils.validateFileName(request.getFolder(), request.getFileName());
        return StringUtils.join(getFileDir(request), "/", request.getFileName());
    }

    private String getFileDir(FileRequest request) {
        MsFileUtils.validateFileName(request.getFolder(), request.getFileName());
        return request.getFolder();
    }
}
