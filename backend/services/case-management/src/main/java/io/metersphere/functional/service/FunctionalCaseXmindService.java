package io.metersphere.functional.service;

import io.metersphere.functional.domain.ExportTask;
import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.domain.FunctionalCaseBlob;
import io.metersphere.functional.domain.FunctionalCaseCustomField;
import io.metersphere.functional.request.FunctionalCaseExportRequest;
import io.metersphere.functional.xmind.domain.FunctionalCaseXmindDTO;
import io.metersphere.functional.xmind.domain.FunctionalCaseXmindData;
import io.metersphere.functional.xmind.utils.XmindExportUtil;
import io.metersphere.sdk.constants.LocalRepositoryDir;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.sdk.util.Translator;
import io.metersphere.system.constants.ExportConstants;
import io.metersphere.system.domain.User;
import io.metersphere.system.dto.sdk.BaseTreeNode;
import io.metersphere.system.dto.sdk.TemplateCustomFieldDTO;
import io.metersphere.system.log.dto.LogDTO;
import io.metersphere.system.log.service.OperationLogService;
import io.metersphere.system.mapper.UserMapper;
import io.metersphere.system.service.NoticeSendService;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author wx
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class FunctionalCaseXmindService {

    public static final String template = "/template/template.json";

    @Resource
    private FunctionalCaseFileService functionalCaseFileService;
    @Resource
    private FunctionalCaseService functionalCaseService;
    @Resource
    private FunctionalCaseCustomFieldService functionalCaseCustomFieldService;
    @Resource
    private FunctionalCaseModuleService functionalCaseModuleService;
    private static final String EXPORT_CASE_TMP_DIR = "tmp";
    @Resource
    private FunctionalCaseExportTaskService exportTaskService;
    @Resource
    private FunctionalCaseLogService functionalCaseLogService;
    private static final String XMIND = "xmind";
    @Resource
    private OperationLogService operationLogService;
    @Resource
    private NoticeSendService noticeSendService;
    @Resource
    private UserMapper userMapper;

    public void downloadXmindTemplate(String projectId, HttpServletResponse response) {
        List<TemplateCustomFieldDTO> customFields = functionalCaseFileService.getCustomFields(projectId);
        //默认字段+自定义字段的 options集合
        Map<String, List<String>> customFieldOptionsMap = functionalCaseFileService.getCustomFieldOptionsMap(customFields);
        try (InputStream stream = FunctionalCaseXmindService.class.getResourceAsStream(template)) {
            FunctionalCaseXmindData functionalCaseXmindData = JSON.parseObject(stream, FunctionalCaseXmindData.class);
            setTemplateCustomFields(functionalCaseXmindData.getChildren(), customFields);

            XmindExportUtil.downloadTemplate(response, functionalCaseXmindData, true, customFieldOptionsMap);

        } catch (Exception e) {
            LogUtils.error(e.getMessage());
            throw new MSException(Translator.get("download_template_failed"));
        }
    }

    private void setTemplateCustomFields(List<FunctionalCaseXmindData> children, List<TemplateCustomFieldDTO> customFields) {
        if (CollectionUtils.isNotEmpty(children)) {
            children.forEach(data -> {
                data.getFunctionalCaseList().forEach(item -> {
                    item.setTemplateCustomFieldDTOList(customFields);
                });
                if (CollectionUtils.isNotEmpty(data.getChildren())) {
                    setTemplateCustomFields(data.getChildren(), customFields);
                }
            });
        }
    }

    /**
     * 导出xmind
     *
     * @param request
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public String exportFunctionalCaseXmind(FunctionalCaseExportRequest request, String userId, String orgId) {
        exportTaskService.authorize(request.getProjectId(), userId);
        functionalCaseFileService.validateExportFields(request);
        return exportTaskService.start(request, userId, XMIND,
                taskId -> exportXmind(request, userId, orgId, taskId));
    }

    private FunctionalCaseExportTaskService.Result exportXmind(FunctionalCaseExportRequest request, String userId, String orgId, String taskId) {
        File tmpFile = null;
        try {
            XmindSnapshot snapshot = exportTaskService.readSnapshot(() -> {
                List<String> ids = functionalCaseFileService.validatedExportIds(request);
                return new XmindSnapshot(ids, buildXmindData(ids, request), functionalCaseFileService.getCustomFields(request.getProjectId()));
            });
            List<String> ids = snapshot.ids();
            exportTaskService.saveSnapshot(taskId, ids, snapshot);
            User user = userMapper.selectByPrimaryKey(userId);
            noticeSendService.setLanguage(user.getLanguage());
            FunctionalCaseXmindData xmindData = snapshot.data();
            File dir = new File(LocalRepositoryDir.getSystemTempDir());
            if (!dir.exists() && !dir.mkdirs()) throw new MSException("无法创建导出临时文件");
            tmpFile = new File(dir, EXPORT_CASE_TMP_DIR + "_" + IDGenerator.nextStr() + ".xmind");
            List<TemplateCustomFieldDTO> templateCustomFields = snapshot.fields();
            XmindExportUtil.export(xmindData, request, tmpFile, templateCustomFields);
            exportTaskService.checkActive(taskId);
            exportTaskService.registerFile(taskId, XMIND);
            functionalCaseFileService.uploadFileToMinio(XMIND, tmpFile, request.getFileId());
            LogDTO logDTO = functionalCaseLogService.exportExcelLog(request, "xmind", userId, orgId);
            operationLogService.add(logDTO);
            return new FunctionalCaseExportTaskService.Result(XMIND, ids.size());
        } catch (FunctionalCaseExportTaskService.EmptyExportException e) {
            throw e;
        } catch (Exception e) {
            throw new MSException("用例导出失败", e);
        } finally {
            if (tmpFile != null) {
                try { FileUtils.delete(tmpFile); }
                catch (Exception cleanup) { LogUtils.error("Export temporary file cleanup failed, taskId=" + taskId, cleanup); }
            }
        }
    }

    private record XmindSnapshot(List<String> ids, FunctionalCaseXmindData data, List<TemplateCustomFieldDTO> fields) { }

    private FunctionalCaseXmindData buildXmindData(List<String> ids, FunctionalCaseExportRequest request) {
        FunctionalCaseXmindData xmindData = new FunctionalCaseXmindData();
        xmindData.setModuleId("MODULE");
        xmindData.setModuleName("MODULE");
        //基础信息
        List<FunctionalCase> caseList = functionalCaseService.getCaseDataByIds(ids);
        //大字段
        Map<String, FunctionalCaseBlob> functionalCaseBlobMap = functionalCaseService.copyBlobInfo(ids);
        //自定义字段
        Map<String, List<FunctionalCaseCustomField>> customFieldMap = functionalCaseCustomFieldService.getCustomFieldMapByCaseIds(ids);


        Map<String, List<FunctionalCase>> moduleCaseMap = caseList.stream().collect(Collectors.groupingBy(FunctionalCase::getModuleId));
        List<BaseTreeNode> tree = functionalCaseModuleService.getTree(request.getProjectId());

        for (Map.Entry<String, List<FunctionalCase>> entry : moduleCaseMap.entrySet()) {
            String moduleId = entry.getKey();
            List<FunctionalCase> dataList = entry.getValue();
            List<FunctionalCaseXmindDTO> dtos = buildXmindDTO(dataList, functionalCaseBlobMap, customFieldMap);
            LinkedList<BaseTreeNode> returnList = new LinkedList<>();
            LinkedList<BaseTreeNode> modulePathDataList = getModuleById(moduleId, tree, returnList);
            xmindData.setItem(modulePathDataList, dtos);
        }

        return xmindData;

    }

    private LinkedList<BaseTreeNode> getModuleById(String moduleId, List<BaseTreeNode> tree, LinkedList<BaseTreeNode> returnList) {

        for (BaseTreeNode baseTreeNode : tree) {
            if (StringUtils.equals(baseTreeNode.getId(), moduleId)) {
                BaseTreeNode node = new BaseTreeNode();
                node.setId(baseTreeNode.getId());
                node.setName(baseTreeNode.getName());
                returnList.addFirst(node);
                return returnList;
            } else {
                List<BaseTreeNode> children = baseTreeNode.getChildren();
                if (CollectionUtils.isNotEmpty(children)) {
                    LinkedList<BaseTreeNode> result = getModuleById(moduleId, children, returnList);
                    if (CollectionUtils.isNotEmpty(result)) {
                        BaseTreeNode node = new BaseTreeNode();
                        node.setId(baseTreeNode.getId());
                        node.setName(baseTreeNode.getName());
                        returnList.addFirst(node);
                        return returnList;
                    }
                }
            }
        }
        return returnList;
    }


    private List<FunctionalCaseXmindDTO> buildXmindDTO(List<FunctionalCase> dataList, Map<String, FunctionalCaseBlob> functionalCaseBlobMap, Map<String, List<FunctionalCaseCustomField>> customFieldMap) {
        List<FunctionalCaseXmindDTO> caseXmindDTOS = new ArrayList<>();
        dataList.forEach(item -> {
            FunctionalCaseBlob functionalCaseBlob = functionalCaseBlobMap.get(item.getId());
            List<FunctionalCaseCustomField> customFields = customFieldMap.get(item.getId());
            FunctionalCaseXmindDTO dto = new FunctionalCaseXmindDTO();
            dto.setId(item.getId());
            dto.setNum(item.getNum().toString());
            dto.setProjectId(item.getProjectId());
            dto.setName(item.getName());
            dto.setTags(JSON.toJSONString(item.getTags()));
            dto.setCaseEditType(item.getCaseEditType());
            dto.setSteps(new String(functionalCaseBlob.getSteps() == null ? new byte[0] : functionalCaseBlob.getSteps(), StandardCharsets.UTF_8));
            dto.setTextDescription(functionalCaseFileService.parseHtml(new String(functionalCaseBlob.getTextDescription() == null ? new byte[0] : functionalCaseBlob.getTextDescription(), StandardCharsets.UTF_8)));
            dto.setExpectedResult(functionalCaseFileService.parseHtml(new String(functionalCaseBlob.getExpectedResult() == null ? new byte[0] : functionalCaseBlob.getExpectedResult(), StandardCharsets.UTF_8)));
            dto.setPrerequisite(functionalCaseFileService.parseHtml(new String(functionalCaseBlob.getPrerequisite() == null ? new byte[0] : functionalCaseBlob.getPrerequisite(), StandardCharsets.UTF_8)));
            dto.setDescription(functionalCaseFileService.parseHtml(new String(functionalCaseBlob.getDescription() == null ? new byte[0] : functionalCaseBlob.getDescription(), StandardCharsets.UTF_8)));
            dto.setCustomFieldDTOList(customFields);
            caseXmindDTOS.add(dto);
        });
        return caseXmindDTOS;
    }
}
