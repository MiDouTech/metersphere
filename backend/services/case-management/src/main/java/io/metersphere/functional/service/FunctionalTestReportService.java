package io.metersphere.functional.service;

import io.metersphere.functional.domain.FunctionalTestReport;
import io.metersphere.functional.dto.FunctionalTestReportBugCountDTO;
import io.metersphere.functional.dto.FunctionalTestReportDTO;
import io.metersphere.functional.dto.FunctionalTestReportResultCountDTO;
import io.metersphere.functional.dto.FunctionalTestReportRiskCaseDTO;
import io.metersphere.functional.dto.FunctionalTestReportStatsDTO;
import io.metersphere.functional.mapper.ExtFunctionalTestReportMapper;
import io.metersphere.functional.mapper.FunctionalTestReportMapper;
import io.metersphere.functional.request.FunctionalTestReportGenerateRequest;
import io.metersphere.functional.request.FunctionalTestReportPageRequest;
import io.metersphere.functional.request.FunctionalTestReportUpdateRequest;
import io.metersphere.sdk.constants.ResultStatus;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.sdk.util.Translator;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(rollbackFor = Exception.class)
public class FunctionalTestReportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String PASS_RATE_FORMULA_NOTE =
            "passRate = pass / (total - block - fail) * 100%; denominator 0 => '-'";

    @Resource
    private FunctionalTestReportMapper functionalTestReportMapper;
    @Resource
    private ExtFunctionalTestReportMapper extFunctionalTestReportMapper;

    public List<FunctionalTestReportDTO> list(FunctionalTestReportPageRequest request) {
        List<FunctionalTestReport> reports = extFunctionalTestReportMapper.list(request);
        List<FunctionalTestReportDTO> result = new ArrayList<>();
        if (CollectionUtils.isEmpty(reports)) {
            return result;
        }
        for (FunctionalTestReport report : reports) {
            result.add(toDTO(report));
        }
        return result;
    }

    public FunctionalTestReportDTO get(String id) {
        return toDTO(checkAndGet(id));
    }

    public FunctionalTestReportDTO generate(FunctionalTestReportGenerateRequest request) {
        if (StringUtils.isBlank(request.getPlanId())) {
            throw new MSException(Translator.get("functional_test_report.plan_id.not_blank"));
        }
        String userId = SessionUtils.getUserId();
        long now = System.currentTimeMillis();
        String name = StringUtils.defaultIfBlank(request.getName(),
                "测试报告-" + LocalDate.now().format(DATE_FMT));

        FunctionalTestReportStatsDTO stats = buildStats(request.getProjectId(), request.getPlanId());

        FunctionalTestReport report = new FunctionalTestReport();
        report.setId(IDGenerator.nextStr());
        report.setProjectId(request.getProjectId());
        report.setName(name);
        report.setPlanId(request.getPlanId());
        report.setContent(JSON.toJSONString(buildDefaultContent(stats.getExec())));
        report.setStatsSnapshot(JSON.toJSONString(stats));
        report.setCreateTime(now);
        report.setUpdateTime(now);
        report.setCreateUser(userId);
        report.setUpdateUser(userId);
        functionalTestReportMapper.insert(report);
        return toDTO(report);
    }

    public FunctionalTestReportDTO update(FunctionalTestReportUpdateRequest request) {
        FunctionalTestReport existing = checkAndGet(request.getId());
        if (StringUtils.isNotBlank(request.getName())) {
            existing.setName(request.getName());
        }
        if (request.getContent() != null) {
            existing.setContent(request.getContent());
        }
        existing.setUpdateTime(System.currentTimeMillis());
        existing.setUpdateUser(SessionUtils.getUserId());
        functionalTestReportMapper.updateByPrimaryKeySelective(existing);
        return toDTO(functionalTestReportMapper.selectByPrimaryKey(existing.getId()));
    }

    /**
     * 刷新系统聚合快照，并覆盖 content.execStats；保留其余文字章节。
     */
    public FunctionalTestReportDTO refreshStats(String id) {
        FunctionalTestReport existing = checkAndGet(id);
        if (StringUtils.isBlank(existing.getPlanId())) {
            throw new MSException(Translator.get("functional_test_report.plan_id.not_blank"));
        }
        FunctionalTestReportStatsDTO stats = buildStats(existing.getProjectId(), existing.getPlanId());
        existing.setStatsSnapshot(JSON.toJSONString(stats));
        existing.setContent(mergeExecStatsIntoContent(existing.getContent(), stats.getExec()));
        existing.setUpdateTime(System.currentTimeMillis());
        existing.setUpdateUser(SessionUtils.getUserId());
        functionalTestReportMapper.updateByPrimaryKeySelective(existing);
        return toDTO(functionalTestReportMapper.selectByPrimaryKey(existing.getId()));
    }

    public void delete(String id) {
        checkAndGet(id);
        functionalTestReportMapper.deleteByPrimaryKey(id);
    }

    public FunctionalTestReportStatsDTO buildStats(String projectId, String planId) {
        if (StringUtils.isBlank(planId)) {
            throw new MSException(Translator.get("functional_test_report.plan_id.not_blank"));
        }
        List<FunctionalTestReportResultCountDTO> counts = extFunctionalTestReportMapper.countExecByPlan(planId);
        List<FunctionalTestReportRiskCaseDTO> riskCases = extFunctionalTestReportMapper.listRiskCasesByPlan(planId);

        long pass = 0;
        long fail = 0;
        long block = 0;
        long total = 0;
        if (CollectionUtils.isNotEmpty(counts)) {
            for (FunctionalTestReportResultCountDTO item : counts) {
                long cnt = item.getCnt() == null ? 0L : item.getCnt();
                total += cnt;
                String result = StringUtils.defaultString(item.getResult());
                if (StringUtils.equals(result, ResultStatus.SUCCESS.name())) {
                    pass += cnt;
                } else if (StringUtils.equals(result, ResultStatus.ERROR.name())
                        || StringUtils.equals(result, ResultStatus.FAKE_ERROR.name())) {
                    fail += cnt;
                } else if (StringUtils.equals(result, ResultStatus.BLOCKED.name())) {
                    block += cnt;
                }
            }
        }

        FunctionalTestReportStatsDTO.ExecStats exec = new FunctionalTestReportStatsDTO.ExecStats();
        exec.setTotal(total);
        exec.setPass(pass);
        exec.setFail(fail);
        exec.setBlock(block);
        exec.setExecRate(formatRate(pass + fail + block, total));
        // 产品公式：pass / (total - block - fail)；分母 0 → "-"
        exec.setPassRate(formatRate(pass, total - block - fail));

        FunctionalTestReportStatsDTO stats = new FunctionalTestReportStatsDTO();
        stats.setExec(exec);
        stats.setBugHandlerStatus(buildBugHandlerStatusRows(planId));
        stats.setBugType(buildBugTypeRows(planId));
        stats.setRiskCases(riskCases == null ? new ArrayList<>() : riskCases);
        stats.setPassRateFormulaNote(PASS_RATE_FORMULA_NOTE);
        stats.setBugTypeMessage(null);
        return stats;
    }

    private List<Object> buildBugHandlerStatusRows(String planId) {
        List<FunctionalTestReportBugCountDTO> rows =
                extFunctionalTestReportMapper.countBugHandlerStatusByPlan(planId);
        List<Object> result = new ArrayList<>();
        if (CollectionUtils.isEmpty(rows)) {
            return result;
        }
        for (FunctionalTestReportBugCountDTO row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("handler", StringUtils.defaultIfBlank(row.getHandleUserName(), row.getHandleUser()));
            item.put("handleUser", row.getHandleUser());
            item.put("status", StringUtils.defaultIfBlank(row.getStatusName(), row.getStatus()));
            item.put("count", row.getCount() == null ? 0L : row.getCount());
            result.add(item);
        }
        return result;
    }

    private List<Object> buildBugTypeRows(String planId) {
        List<FunctionalTestReportBugCountDTO> rows = extFunctionalTestReportMapper.countBugTypeByPlan(planId);
        List<Object> result = new ArrayList<>();
        if (CollectionUtils.isEmpty(rows)) {
            return result;
        }
        for (FunctionalTestReportBugCountDTO row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            String type = StringUtils.defaultIfBlank(row.getType(), "未分类");
            item.put("type", type);
            item.put("name", type);
            item.put("count", row.getCount() == null ? 0L : row.getCount());
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> buildDefaultContent(FunctionalTestReportStatsDTO.ExecStats exec) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("versionOverview", new LinkedHashMap<>());

        Map<String, Object> testScope = new LinkedHashMap<>();
        testScope.put("content", "");
        content.put("testScope", testScope);

        Map<String, Object> conclusion = new LinkedHashMap<>();
        conclusion.put("result", "");
        conclusion.put("suggestion", "");
        content.put("conclusion", conclusion);

        content.put("riskNote", "");
        content.put("execStats", execToMap(exec));
        return content;
    }

    @SuppressWarnings("unchecked")
    private String mergeExecStatsIntoContent(String contentJson, FunctionalTestReportStatsDTO.ExecStats exec) {
        Map<String, Object> content;
        try {
            content = StringUtils.isBlank(contentJson)
                    ? new LinkedHashMap<>()
                    : JSON.parseObject(contentJson, Map.class);
            if (content == null) {
                content = new LinkedHashMap<>();
            }
        } catch (Exception e) {
            content = new LinkedHashMap<>();
        }
        content.put("execStats", execToMap(exec));
        return JSON.toJSONString(content);
    }

    private Map<String, Object> execToMap(FunctionalTestReportStatsDTO.ExecStats exec) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (exec == null) {
            map.put("total", 0);
            map.put("pass", 0);
            map.put("fail", 0);
            map.put("block", 0);
            map.put("execRate", "-");
            map.put("passRate", "-");
            return map;
        }
        map.put("total", exec.getTotal());
        map.put("pass", exec.getPass());
        map.put("fail", exec.getFail());
        map.put("block", exec.getBlock());
        map.put("execRate", exec.getExecRate());
        map.put("passRate", exec.getPassRate());
        return map;
    }

    private String formatRate(long numerator, long denominator) {
        if (denominator <= 0) {
            return "-";
        }
        BigDecimal rate = BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
        return rate.toPlainString() + "%";
    }

    private FunctionalTestReport checkAndGet(String id) {
        FunctionalTestReport report = functionalTestReportMapper.selectByPrimaryKey(id);
        if (report == null) {
            throw new MSException(Translator.get("resource_not_exist"));
        }
        String currentProjectId = SessionUtils.getCurrentProjectId();
        if (StringUtils.isNotBlank(currentProjectId)
                && !StringUtils.equals(currentProjectId, report.getProjectId())) {
            throw new MSException(Translator.get("resource_not_exist"));
        }
        return report;
    }

    private FunctionalTestReportDTO toDTO(FunctionalTestReport report) {
        FunctionalTestReportDTO dto = new FunctionalTestReportDTO();
        dto.setId(report.getId());
        dto.setProjectId(report.getProjectId());
        dto.setName(report.getName());
        dto.setPlanId(report.getPlanId());
        dto.setContent(report.getContent());
        dto.setStatsSnapshot(report.getStatsSnapshot());
        dto.setCreateTime(report.getCreateTime());
        dto.setUpdateTime(report.getUpdateTime());
        dto.setCreateUser(report.getCreateUser());
        dto.setUpdateUser(report.getUpdateUser());
        return dto;
    }
}
