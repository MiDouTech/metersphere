package io.metersphere.agent.controller;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.dto.AgentAttachmentDTO;
import io.metersphere.agent.dto.AgentAttachmentUploadResponse;
import io.metersphere.agent.dto.AgentBatchSubmitRequest;
import io.metersphere.agent.dto.AgentBatchSubmitResponse;
import io.metersphere.agent.dto.AgentCaseDTO;
import io.metersphere.agent.dto.AgentCaseSearchRequest;
import io.metersphere.agent.dto.AgentCaseSearchResponse;
import io.metersphere.agent.dto.AgentCaseSubmitRequest;
import io.metersphere.agent.dto.AgentExecLogDTO;
import io.metersphere.agent.dto.AgentExecLogPageRequest;
import io.metersphere.agent.dto.AgentModuleDTO;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.service.AgentAttachmentService;
import io.metersphere.agent.service.AgentBatchSubmitService;
import io.metersphere.agent.service.AgentExecLogService;
import io.metersphere.agent.service.AgentFunctionalCaseSearchService;
import io.metersphere.agent.service.AgentFunctionalCaseSubmitService;
import io.metersphere.system.domain.AgentToken;
import io.metersphere.system.utils.Pager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Agent Functional Case")
@RestController
@RequestMapping("/api/agent/v1/functional")
public class AgentFunctionalCaseController {
    @Resource
    private AgentFunctionalCaseSearchService agentFunctionalCaseSearchService;
    @Resource
    private AgentFunctionalCaseSubmitService agentFunctionalCaseSubmitService;
    @Resource
    private AgentBatchSubmitService agentBatchSubmitService;
    @Resource
    private AgentExecLogService agentExecLogService;
    @Resource
    private AgentAttachmentService agentAttachmentService;

    @GetMapping("/health")
    @Operation(summary = "健康检查")
    public String health() {
        return "ok";
    }

    @PostMapping("/search")
    @Operation(summary = "检索功能用例")
    public AgentCaseSearchResponse search(@RequestBody AgentCaseSearchRequest request) {
        assertScope(AgentTokenScope.FUNCTIONAL_READ);
        return agentFunctionalCaseSearchService.search(request);
    }

    @GetMapping("/{caseId}")
    @Operation(summary = "获取用例详情")
    public AgentCaseDTO get(@PathVariable String caseId,
                            @RequestParam(defaultValue = "true") boolean includeSteps,
                            @RequestParam(required = false) String testPlanId) {
        assertScope(AgentTokenScope.FUNCTIONAL_READ);
        return agentFunctionalCaseSearchService.getById(caseId, includeSteps, testPlanId);
    }

    @GetMapping("/modules")
    @Operation(summary = "模块列表")
    public List<AgentModuleDTO> modules(@RequestParam String projectId) {
        assertScope(AgentTokenScope.FUNCTIONAL_READ);
        return agentFunctionalCaseSearchService.listModules(projectId);
    }

    @PostMapping("/submit")
    @Operation(summary = "回写执行结果")
    public void submit(@RequestBody @Valid AgentCaseSubmitRequest request) {
        assertScope(AgentTokenScope.FUNCTIONAL_SUBMIT);
        agentFunctionalCaseSubmitService.submit(request);
    }

    @PostMapping("/submit/batch")
    @Operation(summary = "批量回写执行结果")
    public AgentBatchSubmitResponse batchSubmit(@RequestBody @Valid AgentBatchSubmitRequest request) {
        assertScope(AgentTokenScope.FUNCTIONAL_SUBMIT);
        return agentBatchSubmitService.batchSubmit(request);
    }

    @GetMapping("/exec-log/page")
    @Operation(summary = "审计日志分页")
    public Pager<List<AgentExecLogDTO>> execLogPage(AgentExecLogPageRequest request) {
        assertScope(AgentTokenScope.FUNCTIONAL_READ);
        return agentExecLogService.page(request);
    }

    @GetMapping("/exec-log/{id}")
    @Operation(summary = "审计日志详情")
    public AgentExecLogDTO execLogDetail(@PathVariable String id) {
        assertScope(AgentTokenScope.FUNCTIONAL_READ);
        return agentExecLogService.get(id);
    }

    @PostMapping("/attachment/upload")
    @Operation(summary = "上传执行证据附件")
    public AgentAttachmentUploadResponse uploadAttachment(@RequestParam("file") MultipartFile file,
                                                          @RequestParam String projectId,
                                                          @RequestParam(required = false) Integer stepNum) {
        assertScope(AgentTokenScope.FUNCTIONAL_SUBMIT);
        return agentAttachmentService.upload(file, projectId, stepNum);
    }

    @GetMapping("/attachment/{id}")
    @Operation(summary = "获取附件信息")
    public AgentAttachmentDTO getAttachment(@PathVariable String id,
                                            @RequestParam(required = false) String projectId) {
        assertScope(AgentTokenScope.FUNCTIONAL_READ);
        return agentAttachmentService.get(id);
    }

    @GetMapping("/attachment/download/{projectId}/{fileId}")
    @Operation(summary = "下载附件")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable String projectId, @PathVariable String fileId) {
        assertScope(AgentTokenScope.FUNCTIONAL_READ);
        return agentAttachmentService.download(projectId, fileId);
    }

    private void assertScope(String requiredScope) {
        AgentToken token = AgentTokenContext.get();
        if (token == null || StringUtils.isBlank(token.getScopes())) {
            return;
        }
        String scopes = token.getScopes();
        if (StringUtils.contains(scopes, AgentTokenScope.FUNCTIONAL_ALL)
                || StringUtils.contains(scopes, requiredScope)) {
            return;
        }
        throw new IllegalStateException("Agent token scope 不足: " + requiredScope);
    }
}
