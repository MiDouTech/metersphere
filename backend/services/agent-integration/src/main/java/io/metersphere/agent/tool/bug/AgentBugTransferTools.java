package io.metersphere.agent.tool.bug;
import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.service.AgentBugTransferService;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolSchemas;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.*;
import java.util.function.Function;
@Configuration
public class AgentBugTransferTools {
    @Bean AgentMcpToolHandler bugExportColumns(AgentBugTransferService s){return handler("export.columns",s::columns);}
    @Bean AgentMcpToolHandler bugExport(AgentBugTransferService s){return handler("export",s::export);}
    @Bean AgentMcpToolHandler bugImportPreview(AgentBugTransferService s){return handler("import.preview",s::preview);}
    @Bean AgentMcpToolHandler bugImportCommit(AgentBugTransferService s){return handler("import.commit",s::commit);}
    private AgentMcpToolHandler handler(String action,Function<Map<String,Object>,Object> execute) {
        boolean importing=action.startsWith("import"),write=Set.of("export","import.commit").contains(action);
        return new AgentMcpToolHandler(){
            public String name(){return "metersphere.bug."+action;}
            public String description(){return switch(action){
                case "export.columns" -> "Read bug export columns; requires current user export permission and BUG_READ.";
                case "export" -> "Export 1..100 explicit bugIds as a ZIP containing XLSX. Returns an expiring authenticated downloadPath, size and SHA256 (max 5 MiB). Requires user export permission and BUG_READ.";
                case "import.preview" -> "Validate rows or a base64 XLSX (exclusive). Headers: title, description, handleUser, custom:<fieldId>. No formulas; max 100 rows/5 MiB. Returns previewHash and per-row errors. Requires BUG_WRITE.";
                default -> "Commit a previewed import using unchanged content and previewHash. Each row has its own transaction and persistent idempotency. Returns SUCCEEDED/PARTIAL/FAILED with row results; retries never duplicate successful rows. Requires BUG_WRITE.";};}
            public String requiredScope(){return importing?AgentTokenScope.BUG_WRITE:AgentTokenScope.BUG_READ;}
            public Map<String,Object> annotations(){return AgentMcpToolSchemas.annotations(requiredScope(),!write,false,write);}
            public Map<String,Object> inputSchema(){
                Map<String,Object> fields=new LinkedHashMap<>();fields.put("projectId",AgentMcpToolSchemas.string(1,128));
                List<String> required=new ArrayList<>(List.of("projectId"));
                if(write){fields.put("requestId",AgentMcpToolSchemas.string(1,128));required.add("requestId");}
                if("export".equals(action)){fields.put("bugIds",Map.of("type","array","minItems",1,"maxItems",100,"uniqueItems",true,"items",AgentMcpToolSchemas.string(1,128)));required.add("bugIds");
                    fields.put("exportColumns",Map.of("type","array","minItems",1,"maxItems",50,"items",AgentMcpToolSchemas.object(Map.of("key",AgentMcpToolSchemas.string(1,128),"columnType",Map.of("type","string","enum",List.of("system","custom","other"))),List.of("key","columnType"))));}
                if(importing){
                    fields.put("templateId",AgentMcpToolSchemas.string(1,128));fields.put("fileName",AgentMcpToolSchemas.string(1,200));fields.put("base64",AgentMcpToolSchemas.string(1,6990508));
                    fields.put("rows",Map.of("type","array","minItems",1,"maxItems",100,"items",AgentMcpToolSchemas.object(Map.of(
                        "title",AgentMcpToolSchemas.string(1,255),"description",AgentMcpToolSchemas.string(0,100000),"handleUser",AgentMcpToolSchemas.string(1,2000),
                        "customFields",Map.of("type","object","additionalProperties",Map.of("type","string"))),List.of("title"))));
                    if(write){fields.put("previewHash",AgentMcpToolSchemas.string(64,64));required.add("previewHash");}
                }
                return AgentMcpToolSchemas.object(fields,required);
            }
            public Object execute(Map<String,Object> args){return execute.apply(args);}
        };
    }
}
