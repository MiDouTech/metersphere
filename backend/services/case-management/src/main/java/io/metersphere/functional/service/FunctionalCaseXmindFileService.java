package io.metersphere.functional.service;

import io.metersphere.functional.domain.FunctionalCaseXmindFile;
import io.metersphere.functional.dto.FunctionalCaseXmindFileDTO;
import io.metersphere.functional.mapper.ExtFunctionalCaseXmindFileMapper;
import io.metersphere.functional.mapper.FunctionalCaseXmindFileMapper;
import io.metersphere.functional.request.FunctionalCaseXmindFilePageRequest;
import io.metersphere.functional.request.FunctionalCaseXmindFileRenameRequest;
import io.metersphere.functional.request.FunctionalCaseXmindFileUploadRequest;
import io.metersphere.functional.xmind.parser.XMindParser;
import io.metersphere.functional.xmind.pojo.JsonRootBean;
import io.metersphere.functional.xmind.utils.XmindMinderConverter;
import io.metersphere.sdk.constants.DefaultRepositoryDir;
import io.metersphere.sdk.constants.StorageType;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.file.FileRequest;
import io.metersphere.sdk.util.Translator;
import io.metersphere.system.service.FileService;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Xmind 用例文件库：仅存文件资产，禁止解析入库功能用例。
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class FunctionalCaseXmindFileService {

    @Resource
    private FunctionalCaseXmindFileMapper functionalCaseXmindFileMapper;
    @Resource
    private ExtFunctionalCaseXmindFileMapper extFunctionalCaseXmindFileMapper;
    @Resource
    private FileService fileService;

    public List<FunctionalCaseXmindFileDTO> list(FunctionalCaseXmindFilePageRequest request) {
        List<FunctionalCaseXmindFile> files = extFunctionalCaseXmindFileMapper.list(request);
        List<FunctionalCaseXmindFileDTO> result = new ArrayList<>();
        if (CollectionUtils.isEmpty(files)) {
            return result;
        }
        for (FunctionalCaseXmindFile file : files) {
            result.add(toDTO(file));
        }
        return result;
    }

    public FunctionalCaseXmindFileDTO upload(FunctionalCaseXmindFileUploadRequest request, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new MSException(Translator.get("incorrect_format"));
        }
        String original = StringUtils.defaultString(file.getOriginalFilename());
        if (!StringUtils.endsWithIgnoreCase(original, ".xmind")) {
            throw new MSException(Translator.get("incorrect_format"));
        }
        String userId = SessionUtils.getUserId();
        long now = System.currentTimeMillis();
        String id = IDGenerator.nextStr();
        String displayName = StringUtils.defaultIfBlank(request.getName(), stripExt(original));

        FileRequest fileRequest = new FileRequest();
        fileRequest.setFileName(original);
        fileRequest.setFolder(DefaultRepositoryDir.getFunctionalCaseXmindFileDir(request.getProjectId(), id));
        fileRequest.setStorage(StorageType.MINIO.name());
        try {
            fileService.upload(file, fileRequest);
        } catch (Exception e) {
            throw new MSException("save file error");
        }

        FunctionalCaseXmindFile record = new FunctionalCaseXmindFile();
        record.setId(id);
        record.setProjectId(request.getProjectId());
        record.setName(displayName);
        record.setOriginalName(original);
        record.setFileId(id);
        record.setSize(file.getSize());
        record.setStorage(StorageType.MINIO.name());
        record.setCreateTime(now);
        record.setUpdateTime(now);
        record.setCreateUser(userId);
        record.setUpdateUser(userId);
        functionalCaseXmindFileMapper.insert(record);
        return toDTO(record);
    }

    public FunctionalCaseXmindFileDTO rename(FunctionalCaseXmindFileRenameRequest request) {
        FunctionalCaseXmindFile existing = checkAndGet(request.getId());
        existing.setName(request.getName().trim());
        existing.setUpdateTime(System.currentTimeMillis());
        existing.setUpdateUser(SessionUtils.getUserId());
        functionalCaseXmindFileMapper.updateByPrimaryKeySelective(existing);
        return toDTO(functionalCaseXmindFileMapper.selectByPrimaryKey(existing.getId()));
    }

    public ResponseEntity<byte[]> download(String id) {
        FunctionalCaseXmindFile existing = checkAndGet(id);
        byte[] bytes = downloadBytes(existing);
        String filename = StringUtils.defaultIfBlank(existing.getOriginalName(), existing.getName() + ".xmind");
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    /**
     * 在线浏览：解析 xmind 为 MinderJson，不写入功能用例。
     */
    public Map<String, Object> preview(String id) {
        FunctionalCaseXmindFile existing = checkAndGet(id);
        byte[] bytes = downloadBytes(existing);
        File temp = null;
        try {
            temp = File.createTempFile("xmind-preview-", ".xmind");
            FileUtils.writeByteArrayToFile(temp, bytes);
            List<JsonRootBean> sheets = XMindParser.parseObject(temp);
            return XmindMinderConverter.toMinderJson(sheets);
        } catch (MSException e) {
            throw e;
        } catch (Exception e) {
            throw new MSException(e.getMessage());
        } finally {
            FileUtils.deleteQuietly(temp);
        }
    }

    public void delete(String id) {
        FunctionalCaseXmindFile existing = checkAndGet(id);
        FileRequest fileRequest = buildFileRequest(existing);
        try {
            fileService.deleteFile(fileRequest);
        } catch (Exception e) {
            // 文件不存在仍删除元数据
        }
        functionalCaseXmindFileMapper.deleteByPrimaryKey(id);
    }

    private byte[] downloadBytes(FunctionalCaseXmindFile existing) {
        try {
            return fileService.download(buildFileRequest(existing));
        } catch (Exception e) {
            throw new MSException(Translator.get("resource_not_exist"));
        }
    }

    private FileRequest buildFileRequest(FunctionalCaseXmindFile existing) {
        FileRequest fileRequest = new FileRequest();
        fileRequest.setFileName(existing.getOriginalName());
        fileRequest.setFolder(DefaultRepositoryDir.getFunctionalCaseXmindFileDir(existing.getProjectId(), existing.getFileId()));
        fileRequest.setStorage(StringUtils.defaultIfBlank(existing.getStorage(), StorageType.MINIO.name()));
        return fileRequest;
    }

    private FunctionalCaseXmindFile checkAndGet(String id) {
        FunctionalCaseXmindFile record = functionalCaseXmindFileMapper.selectByPrimaryKey(id);
        if (record == null) {
            throw new MSException(Translator.get("resource_not_exist"));
        }
        String currentProjectId = SessionUtils.getCurrentProjectId();
        if (StringUtils.isNotBlank(currentProjectId)
                && !StringUtils.equals(currentProjectId, record.getProjectId())) {
            throw new MSException(Translator.get("resource_not_exist"));
        }
        return record;
    }

    private String stripExt(String name) {
        if (StringUtils.endsWithIgnoreCase(name, ".xmind")) {
            return name.substring(0, name.length() - 6);
        }
        return name;
    }

    private FunctionalCaseXmindFileDTO toDTO(FunctionalCaseXmindFile file) {
        FunctionalCaseXmindFileDTO dto = new FunctionalCaseXmindFileDTO();
        dto.setId(file.getId());
        dto.setProjectId(file.getProjectId());
        dto.setName(file.getName());
        dto.setOriginalName(file.getOriginalName());
        dto.setSize(file.getSize());
        dto.setCreateTime(file.getCreateTime());
        dto.setUpdateTime(file.getUpdateTime());
        dto.setCreateUser(file.getCreateUser());
        dto.setUpdateUser(file.getUpdateUser());
        return dto;
    }
}
