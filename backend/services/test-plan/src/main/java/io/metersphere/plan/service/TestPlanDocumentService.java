package io.metersphere.plan.service;

import io.metersphere.plan.domain.TestPlan;
import io.metersphere.plan.domain.TestPlanDocument;
import io.metersphere.plan.dto.request.TestPlanDocumentSaveRequest;
import io.metersphere.plan.dto.response.TestPlanDocumentResponse;
import io.metersphere.plan.mapper.TestPlanDocumentMapper;
import io.metersphere.plan.mapper.TestPlanMapper;
import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.Translator;
import io.metersphere.system.dto.sdk.SessionUser;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@Transactional(rollbackFor = Exception.class)
public class TestPlanDocumentService {

    private static final String DEFAULT_CONTENT_TYPE = "RICH_TEXT";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DOC_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Resource
    private TestPlanDocumentMapper testPlanDocumentMapper;
    @Resource
    private TestPlanMapper testPlanMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private io.metersphere.plan.hub.service.DefaultHubPlanSyncService defaultHubPlanSyncService;

    public TestPlanDocumentResponse getDocument(String testPlanId) {
        TestPlan testPlan = checkAndGetTestPlan(testPlanId);
        TestPlanDocument document = testPlanDocumentMapper.selectByTestPlanId(testPlanId);

        TestPlanDocumentResponse response = new TestPlanDocumentResponse();
        response.setTestPlanId(testPlanId);
        response.setTemplateMeta(buildTemplateMeta(testPlan));

        if (document == null) {
            response.setExists(false);
            response.setContent(StringUtils.EMPTY);
            response.setContentType(DEFAULT_CONTENT_TYPE);
            return response;
        }

        response.setExists(true);
        response.setContent(document.getContent());
        response.setContentType(document.getContentType());
        response.setUpdateTime(document.getUpdateTime());
        response.setUpdateUser(document.getUpdateUser());
        return response;
    }

    public TestPlanDocumentResponse saveDocument(String testPlanId, TestPlanDocumentSaveRequest request) {
        TestPlan testPlan = checkAndGetTestPlan(testPlanId);
        String userId = SessionUtils.getUserId();
        long now = System.currentTimeMillis();
        String contentType = StringUtils.defaultIfBlank(request.getContentType(), DEFAULT_CONTENT_TYPE);

        TestPlanDocument existing = testPlanDocumentMapper.selectByTestPlanId(testPlanId);
        if (existing == null) {
            TestPlanDocument document = new TestPlanDocument();
            document.setId(IDGenerator.nextStr());
            document.setTestPlanId(testPlanId);
            document.setProjectId(testPlan.getProjectId());
            document.setContent(StringUtils.defaultString(request.getContent()));
            document.setContentType(contentType);
            document.setCreateTime(now);
            document.setUpdateTime(now);
            document.setCreateUser(userId);
            document.setUpdateUser(userId);
            testPlanDocumentMapper.insert(document);
            existing = document;
        } else {
            existing.setContent(StringUtils.defaultString(request.getContent()));
            existing.setContentType(contentType);
            existing.setUpdateTime(now);
            existing.setUpdateUser(userId);
            testPlanDocumentMapper.updateByPrimaryKeySelective(existing);
        }

        TestPlanDocumentResponse response = new TestPlanDocumentResponse();
        response.setTestPlanId(testPlanId);
        response.setExists(true);
        response.setContent(existing.getContent());
        response.setContentType(existing.getContentType());
        response.setUpdateTime(existing.getUpdateTime());
        response.setUpdateUser(existing.getUpdateUser());
        response.setTemplateMeta(buildTemplateMeta(testPlan));
        try {
            defaultHubPlanSyncService.syncPlanUpsert(testPlan.getProjectId(), testPlanId, userId);
        } catch (Exception e) {
            io.metersphere.sdk.util.LogUtils.error("default hub plan document sync failed, planId=" + testPlanId, e);
        }
        return response;
    }

    /**
     * 导出 HTML 文档（产品确认可导出；本期 HTML，Word/PDF 二期）
     */
    public ResponseEntity<byte[]> exportDocumentHtml(String testPlanId) {
        TestPlan testPlan = checkAndGetTestPlan(testPlanId);
        TestPlanDocument document = testPlanDocumentMapper.selectByTestPlanId(testPlanId);
        String body = document == null ? StringUtils.EMPTY : StringUtils.defaultString(document.getContent());
        String title = StringUtils.defaultIfBlank(testPlan.getName(), "test-plan-document");
        String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/><title>"
                + escapeHtml(title)
                + "</title><style>body{font-family:sans-serif;padding:24px;line-height:1.6}"
                + "table{border-collapse:collapse;width:100%}td,th{border:1px solid #ddd;padding:8px}</style>"
                + "</head><body>"
                + body
                + "</body></html>";
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        String filename = "test-plan-" + testPlanId + "-" + LocalDate.now().format(DOC_DATE_FMT) + ".html";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_HTML);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private TestPlan checkAndGetTestPlan(String testPlanId) {
        TestPlan testPlan = testPlanMapper.selectByPrimaryKey(testPlanId);
        if (testPlan == null) {
            throw new MSException(Translator.get("test_plan_not_exist"));
        }
        String currentProjectId = SessionUtils.getCurrentProjectId();
        if (StringUtils.isNotBlank(currentProjectId)
                && !StringUtils.equals(currentProjectId, testPlan.getProjectId())) {
            throw new MSException(Translator.get("test_plan_not_exist"));
        }
        return testPlan;
    }

    private TestPlanDocumentResponse.TemplateMeta buildTemplateMeta(TestPlan testPlan) {
        TestPlanDocumentResponse.TemplateMeta meta = new TestPlanDocumentResponse.TemplateMeta();
        Project project = projectMapper.selectByPrimaryKey(testPlan.getProjectId());
        meta.setProjectName(project == null ? StringUtils.EMPTY : project.getName());
        meta.setPlanName(testPlan.getName());

        SessionUser user = SessionUtils.getUser();
        meta.setAuthor(user == null ? StringUtils.EMPTY : StringUtils.defaultString(user.getName()));

        LocalDate today = LocalDate.now();
        meta.setDate(today.format(DATE_FMT));
        meta.setDocNo(buildDocNo(testPlan.getProjectId(), testPlan.getNum(), today));
        return meta;
    }

    /**
     * 文档编号：TP-{projectId短码}-{planNum}-{yyyyMMdd}
     */
    private String buildDocNo(String projectId, Long planNum, LocalDate date) {
        String shortCode = StringUtils.EMPTY;
        if (StringUtils.isNotBlank(projectId)) {
            shortCode = projectId.length() <= 8 ? projectId : projectId.substring(0, 8);
        }
        String num = planNum == null ? "0" : String.valueOf(planNum);
        return "TP-" + shortCode + "-" + num + "-" + date.format(DOC_DATE_FMT);
    }
}
