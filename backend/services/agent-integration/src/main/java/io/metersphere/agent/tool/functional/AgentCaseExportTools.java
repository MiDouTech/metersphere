package io.metersphere.agent.tool.functional;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.security.AgentScopeAssert;
import io.metersphere.agent.service.AgentProjectService;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolSchemas;
import io.metersphere.functional.request.FunctionalCaseExportRequest;
import io.metersphere.functional.service.FunctionalCaseExportTaskService;
import io.metersphere.functional.service.FunctionalCaseFileService;
import io.metersphere.functional.service.FunctionalCaseXmindService;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.utils.SessionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;

/** Same durable tasks and authorization as the web UI; each chunk is reauthorized. */
@Configuration
public class AgentCaseExportTools {
    @Bean public AgentMcpToolHandler caseExportStart(AgentProjectService projects, FunctionalCaseExportTaskService tasks,
            FunctionalCaseFileService files, FunctionalCaseXmindService xmind) {
        return handler("start", projects, tasks, files, xmind);
    }
    @Bean public AgentMcpToolHandler caseExportStatus(AgentProjectService projects, FunctionalCaseExportTaskService tasks,
            FunctionalCaseFileService files, FunctionalCaseXmindService xmind) {
        return handler("status", projects, tasks, files, xmind);
    }
    @Bean public AgentMcpToolHandler caseExportStop(AgentProjectService projects, FunctionalCaseExportTaskService tasks,
            FunctionalCaseFileService files, FunctionalCaseXmindService xmind) {
        return handler("stop", projects, tasks, files, xmind);
    }
    @Bean public AgentMcpToolHandler caseExportDownload(AgentProjectService projects, FunctionalCaseExportTaskService tasks,
            FunctionalCaseFileService files, FunctionalCaseXmindService xmind) {
        return handler("download", projects, tasks, files, xmind);
    }

    private AgentMcpToolHandler handler(String action, AgentProjectService projects, FunctionalCaseExportTaskService tasks,
            FunctionalCaseFileService files, FunctionalCaseXmindService xmind) {
        return new AgentMcpToolHandler() {
            public String name() { return "metersphere.functional.export." + action; }
            public String description() { return "start".equals(action)
                    ? "Create an idempotent Excel/XMind export. request uses web export fields: selectIds/selectAll, moduleIds, systemFields/customFields/otherFields (id/name), isMerge. Poll status, then download chunks."
                    : "download".equals(action) ? "Read up to 65536 bytes as base64 at offset, with fresh authorization. Append chunks until eof. Never returns a public file URL."
                    : "Read or stop your own durable export task. Stopping a completed task preserves success."; }
            public String requiredScope() { return AgentTokenScope.FUNCTIONAL_READ; }
            public Map<String,Object> inputSchema() {
                Map<String,Object> fields = new LinkedHashMap<>();
                fields.put("projectId", AgentMcpToolSchemas.string(1,128));
                if ("start".equals(action)) {
                    fields.put("format", Map.of("type","string","enum",List.of("xlsx","xmind")));
                    fields.put("requestId", AgentMcpToolSchemas.string(1,64));
                    fields.put("request", Map.of("type","object"));
                    return Map.of("type","object","properties",fields,"required",List.of("projectId","format","requestId","request"),"additionalProperties",false);
                }
                fields.put("taskId", AgentMcpToolSchemas.string(1,64));
                if ("stop".equals(action)) {
                    fields.put("requestId", AgentMcpToolSchemas.string(1,64));
                    return Map.of("type","object","properties",fields,"required",List.of("projectId","taskId","requestId"),"additionalProperties",false);
                }
                if ("download".equals(action)) {
                    fields.put("offset",Map.of("type","integer","minimum",0,"maximum",67108864));
                    fields.put("length",Map.of("type","integer","minimum",1,"maximum",65536));
                }
                return Map.of("type","object","properties",fields,"required",List.of("projectId","taskId"),"additionalProperties",false);
            }
            public Map<String,Object> annotations() {
                return Map.of("readOnlyHint",Set.of("status","download").contains(action),"destructiveHint",false,"idempotentHint",true,"openWorldHint",false);
            }
            public Object execute(Map<String,Object> args) {
                AgentScopeAssert.assertScope(AgentTokenScope.FUNCTIONAL_READ);
                String projectId = projects.resolveProjectId((String)args.get("projectId"));
                String userId = SessionUtils.getUserId();
                if ("start".equals(action)) {
                    String format = (String)args.get("format");
                    if (!Set.of("xlsx","xmind").contains(format)) throw new MSException("导出格式无效");
                    var request = JSON.parseObject(JSON.toJSONString(args.get("request")), FunctionalCaseExportRequest.class);
                    request.setProjectId(projectId);
                    request.setRequestId((String)args.get("requestId"));
                    if (request.getRequestId() == null) throw new MSException("导出请求标识不能为空");
                    String id = "xlsx".equals(format) ? files.export(userId, request, SessionUtils.getCurrentOrganizationId())
                            : xmind.exportFunctionalCaseXmind(request,userId,SessionUtils.getCurrentOrganizationId());
                    return tasks.get(id,userId);
                }
                String id = (String)args.get("taskId");
                var task = tasks.get(id,userId);
                if (!projectId.equals(task.getProjectId())) throw new MSException("导出任务不存在或当前账号无权访问");
                if ("stop".equals(action)) return tasks.stop(id,userId);
                if (!"download".equals(action)) return task;
                long offset = ((Number)args.getOrDefault("offset",0)).longValue();
                int length = ((Number)args.getOrDefault("length",65536)).intValue();
                if (offset < 0 || offset > 67108864 || length < 1 || length > 65536) throw new MSException("下载分块参数无效");
                var response = files.downloadFile(projectId,task.getFileId(),userId);
                try (var stream = Objects.requireNonNull(response.getBody()).getInputStream()) {
                    stream.skipNBytes(offset);
                    byte[] bytes = stream.readNBytes(length+1);
                    boolean eof = bytes.length <= length;
                    byte[] chunk = eof ? bytes : Arrays.copyOf(bytes,length);
                    return Map.of("taskId",id,"fileType",task.getFileType(),"offset",offset,"nextOffset",offset+chunk.length,
                            "eof",eof,"base64",Base64.getEncoder().encodeToString(chunk));
                } catch (Exception exception) { throw new MSException("导出文件读取失败，请检查任务状态后重试"); }
            }
        };
    }
}
