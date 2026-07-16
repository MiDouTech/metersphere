package io.metersphere.system.service;

import io.metersphere.sdk.constants.DefaultRepositoryDir;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.file.FileRequest;
import io.metersphere.sdk.file.MinioRepository;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.domain.SystemParameter;
import io.metersphere.system.domain.SystemParameterExample;
import io.metersphere.system.dto.DisplayDTO;
import io.metersphere.system.mapper.SystemParameterMapper;
import jakarta.annotation.Resource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 社区版界面配置（/display/info、/display/save）
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class DisplayService {

    private static final Map<String, String> FILE_PREVIEW = Map.of(
            "ui.icon", "/base-display/get/icon",
            "ui.loginLogo", "/base-display/get/login-logo",
            "ui.loginImage", "/base-display/get/login-image",
            "ui.logoPlatform", "/base-display/get/logo-platform"
    );

    @Resource
    private SystemParameterMapper systemParameterMapper;
    @Resource
    private MinioRepository minioRepository;

    public List<DisplayDTO> uiInfo() {
        SystemParameterExample example = new SystemParameterExample();
        example.createCriteria().andParamKeyLike("ui.%");
        List<SystemParameter> params = systemParameterMapper.selectByExample(example);
        List<DisplayDTO> result = new ArrayList<>();
        if (CollectionUtils.isEmpty(params)) {
            return result;
        }
        for (SystemParameter param : params) {
            DisplayDTO dto = new DisplayDTO();
            dto.setParamKey(param.getParamKey());
            dto.setParamValue(param.getParamValue());
            dto.setType(param.getType());
            if (StringUtils.equalsIgnoreCase(param.getType(), "file")
                    || FILE_PREVIEW.containsKey(param.getParamKey())) {
                dto.setType("file");
                dto.setFileName(FILE_PREVIEW.getOrDefault(param.getParamKey(), param.getParamValue()));
                dto.setFile(param.getParamValue());
            }
            result.add(dto);
        }
        return result;
    }

    public void save(List<DisplayDTO> requests, List<MultipartFile> files) {
        Map<String, MultipartFile> fileMap = new HashMap<>();
        if (CollectionUtils.isNotEmpty(files)) {
            for (MultipartFile file : files) {
                if (file == null || StringUtils.isBlank(file.getOriginalFilename())) {
                    continue;
                }
                // 前端命名：ui.icon,xxx.png
                String[] parts = StringUtils.split(file.getOriginalFilename(), ",", 2);
                if (parts.length >= 1) {
                    fileMap.put(parts[0], file);
                }
            }
        }

        if (CollectionUtils.isEmpty(requests)) {
            return;
        }

        for (DisplayDTO request : requests) {
            if (StringUtils.isBlank(request.getParamKey())) {
                continue;
            }
            if (BooleanUtils.isTrue(request.getOriginal()) && StringUtils.equalsIgnoreCase(request.getType(), "file")) {
                deleteParam(request.getParamKey());
                continue;
            }

            MultipartFile upload = fileMap.get(request.getParamKey());
            if (upload != null) {
                String originalName = upload.getOriginalFilename();
                String shortName = originalName;
                if (StringUtils.contains(originalName, ",")) {
                    shortName = StringUtils.substringAfter(originalName, ",");
                }
                String storageName = request.getParamKey() + ":" + shortName;
                try {
                    FileRequest fileRequest = new FileRequest();
                    fileRequest.setFolder(DefaultRepositoryDir.getSystemRootDir());
                    fileRequest.setFileName(storageName);
                    minioRepository.saveFile(upload, fileRequest);
                } catch (Exception e) {
                    throw new MSException("save display file error: " + request.getParamKey());
                }
                upsert(request.getParamKey(), storageName, "file");
            } else if (!StringUtils.equalsIgnoreCase(request.getType(), "file")) {
                upsert(request.getParamKey(), StringUtils.defaultString(request.getParamValue()),
                        StringUtils.defaultIfBlank(request.getType(), "text"));
            } else if (StringUtils.isNotBlank(request.getParamValue())
                    && !BooleanUtils.isTrue(request.getOriginal())) {
                // 保留已有文件配置
                upsert(request.getParamKey(), request.getParamValue(), "file");
            }
        }
    }

    public List<DisplayDTO> parseRequest(String requestJson) {
        if (StringUtils.isBlank(requestJson)) {
            return List.of();
        }
        return JSON.parseArray(requestJson, DisplayDTO.class);
    }

    private void upsert(String key, String value, String type) {
        SystemParameter param = new SystemParameter();
        param.setParamKey(key);
        param.setParamValue(value);
        param.setType(type);
        if (systemParameterMapper.selectByPrimaryKey(key) != null) {
            systemParameterMapper.updateByPrimaryKey(param);
        } else {
            systemParameterMapper.insert(param);
        }
    }

    private void deleteParam(String key) {
        if (systemParameterMapper.selectByPrimaryKey(key) != null) {
            systemParameterMapper.deleteByPrimaryKey(key);
        }
    }
}
