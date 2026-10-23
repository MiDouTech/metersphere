package io.metersphere.functional.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.EasyExcelFactory;
import com.alibaba.excel.enums.CellExtraTypeEnum;
import com.alibaba.excel.metadata.data.HyperlinkData;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import io.metersphere.functional.constants.FunctionalCaseTypeConstants;
import io.metersphere.functional.domain.*;
import io.metersphere.functional.dto.ExportTaskDTO;
import io.metersphere.functional.dto.response.FunctionalCaseImportResponse;
import io.metersphere.functional.excel.constants.FunctionalCaseImportFiled;
import io.metersphere.functional.excel.converter.FunctionalCaseExportConverter;
import io.metersphere.functional.excel.converter.FunctionalCaseExportConverterFactory;
import io.metersphere.functional.excel.domain.*;
import io.metersphere.functional.excel.handler.FunctionCaseMergeWriteHandler;
import io.metersphere.functional.excel.handler.FunctionCaseTemplateWriteHandler;
import io.metersphere.functional.excel.listener.FunctionalCaseCheckEventListener;
import io.metersphere.functional.excel.listener.FunctionalCaseImportEventListener;
import io.metersphere.functional.excel.listener.FunctionalCasePretreatmentListener;
import io.metersphere.functional.excel.validate.AbstractCustomFieldValidator;
import io.metersphere.functional.excel.validate.CustomFieldValidatorFactory;
import io.metersphere.functional.mapper.ExportTaskMapper;
import io.metersphere.functional.mapper.ExtFunctionalCaseCommentMapper;
import io.metersphere.functional.request.FunctionalCaseExportRequest;
import io.metersphere.functional.request.FunctionalCaseImportRequest;
import io.metersphere.functional.xmind.parser.XMindCaseParser;
import io.metersphere.plan.domain.TestPlanCaseExecuteHistory;
import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ExtBaseProjectVersionMapper;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.project.service.ProjectTemplateService;
import io.metersphere.sdk.constants.*;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.file.FileRequest;
import io.metersphere.sdk.util.*;
import io.metersphere.system.constants.ExportConstants;
import io.metersphere.system.domain.CustomFieldOption;
import io.metersphere.system.domain.SystemParameter;
import io.metersphere.system.domain.User;
import io.metersphere.system.dto.sdk.BaseTreeNode;
import io.metersphere.system.dto.sdk.SessionUser;
import io.metersphere.system.dto.sdk.TemplateCustomFieldDTO;
import io.metersphere.system.dto.sdk.TemplateDTO;
import io.metersphere.system.excel.domain.ExcelErrData;
import io.metersphere.system.excel.utils.EasyExcelExporter;
import io.metersphere.system.log.dto.LogDTO;
import io.metersphere.system.log.service.OperationLogService;
import io.metersphere.system.mapper.SystemParameterMapper;
import io.metersphere.system.mapper.UserMapper;
import io.metersphere.system.service.FileService;
import io.metersphere.system.service.NoticeSendService;
import io.metersphere.system.utils.ServiceUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author wx
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class FunctionalCaseFileService {


    @Resource
    private ProjectTemplateService projectTemplateService;
    @Resource
    private ExtBaseProjectVersionMapper extBaseProjectVersionMapper;

    @Resource
    private FunctionalCaseService functionalCaseService;
    @Resource
    private FunctionalCaseModuleService functionalCaseModuleService;
    @Resource
    private FunctionalCaseCustomFieldService functionalCaseCustomFieldService;
    private static final int EXPORT_CASE_MAX_COUNT = 2000;
    @Resource
    private ExtFunctionalCaseCommentMapper extFunctionalCaseCommentMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private FileService fileService;
    @Resource
    private FunctionalCaseLogService functionalCaseLogService;
    @Resource
    private SystemParameterMapper systemParameterMapper;
    @Resource
    private FunctionalCaseExportTaskService exportTaskService;
    @Resource
    private FunctionalCaseExportTempFiles exportTempFiles;
    @Resource
    private ExportTaskMapper exportTaskMapper;
    private static final String XLSX = "xlsx";
    private static final String ZIP = "zip";
    @Resource
    private OperationLogService operationLogService;
    @Resource
    private NoticeSendService noticeSendService;
    @Resource
    private UserMapper userMapper;
    @Resource
    private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @org.springframework.beans.factory.annotation.Value("${ms.import.write-timeout-seconds:120}")
    private int importWriteTimeoutSeconds = 120;

    /**
     * 下载excel导入模板
     *
     * @param projectId
     * @param response
     */
    public void downloadExcelTemplate(String projectId, HttpServletResponse response) {
        //获取当前项目下默认模板的自定义字段属性（导入校验用；下载模板仅展示固定 8 列含用例等级）
        List<TemplateCustomFieldDTO> customFields = getCustomFields(projectId);

        // 固定表头：用例ID|用例名称|所属模块|前置条件|步骤描述|预期结果|用例等级|备注
        List<List<String>> heads = getTemplateHead(projectId, customFields);

        FunctionalCaseExcelData caseExcelData = new FunctionalCaseExcelDataFactory().getFunctionalCaseExcelDataLocal();

        //默认字段+自定义字段的 options集合
        Map<String, List<String>> customFieldOptionsMap = getCustomFieldOptionsMap(customFields);
        Map<String, TemplateCustomFieldDTO> customFieldMap = customFields.stream().collect(Collectors.toMap(TemplateCustomFieldDTO::getFieldName, templateCustomFieldDTO -> templateCustomFieldDTO));

        //表头备注信息
        FunctionCaseTemplateWriteHandler handler = new FunctionCaseTemplateWriteHandler(heads, customFieldOptionsMap, customFieldMap);

        List<FunctionalCaseExcelData> functionalCaseExcelData = generateExportData();
        List<List<Object>> data = parseExcelData2List(heads, functionalCaseExcelData);

        new EasyExcelExporter(caseExcelData.getClass())
                .exportByCustomWriteHandler(response, heads, data, Translator.get("test_case_import_template_name"),
                        Translator.get("test_case_import_template_sheet"), handler);
    }


    private List<List<Object>> parseExcelData2List(List<List<String>> headListParams, List<FunctionalCaseExcelData> data) {
        List<List<Object>> result = new ArrayList<>();
        //转化excel头
        List<String> headList = new ArrayList<>();
        for (List<String> list : headListParams) {
            for (String head : list) {
                headList.add(head);
            }
        }

        FunctionalCaseImportFiled[] importFields = FunctionalCaseImportFiled.values();


        for (FunctionalCaseExcelData model : data) {
            List<Object> fields = new ArrayList<>();
            Map<String, Object> customDataMaps = Optional.ofNullable(model.getCustomData())
                    .orElse(new HashMap<>());
            Map<String, String> otherFieldMaps = Optional.ofNullable(model.getOtherFields())
                    .orElse(new HashMap<>());
            for (String head : headList) {
                boolean isSystemField = false;
                for (FunctionalCaseImportFiled importFiled : importFields) {
                    if (importFiled.getFiledLangMap().containsValue(head) && StringUtils.equals("name", importFiled.getValue()) && model.getHyperLinkName() != null) {
                        fields.add(model.getHyperLinkName());
                        isSystemField = true;
                        break;
                    }
                    if (importFiled.containsHead(head)) {
                        fields.add(importFiled.parseExcelDataValue(model));
                        isSystemField = true;
                        break;
                    }

                }
                if (!isSystemField) {
                    Object value = customDataMaps.get(head);
                    if (value == null) {
                        value = otherFieldMaps.get(head);
                    }
                    if (value == null) {
                        value = StringUtils.EMPTY;
                    }
                    fields.add(value);
                }
            }
            result.add(fields);
        }

        return result;
    }


    private List<FunctionalCaseExcelData> generateExportData() {
        List<FunctionalCaseExcelData> list = new ArrayList<>();
        StringBuilder path = new StringBuilder();
        for (int i = 1; i <= 4; i++) {
            path.append("/" + Translator.get("module") + i);
            FunctionalCaseExcelData testCaseDTO = new FunctionalCaseExcelData();
            testCaseDTO.setNum(StringUtils.EMPTY);
            testCaseDTO.setName(Translator.get("test_case") + i);
            testCaseDTO.setModule(path.toString());
            testCaseDTO.setPrerequisite(Translator.get("test_case_prerequisite"));
            testCaseDTO.setDescription(Translator.get("test_case_remark"));
            String textDescription = "";
            String expectedResult = "";
            for (int j = 1; j < 5; j++) {
                textDescription = textDescription + "[" + j + "]" + Translator.get("test_case_step_desc") + i + "\n";
                expectedResult = expectedResult + "[" + j + "]" + Translator.get("test_case_step_result") + i + "\n";
            }
            testCaseDTO.setTextDescription(textDescription);
            testCaseDTO.setExpectedResult(expectedResult);
            Map<String, Object> customData = new HashMap<>();
            customData.put(Translator.get("custom_field.functional_priority"), "P" + ((i - 1) % 4));
            testCaseDTO.setCustomData(customData);
            list.add(testCaseDTO);
        }
        return list;
    }

    public Map<String, List<String>> getCustomFieldOptionsMap(List<TemplateCustomFieldDTO> customFields) {
        Map<String, List<String>> returnMap = new HashMap<>();
        customFields.forEach(item -> {
            List<String> values = getOptionValues(Optional.ofNullable(item.getOptions()).orElse(new ArrayList<>()));
            returnMap.put(item.getFieldName(), values);
        });
        return returnMap;
    }

    private List<String> getOptionValues(List<CustomFieldOption> options) {
        List<String> values = new ArrayList<>();
        options.forEach(item -> {
            values.add(item.getText());
        });
        return values;
    }

    /**
     * 获取表头字段
     *
     * @param projectId
     * @param customFields
     * @return
     */
    private List<List<String>> getTemplateHead(String projectId, List<TemplateCustomFieldDTO> customFields) {
        List<List<String>> heads = new FunctionalCaseExcelDataFactory().getFunctionalCaseExcelDataLocal().getHead(customFields);
        return heads;
    }


    /**
     * 导入前校验excel模板
     *
     * @param request
     * @param file
     * @return
     */
    public FunctionalCaseImportResponse preCheckExcel(FunctionalCaseImportRequest request, MultipartFile file) {
        if (file == null) {
            throw new MSException(Translator.get("file_cannot_be_null"));
        }
        FunctionalCaseImportResponse response = new FunctionalCaseImportResponse();
        checkImportExcel(response, request, file);
        return response;
    }

    private void checkImportExcel(FunctionalCaseImportResponse response, FunctionalCaseImportRequest request, MultipartFile file) {
        try {
            //根据本地语言环境选择用哪种数据对象进行存放读取的数据
            Class clazz = new FunctionalCaseExcelDataFactory().getExcelDataByLocal();
            //获取当前项目默认模板的自定义字段
            List<TemplateCustomFieldDTO> customFields = getCustomFields(request.getProjectId());
            Set<ExcelMergeInfo> mergeInfoSet = new TreeSet<>();
            FunctionalLegacyImportService.stage("PREPROCESSING", 0);
            // 预处理，查询合并单元格信息
            EasyExcel.read(file.getInputStream(), null, new FunctionalCasePretreatmentListener(mergeInfoSet))
                    .extraRead(CellExtraTypeEnum.MERGE).sheet().doRead();
            FunctionalCaseCheckEventListener eventListener = new FunctionalCaseCheckEventListener(request, clazz, customFields, mergeInfoSet);
            FunctionalLegacyImportService.stage("PARSING", 0);
            EasyExcelFactory.read(file.getInputStream(), eventListener).sheet().doRead();
            response.setErrorMessages(eventListener.getErrList());
            response.setSuccessCount(eventListener.getList().size());
            response.setFailCount(eventListener.getErrList().size());
        } catch (Exception e) {
            LogUtils.error("checkImportExcel error", e);
            throw new MSException(Translator.get("check_import_excel_error"), e);
        }
    }

    public List<TemplateCustomFieldDTO> getCustomFields(String projectId) {
        TemplateDTO defaultTemplateDTO = projectTemplateService.getDefaultTemplateDTO(projectId, TemplateScene.FUNCTIONAL.name());
        return Optional.ofNullable(defaultTemplateDTO.getCustomFields()).orElse(new ArrayList<>());
    }

    /**
     * 导入前校验xmind格式
     *
     * @return FunctionalCaseImportResponse
     */
    public FunctionalCaseImportResponse preCheckXMind(FunctionalCaseImportRequest request, SessionUser user, MultipartFile multipartFile) {
        if (multipartFile == null) {
            throw new MSException(Translator.get("file_cannot_be_null"));
        }
        try {
            List<ExcelErrData<FunctionalCaseExcelData>> errList;
            FunctionalCaseImportResponse response = new FunctionalCaseImportResponse();
            //设置默认版本
            if (StringUtils.isEmpty(request.getVersionId())) {
                request.setVersionId(extBaseProjectVersionMapper.getDefaultVersion(request.getProjectId()));
            }
            Long lasePos = 0L;
            //获取当前项目默认模板的自定义字段
            List<TemplateCustomFieldDTO> customFields = getCustomFields(request.getProjectId());
            XMindCaseParser xMindParser = new XMindCaseParser(request, customFields, user, lasePos);
            errList = xMindParser.parse(multipartFile);
            response.setErrorMessages(errList);
            response.setSuccessCount(xMindParser.getList().size() + xMindParser.getUpdateList().size() + xMindParser.getCheckSuccessList().size());
            response.setFailCount(errList.size());
            xMindParser.clear();
            return response;
        } catch (Exception e) {
            LogUtils.error("checkImportExcel error", e);
            throw new MSException(Translator.get("check_import_excel_error"), e);
        }
    }


    /**
     * 导入excel
     *
     * @param request
     * @param user
     * @param file
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public FunctionalCaseImportResponse importExcel(FunctionalCaseImportRequest request, SessionUser user, MultipartFile file) {
        if (file == null) {
            throw new MSException(Translator.get("file_cannot_be_null"));
        }
        try {
            FunctionalCaseImportResponse response = new FunctionalCaseImportResponse();
            //设置默认版本
            if (StringUtils.isEmpty(request.getVersionId())) {
                request.setVersionId(extBaseProjectVersionMapper.getDefaultVersion(request.getProjectId()));
            }
            Long nextPos = functionalCaseService.getNextOrder(request.getProjectId());
            Long lasePos = nextPos + (ServiceUtils.POS_STEP * parseImportCount(request.getCount()));
            //根据本地语言环境选择用哪种数据对象进行存放读取的数据
            Class clazz = new FunctionalCaseExcelDataFactory().getExcelDataByLocal();
            //获取当前项目默认模板的自定义字段
            List<TemplateCustomFieldDTO> customFields = getCustomFields(request.getProjectId());
            Set<ExcelMergeInfo> mergeInfoSet = new TreeSet<>();
            FunctionalLegacyImportService.stage("PREPROCESSING", 0);
            // 预处理，查询合并单元格信息
            EasyExcel.read(file.getInputStream(), null, new FunctionalCasePretreatmentListener(mergeInfoSet))
                    .extraRead(CellExtraTypeEnum.MERGE).sheet().doRead();
            FunctionalCaseImportEventListener eventListener = new FunctionalCaseImportEventListener(request, clazz, customFields, mergeInfoSet, user, lasePos);
            eventListener.deferWrites();
            FunctionalLegacyImportService.stage("PARSING", 0);
            EasyExcelFactory.read(file.getInputStream(), eventListener).sheet().doRead();
            FunctionalLegacyImportService.stage("WRITING", eventListener.getSuccessCount());
            var writeTransaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            writeTransaction.setTimeout(importWriteTimeoutSeconds);
            writeTransaction.executeWithoutResult(status -> eventListener.writePrepared());
            response.setErrorMessages(eventListener.getErrList());
            response.setSuccessCount(eventListener.getSuccessCount());
            response.setFailCount(eventListener.getErrList().size());
            return response;
        } catch (Exception e) {
            LogUtils.error("checkImportExcel error", e);
            throw new MSException(Translator.get("check_import_excel_error"), e);
        }
    }

    public FunctionalCaseImportResponse importXMind(FunctionalCaseImportRequest request, SessionUser user, MultipartFile multipartFile) {
        if (multipartFile == null) {
            throw new MSException(Translator.get("file_cannot_be_null"));
        }
        try {
            List<ExcelErrData<FunctionalCaseExcelData>> errList;
            FunctionalCaseImportResponse response = new FunctionalCaseImportResponse();
            //设置默认版本
            if (StringUtils.isEmpty(request.getVersionId())) {
                request.setVersionId(extBaseProjectVersionMapper.getDefaultVersion(request.getProjectId()));
            }
            Long nextPos = functionalCaseService.getNextOrder(request.getProjectId());
            Long lasePos = nextPos + ((long) ServiceUtils.POS_STEP * parseImportCount(request.getCount()));
            //获取当前项目默认模板的自定义字段
            List<TemplateCustomFieldDTO> customFields = getCustomFields(request.getProjectId());
            XMindCaseParser xmindParser = new XMindCaseParser(request, customFields, user, lasePos);
            errList = xmindParser.parse(multipartFile);
            if (CollectionUtils.isEmpty(xmindParser.getList())
                    && CollectionUtils.isEmpty(xmindParser.getUpdateList())) {
                if (errList == null) {
                    errList = new ArrayList<>();
                }
                ExcelErrData excelErrData = new ExcelErrData(1, Translator.get("upload_fail") + "：" + Translator.get("upload_content_is_null"));
                errList.add(excelErrData);
            }
            xmindParser.saveData();
            response.setErrorMessages(errList);
            response.setSuccessCount(xmindParser.getList().size() + xmindParser.getUpdateList().size() + xmindParser.getCheckSuccessList().size());
            response.setFailCount(errList.size());
            xmindParser.clear();
            return response;
        } catch (Exception e) {
            LogUtils.error("checkImportExcel error", e);
            throw new MSException(Translator.get("check_import_excel_error"), e);
        }
    }

    private int parseImportCount(String count) {
        if (StringUtils.isBlank(count)) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(count));
        } catch (NumberFormatException e) {
            throw new MSException("导入数量格式不正确: " + count, e);
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public String export(String userId, FunctionalCaseExportRequest request, String orgId) {
        exportTaskService.authorize(request.getProjectId(), userId);
        validateExportFields(request);
        return exportTaskService.start(request, userId, XLSX,
                taskId -> {
                    User user = userMapper.selectByPrimaryKey(userId);
                    noticeSendService.setLanguage(user.getLanguage());
                    ExcelSnapshot snapshot = exportTaskService.readSnapshot(() -> snapshotExcel(request));
                    exportTaskService.saveSnapshot(taskId, snapshot.ids(), snapshot);
                    return snapshot;
                },
                (taskId, snapshot) -> exportFunctionalCaseZip(request, userId, orgId, taskId, snapshot));
    }

    public void validateExportFields(FunctionalCaseExportRequest request) {
        FunctionalCaseExportColumns allowed = getExportColumns(request.getProjectId());
        validateFields(request.getSystemFields(), allowed.getSystemColumns());
        validateFields(request.getCustomFields(), allowed.getCustomColumns());
        validateFields(request.getOtherFields(), allowed.getOtherColumns());
        if (request.getSystemFields().isEmpty() && request.getCustomFields().isEmpty() && request.getOtherFields().isEmpty()) {
            throw new MSException("请至少选择一个导出字段");
        }
    }

    private void validateFields(List<FunctionalCaseHeader> selected, Map<String, String> names) {
        if (selected == null || selected.size() > 200) throw new MSException("导出字段无效");
        Set<String> seen = new HashSet<>();
        for (FunctionalCaseHeader field : selected) {
            if (field == null || !names.containsKey(field.getId()) || !seen.add(field.getId())) throw new MSException("导出字段无效");
            field.setName(names.get(field.getId()));
        }
    }

    public List<String> validatedExportIds(FunctionalCaseExportRequest request) {
        List<String> selected = functionalCaseService.doSelectIds(request, request.getProjectId());
        if (CollectionUtils.isEmpty(selected)) throw new FunctionalCaseExportTaskService.EmptyExportException();
        List<String> ids = new ArrayList<>(new LinkedHashSet<>(selected));
        if (ids.size() > 100000) throw new MSException("单次最多导出 100000 条用例");
        SubListUtils.dealForSubList(ids, 1000, batch -> {
            List<FunctionalCase> cases = functionalCaseService.getCaseDataByIds(batch);
            if (cases.size() != batch.size() || cases.stream().anyMatch(item ->
                    !request.getProjectId().equals(item.getProjectId()) || Boolean.TRUE.equals(item.getDeleted()))) {
                throw new MSException("导出范围包含已删除或无权访问的用例");
            }
        });
        return ids;
    }

    private FunctionalCaseExportTaskService.Result exportFunctionalCaseZip(FunctionalCaseExportRequest request, String userId, String orgId, String taskId, ExcelSnapshot snapshot) {
        File tmpDir = null;
        try {
            User user = userMapper.selectByPrimaryKey(userId);
            noticeSendService.setLanguage(user.getLanguage());
            List<String> ids = snapshot.ids();
            tmpDir = exportTempFiles.create(taskId);
            List<File> batchExcels = generateCaseExportExcel(snapshot, tmpDir.getPath(), request, taskId);
            exportTaskService.checkActive(taskId);
            String fileType;
            if (batchExcels.size() > 1) {
                File zipFile = CompressUtils.zipFilesToPath(tmpDir.getPath() + File.separatorChar + request.getFileId() + ".zip", batchExcels);
                fileType = ZIP;
                exportTaskService.registerFile(taskId, fileType);
                uploadFileToMinio(fileType, zipFile, request.getFileId());
            } else {
                fileType = XLSX;
                exportTaskService.registerFile(taskId, fileType);
                uploadFileToMinio(fileType, batchExcels.getFirst(), request.getFileId());
            }
            LogDTO logDTO = functionalCaseLogService.exportExcelLog(request, "excel", userId, orgId);
            operationLogService.add(logDTO);
            return new FunctionalCaseExportTaskService.Result(fileType, ids.size());
        } catch (FunctionalCaseExportTaskService.EmptyExportException e) {
            throw e;
        } catch (Exception e) {
            throw new MSException("用例导出失败", e);
        } finally {
            exportTempFiles.cleanup(tmpDir);
        }
    }

    public List<ExportTask> getExportTasks(String projectId, String userId) {
        ExportTaskExample exportTaskExample = new ExportTaskExample();
        exportTaskExample.createCriteria().andTypeEqualTo(ExportConstants.ExportType.CASE.toString()).andStateEqualTo(ExportConstants.ExportState.PREPARED.toString())
                .andCreateUserEqualTo(userId).andProjectIdEqualTo(projectId);
        exportTaskExample.setOrderByClause("create_time desc");
        return exportTaskMapper.selectByExample(exportTaskExample);
    }

    public void updateExportTask(String state, String taskId, String fileType) {
        ExportTask exportTask = new ExportTask();
        exportTask.setState(state);
        exportTask.setFileType(fileType);
        exportTask.setId(taskId);
        exportTaskMapper.updateByPrimaryKeySelective(exportTask);
    }

    public void uploadFileToMinio(String fileType, File file, String fileId) throws Exception {
        if (file.length() > 64L * 1024 * 1024) throw new MSException("导出文件超过 64 MiB，请缩小范围分批导出");
        FileRequest fileRequest = new FileRequest();
        fileRequest.setFileName(fileId.concat(".").concat(fileType));
        fileRequest.setFolder(DefaultRepositoryDir.getExportExcelTempDir());
        fileRequest.setStorage(StorageType.MINIO.name());
        try (FileInputStream inputStream = new FileInputStream(file)) {
            fileService.upload(inputStream, fileRequest);
        }
    }

    private record ExcelBatch(List<List<Object>> rows, Map<Integer, Integer> merges) { }
    public record CaseRevision(String caseId, String versionId, Long updateTime) { }

    public List<CaseRevision> exportRevisions(List<String> ids) {
        Map<String, CaseRevision> revisions = new LinkedHashMap<>();
        SubListUtils.dealForSubList(ids, 1000, batch -> functionalCaseService.getCaseDataByIds(batch).forEach(item ->
                revisions.put(item.getId(), new CaseRevision(item.getId(), item.getVersionId(), item.getUpdateTime()))));
        return ids.stream().map(id -> {
            CaseRevision revision = revisions.get(id);
            if (revision == null) throw new MSException("导出范围已变化，请重新导出");
            return revision;
        }).toList();
    }

    private record ExcelSnapshot(List<String> ids, List<CaseRevision> revisions, List<List<String>> heads, Map<String, List<String>> options,
                                 Map<String, TemplateCustomFieldDTO> fields, List<ExcelBatch> batches) { }

    private ExcelSnapshot snapshotExcel(FunctionalCaseExportRequest request) {
        List<String> ids = validatedExportIds(request);
        List<List<String>> heads = getFunctionalCaseExportHeads(request);
        List<TemplateCustomFieldDTO> customFields = getCustomFields(request.getProjectId());
        Map<String, List<String>> options = getCustomFieldOptionsMap(customFields);
        Map<String, TemplateCustomFieldDTO> fields = customFields.stream().collect(Collectors.toMap(TemplateCustomFieldDTO::getFieldName, item -> item));
        SystemParameter parameter = systemParameterMapper.selectByPrimaryKey(ParamConstants.BASE.URL.getValue());
        Map<String, String> modules = getModuleMap(request.getProjectId());
        List<ExcelBatch> batches = new ArrayList<>();
        int[] bytes = {0};
        SubListUtils.dealForSubList(ids, EXPORT_CASE_MAX_COUNT, batch -> {
            Map<Integer, Integer> merges = new HashMap<>();
            List<FunctionalCaseExcelData> data = parseCaseData2ExcelData(batch, merges, request, customFields, modules,
                    parameter == null ? "" : parameter.getParamValue());
            List<List<Object>> rows = parseExcelData2List(heads, data);
            bytes[0] += JSON.toJSONString(rows).getBytes(StandardCharsets.UTF_8).length;
            if (bytes[0] > 8 * 1024 * 1024) throw new MSException("导出内容过大，请缩小范围分批导出");
            batches.add(new ExcelBatch(rows, merges));
        });
        return new ExcelSnapshot(ids, exportRevisions(ids), heads, options, fields, batches);
    }

    private List<File> generateCaseExportExcel(ExcelSnapshot snapshot, String directory, FunctionalCaseExportRequest request, String taskId) {
        List<File> files = new ArrayList<>();
        for (ExcelBatch batch : snapshot.batches()) {
            exportTaskService.checkActive(taskId);
            File file = new File(directory, request.getFileId() + (files.size() + 1) + ".xlsx");
            EasyExcel.write(file).head(snapshot.heads())
                    .registerWriteHandler(new FunctionCaseTemplateWriteHandler(snapshot.heads(), snapshot.options(), snapshot.fields()))
                    .registerWriteHandler(new FunctionCaseMergeWriteHandler(batch.merges(), snapshot.heads()))
                    .registerWriteHandler(FunctionCaseTemplateWriteHandler.getHorizontalWrapStrategy())
                    .excelType(ExcelTypeEnum.XLSX).sheet(Translator.get("test_case_import_template_sheet")).doWrite(batch.rows());
            files.add(file);
        }
        return files;
    }

    private List<FunctionalCaseExcelData> parseCaseData2ExcelData(List<String> ids, Map<Integer, Integer> rowMergeInfo, FunctionalCaseExportRequest request, List<TemplateCustomFieldDTO> customFields, Map<String, String> moduleMap, String url) {
        List<FunctionalCaseExcelData> list = new ArrayList<>();
        //基础信息
        Map<String, FunctionalCase> functionalCaseMap = functionalCaseService.copyBaseInfo(request.getProjectId(), ids);
        //大字段
        Map<String, FunctionalCaseBlob> functionalCaseBlobMap = functionalCaseService.copyBlobInfo(ids);
        //自定义字段
        Map<String, List<FunctionalCaseCustomField>> customFieldMap = functionalCaseCustomFieldService.getCustomFieldMapByCaseIds(ids);
        //用例评论
        Map<String, List<FunctionalCaseComment>> caseCommentMap = getCaseComment(ids);
        //执行评论
        Map<String, List<TestPlanCaseExecuteHistory>> executeCommentMap = getExecuteComment(ids);
        //评审评论
        Map<String, List<CaseReviewHistory>> reviewCommentMap = getReviewComment(ids);
        // 项目信息
        Project project = projectMapper.selectByPrimaryKey(request.getProjectId());

        ids.forEach(id -> {
            List<String> textDescriptionList = new ArrayList<>();
            List<String> expectedResultList = new ArrayList<>();
            //构建基本参数
            FunctionalCaseExcelData data = new FunctionalCaseExcelData();
            FunctionalCase functionalCase = functionalCaseMap.get(id);
            FunctionalCaseBlob functionalCaseBlob = functionalCaseBlobMap.get(id);
            //构建基本参数
            buildBaseField(data, functionalCase, functionalCaseBlob, moduleMap, textDescriptionList, expectedResultList, url, project);
            //构建自定义字段
            buildExportCustomField(customFields, customFieldMap.getOrDefault(id, List.of()), data, request);
            //构建其他字段
            buildExportOtherField(functionalCase, data, caseCommentMap, executeCommentMap, reviewCommentMap, request);
            validateExportTextField(data);
            if (CollectionUtils.isNotEmpty(textDescriptionList) && request.getIsMerge()) {
                // 如果有多条步骤则添加多条数据，之后合并单元格
                buildExportMergeData(rowMergeInfo, list, textDescriptionList, expectedResultList, data);
            } else {
                if (CollectionUtils.isNotEmpty(textDescriptionList)) {
                    data.setTextDescription(parseData(textDescriptionList));
                    data.setExpectedResult(parseData(expectedResultList));
                }
                list.add(data);
            }
        });

        return list;
    }

    /**
     * 处理单行格式
     *
     * @param list
     * @return
     */
    private String parseData(List<String> list) {
        String result = "";
        for (int i = 0; i < list.size(); i++) {
            result += "[" + (i + 1) + "]" + list.get(i) + "\n";
        }
        return result;
    }

    /**
     * 构建基本参数
     *
     * @param data
     * @param functionalCase
     * @param functionalCaseBlob
     */
    private void buildBaseField(FunctionalCaseExcelData data, FunctionalCase functionalCase, FunctionalCaseBlob functionalCaseBlob, Map<String, String> moduleMap,
                                List<String> textDescriptionList, List<String> expectedResultList, String url, Project project) {
        data.setNum(functionalCase.getNum().toString());
        data.setModule(moduleMap.get(functionalCase.getModuleId()));
        //构建步骤
        buildExportStep(data, functionalCaseBlob, functionalCase.getCaseEditType(), textDescriptionList, expectedResultList);
        data.setPrerequisite(parseHtml(new String(functionalCaseBlob.getPrerequisite() == null ? new byte[0] : functionalCaseBlob.getPrerequisite(), StandardCharsets.UTF_8)));
        //备注
        data.setDescription(parseHtml(new String(functionalCaseBlob.getDescription() == null ? new byte[0] : functionalCaseBlob.getDescription(), StandardCharsets.UTF_8)));
        //标签
        data.setTags(JSON.toJSONString(functionalCase.getTags()));
        data.setCaseEditType(functionalCase.getCaseEditType());
        // 设置超链接
        WriteCellData<String> hyperlink = new WriteCellData<>(functionalCase.getName());
        data.setHyperLinkName(hyperlink);
        HyperlinkData hyperlinkData = new HyperlinkData();
        hyperlink.setHyperlinkData(hyperlinkData);

        WriteFont writeFont = new WriteFont();
        writeFont.setUnderline(Font.U_SINGLE);
        writeFont.setColor(IndexedColors.BLUE.getIndex());
        WriteCellStyle writeCellStyle = new WriteCellStyle();
        writeCellStyle.setWriteFont(writeFont);
        hyperlink.setWriteCellStyle(writeCellStyle);
        hyperlinkData.setAddress(url + "#/case-management/featureCase?id=" + functionalCase.getId() +
                "&pId=" + functionalCase.getProjectId() + "&orgId=" + project.getOrganizationId());
        hyperlinkData.setHyperlinkType(HyperlinkData.HyperlinkType.URL);
    }


    /**
     * 合并单元格
     *
     * @param rowMergeInfo
     * @param list
     * @param textDescriptionList
     * @param expectedResultList
     * @param data
     */
    @NotNull
    private void buildExportMergeData(Map<Integer, Integer> rowMergeInfo, List<FunctionalCaseExcelData> list, List<String> textDescriptionList, List<String> expectedResultList, FunctionalCaseExcelData data) {
        for (int i = 0; i < textDescriptionList.size(); i++) {
            FunctionalCaseExcelData excelData;
            if (i == 0) {
                // 第一行存全量元素
                excelData = data;
                if (textDescriptionList.size() > 1) {
                    // 保存合并单元格的下标和数量
                    rowMergeInfo.put(list.size() + 1, textDescriptionList.size());
                }
            } else {
                // 之后的行只存步骤
                excelData = new FunctionalCaseExcelData();
            }
            excelData.setTextDescription(textDescriptionList.get(i));
            excelData.setExpectedResult(expectedResultList.get(i));
            list.add(excelData);
        }
    }

    private void validateExportTextField(FunctionalCaseExcelData data) {
        data.setPrerequisite(validateExportText(data.getPrerequisite()));
        data.setDescription(validateExportText(data.getDescription()));
        data.setTextDescription(validateExportText(data.getTextDescription()));
        data.setExpectedResult(validateExportText(data.getExpectedResult()));
    }


    /**
     * 构建其他字段
     *
     * @param functionalCase
     * @param data
     * @param caseCommentMap
     * @param executeCommentMap
     * @param reviewCommentMap
     * @param request
     */
    private void buildExportOtherField(FunctionalCase functionalCase, FunctionalCaseExcelData data, Map<String, List<FunctionalCaseComment>> caseCommentMap, Map<String, List<TestPlanCaseExecuteHistory>> executeCommentMap, Map<String, List<CaseReviewHistory>> reviewCommentMap, FunctionalCaseExportRequest request) {
        if (CollectionUtils.isEmpty(request.getOtherFields())) {
            return;
        }
        List<FunctionalCaseHeader> otherFields = request.getOtherFields();
        List<String> keys = otherFields.stream().map(FunctionalCaseHeader::getId).toList();
        Map<String, FunctionalCaseExportConverter> converterMaps = FunctionalCaseExportConverterFactory.getConverters(keys, request.getProjectId());
        HashMap<String, String> other = new HashMap<>();
        otherFields.forEach(header -> {
            FunctionalCaseExportConverter converter = converterMaps.get(header.getId());
            if (converter != null) {
                other.put(header.getName(), converter.parse(functionalCase, caseCommentMap, executeCommentMap, reviewCommentMap));
            } else {
                other.put(header.getName(), StringUtils.EMPTY);
            }
        });
        data.setOtherFields(other);
    }


    /**
     * 评审评论
     *
     * @param ids
     * @return
     */
    private Map<String, List<CaseReviewHistory>> getReviewComment(List<String> ids) {
        List<CaseReviewHistory> reviewHistories = extFunctionalCaseCommentMapper.getReviewComment(ids);
        Map<String, List<CaseReviewHistory>> reviewHistoryMap = reviewHistories.stream().collect(Collectors.groupingBy(CaseReviewHistory::getCaseId));
        return reviewHistoryMap;
    }

    /**
     * 执行评论
     *
     * @param ids
     * @return
     */
    private Map<String, List<TestPlanCaseExecuteHistory>> getExecuteComment(List<String> ids) {
        List<TestPlanCaseExecuteHistory> historyList = extFunctionalCaseCommentMapper.getExecuteComment(ids);
        Map<String, List<TestPlanCaseExecuteHistory>> commentMap = historyList.stream().collect(Collectors.groupingBy(TestPlanCaseExecuteHistory::getCaseId));
        return commentMap;
    }

    /**
     * 用例评论
     *
     * @param ids
     * @return
     */
    private Map<String, List<FunctionalCaseComment>> getCaseComment(List<String> ids) {
        List<FunctionalCaseComment> functionalCaseComments = extFunctionalCaseCommentMapper.getCaseComment(ids);
        Map<String, List<FunctionalCaseComment>> commentMap = functionalCaseComments.stream().collect(Collectors.groupingBy(FunctionalCaseComment::getCaseId));
        return commentMap;
    }

    /**
     * 构建自定义字段
     *
     * @param templateCustomFields
     * @param functionalCaseCustomFields
     * @param data
     * @param request
     */
    private void buildExportCustomField(List<TemplateCustomFieldDTO> templateCustomFields, List<FunctionalCaseCustomField> functionalCaseCustomFields, FunctionalCaseExcelData data, FunctionalCaseExportRequest request) {
        HashMap<String, AbstractCustomFieldValidator> customFieldValidatorMap = CustomFieldValidatorFactory.getValidatorMap(request.getProjectId());
        Map<String, TemplateCustomFieldDTO> customFieldsMap = templateCustomFields.stream().collect(Collectors.toMap(TemplateCustomFieldDTO::getFieldId, i -> i));
        Map<String, String> caseFieldvalueMap = functionalCaseCustomFields.stream().collect(Collectors.toMap(FunctionalCaseCustomField::getFieldId, FunctionalCaseCustomField::getValue));
        Map<String, Object> map = new HashMap<>();
        customFieldsMap.forEach((k, v) -> {
            if (caseFieldvalueMap.containsKey(k)) {
                AbstractCustomFieldValidator customFieldValidator = customFieldValidatorMap.get(v.getType());
                if (customFieldValidator.isKVOption) {
                    if (!v.getInternal()) {
                        // 这里如果填的是选项值，替换成选项ID，保存
                        map.put(v.getFieldName(), customFieldValidator.parse2Value(caseFieldvalueMap.get(k), v));
                    } else {
                        map.put(Translator.get("custom_field.functional_priority"), caseFieldvalueMap.get(k));
                    }
                } else {
                    map.put(v.getFieldName(), caseFieldvalueMap.get(k));
                }
            }
        });
        data.setCustomData(map);
    }


    private String validateExportText(String textValue) {
        // poi 导出的单个单元格最大字符数量为 32767 ，这里添加校验提示
        int maxLength = 32767;
        if (StringUtils.isNotBlank(textValue) && textValue.length() > maxLength) {
            return String.format(Translator.get("case_export_text_validate_tip"), maxLength);
        }
        return textValue;
    }


    /**
     * 构建步骤单元格
     *
     * @param data
     * @param functionalCaseBlob
     * @param caseEditType
     * @param textDescriptionList
     * @param expectedResultList
     */
    private void buildExportStep(FunctionalCaseExcelData data, FunctionalCaseBlob functionalCaseBlob, String caseEditType, List<String> textDescriptionList, List<String> expectedResultList) {
        if (StringUtils.equals(caseEditType, FunctionalCaseTypeConstants.CaseEditType.TEXT.name())) {
            data.setTextDescription(parseHtml(new String(functionalCaseBlob.getTextDescription() == null ? new byte[0] : functionalCaseBlob.getTextDescription(), StandardCharsets.UTF_8)));
            data.setExpectedResult(parseHtml(new String(functionalCaseBlob.getExpectedResult() == null ? new byte[0] : functionalCaseBlob.getExpectedResult(), StandardCharsets.UTF_8)));
        } else {
            String steps = new String(functionalCaseBlob.getSteps() == null ? new byte[0] : functionalCaseBlob.getSteps(), StandardCharsets.UTF_8);
            List jsonArray = new ArrayList();
            try {
                jsonArray = JSON.parseArray(steps);
            } catch (Exception e) {
                if (steps.contains("null") && !steps.contains("\"null\"")) {
                    steps = steps.replace("null", "\"\"");
                    jsonArray = JSON.parseArray(steps);
                }
            }
            for (int j = 0; j < jsonArray.size(); j++) {
                // 将步骤存储起来，之后生成多条数据，再合并单元格
                Map item = (Map) jsonArray.get(j);
                String textDescription = Optional.ofNullable(item.get("desc")).orElse(StringUtils.EMPTY).toString();
                String expectedResult = Optional.ofNullable(item.get("result")).orElse(StringUtils.EMPTY).toString();
                if (StringUtils.isNotBlank(textDescription) || StringUtils.isNotBlank(expectedResult)) {
                    textDescriptionList.add(textDescription);
                    expectedResultList.add(expectedResult);
                }
            }
        }
    }

    public String parseHtml(String html) {
        return html.replaceAll("</?p[^>]*>", "");
    }

    /**
     * 获取模块map
     *
     * @param projectId
     * @return
     */
    private Map<String, String> getModuleMap(String projectId) {
        List<BaseTreeNode> moduleTree = functionalCaseModuleService.getTree(projectId);
        Map<String, String> moduleMap = buildModuleMap(moduleTree, new HashMap());
        return moduleMap;
    }

    private Map<String, String> buildModuleMap(List<BaseTreeNode> moduleTree, Map<String, String> moduleMap) {
        for (BaseTreeNode treeNode : moduleTree) {
            moduleMap.put(treeNode.getId(), treeNode.getPath());
            if (CollectionUtils.isNotEmpty(treeNode.getChildren())) {
                buildModuleMap(treeNode.getChildren(), moduleMap);
            }
        }
        return moduleMap;
    }


    /**
     * 获取导出表头
     *
     * @param request
     * @return
     */
    private List<List<String>> getFunctionalCaseExportHeads(FunctionalCaseExportRequest request) {
        List<List<String>> headList = new ArrayList<>() {
            @Serial
            private static final long serialVersionUID = 5726921174161850104L;

            {
                addAll(request.getSystemFields()
                        .stream()
                        .map(item -> Arrays.asList(item.getName()))
                        .collect(Collectors.toList()));
                addAll(request.getCustomFields()
                        .stream()
                        .map(item -> Arrays.asList(item.getName()))
                        .collect(Collectors.toList()));
                addAll(request.getOtherFields()
                        .stream()
                        .map(item -> Arrays.asList(item.getName()))
                        .collect(Collectors.toList()));
            }
        };
        List<FunctionalCaseHeader> textDescription = request.getSystemFields().stream().filter(item -> StringUtils.equals(item.getId(), "text_description")).toList();
        if (CollectionUtils.isNotEmpty(textDescription)) {
            headList.add(Arrays.asList(Translator.get("case.export.columns.case_edit_type")));
        }
        return headList;
    }

    public FunctionalCaseExportColumns getExportColumns(String projectId) {
        FunctionalCaseExportColumns functionalCaseExportColumns = new FunctionalCaseExportColumns();
        // 表头自定义字段
        List<TemplateCustomFieldDTO> headerCustomFields = getCustomFields(projectId);
        functionalCaseExportColumns.initCustomColumns(headerCustomFields);
        return functionalCaseExportColumns;
    }

    public ResponseEntity<org.springframework.core.io.InputStreamResource> downloadFile(String projectId, String fileId, String userId) {
        ExportTaskDTO task = exportTaskService.forDownload(projectId, fileId, userId);
        Project project = projectMapper.selectByPrimaryKey(projectId);
        FileRequest fileRequest = new FileRequest();
        fileRequest.setFileName(task.getFileId() + "." + task.getFileType());
        fileRequest.setFolder(DefaultRepositoryDir.getExportExcelTempDir());
        fileRequest.setStorage(StorageType.MINIO.name());
        try {
            String contentType = switch (task.getFileType()) {
                case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                case "zip" -> "application/zip";
                default -> "application/octet-stream";
            };
            String name = "Metersphere_case_" + project.getName() + "." + task.getFileType();
            var stream = fileService.getFileAsStream(fileRequest);
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, org.springframework.http.ContentDisposition.attachment()
                            .filename(name, StandardCharsets.UTF_8).build().toString())
                    .body(new org.springframework.core.io.InputStreamResource(stream));
        } catch (Exception exception) {
            LogUtils.error("Export download failed, taskId=" + task.getTaskId(), exception);
            throw new MSException("导出文件暂时无法下载，请稍后重试");
        }
    }

    private List<ExportTask> getExportTasksByFileId(String projectId, String userId, String fileId) {
        ExportTaskExample exportTaskExample = new ExportTaskExample();
        exportTaskExample.createCriteria().andTypeEqualTo(ExportConstants.ExportType.CASE.toString()).andStateEqualTo(ExportConstants.ExportState.SUCCESS.toString())
                .andCreateUserEqualTo(userId).andProjectIdEqualTo(projectId).andFileIdEqualTo(fileId);
        exportTaskExample.setOrderByClause("create_time desc");
        return exportTaskMapper.selectByExample(exportTaskExample);
    }

    public void stopExport(String taskId, String userId) {
        exportTaskService.stop(taskId, userId);
    }

    public ExportTaskDTO checkExportTask(String projectId, String userId) {
        ExportTaskDTO exportTaskDTO = new ExportTaskDTO();
        List<ExportTask> exportTasks = getExportTasks(projectId, userId);
        if (CollectionUtils.isNotEmpty(exportTasks)) {
            exportTaskDTO.setFileId(exportTasks.getFirst().getFileId());
            exportTaskDTO.setTaskId(exportTasks.getFirst().getId());
            return exportTaskDTO;
        } else {
            return exportTaskDTO;
        }
    }
}
