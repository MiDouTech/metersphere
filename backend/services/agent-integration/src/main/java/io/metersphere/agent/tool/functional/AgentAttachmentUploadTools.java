package io.metersphere.agent.tool.functional;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.service.AgentAttachmentUploadIntentService;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolSchemas;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import java.util.function.Function;

@Configuration
public class AgentAttachmentUploadTools {
    @Bean public AgentMcpToolHandler prepareCaseAttachmentUpload(AgentAttachmentUploadIntentService service) {
        return tool("prepare", Map.of("projectId", AgentMcpToolSchemas.string(1,128), "purpose", AgentMcpToolSchemas.enumString(List.of("CASE_DETAIL","CASE_COMMENT")),
                "fileName", AgentMcpToolSchemas.string(1,200), "contentType", AgentMcpToolSchemas.string(1,128), "size", Map.of("type","integer","minimum",1,"maximum",5242880),
                "sha256", AgentMcpToolSchemas.string(64,64), "requestId", AgentMcpToolSchemas.string(1,128)),
                args -> service.prepare(JSON.parseObject(JSON.toJSONString(args), AgentAttachmentUploadIntentService.Prepare.class)));
    }
    @Bean public AgentMcpToolHandler completeCaseAttachmentUpload(AgentAttachmentUploadIntentService service) {
        return tool("complete", Map.of("uploadId", AgentMcpToolSchemas.string(1,50), "fileName", AgentMcpToolSchemas.string(1,200),
                "contentType", AgentMcpToolSchemas.string(1,128), "base64", AgentMcpToolSchemas.string(1,6990508)), args -> {
            String encoded = (String) args.get("base64");
            if (encoded == null || encoded.length() > 6990508) throw new MSException("上传内容不能超过5MiB");
            byte[] bytes;
            try { bytes = Base64.getDecoder().decode(encoded); } catch (IllegalArgumentException failure) { throw new MSException("上传内容不是有效的Base64"); }
            if (bytes.length == 0 || bytes.length > 5242880) throw new MSException("上传内容大小无效");
            try { return service.complete((String) args.get("uploadId"), new UploadFile((String) args.get("fileName"), (String) args.get("contentType"), bytes)); }
            catch (java.io.IOException failure) { throw new MSException("读取上传内容失败，请重试"); }
        });
    }
    private AgentMcpToolHandler tool(String action,Map<String,Object> properties,Function<Map<String,Object>,Object> operation) {
        return new AgentMcpToolHandler() {
            public String name(){return "metersphere.functional.attachment.upload."+action;}
            public String description(){return action.equals("prepare") ? "Prepare an ordinary case attachment upload, bound to token/project/purpose/file hash. 5 MiB per file, 15 minute expiry. Does not attach it to a case." : "Complete a prepared upload with matching base64 bytes. Returns temporary attachmentId; use attachment.attach or comment.create to associate it. Same uploadId and file replay safely.";}
            public String requiredScope(){return AgentTokenScope.CASE_ATTACHMENT;}
            public Map<String,Object> inputSchema(){return AgentMcpToolSchemas.object(properties,new ArrayList<>(properties.keySet()));}
            public Map<String,Object> annotations(){return AgentMcpToolSchemas.annotations(requiredScope(),false,false,true);}
            public Object execute(Map<String,Object> args){return operation.apply(args);}
        };
    }
    private record UploadFile(String filename,String contentType,byte[] bytes) implements MultipartFile {
        public String getName(){return "file";}
        public String getOriginalFilename(){return filename;}
        public String getContentType(){return contentType;}
        public boolean isEmpty(){return bytes.length==0;}
        public long getSize(){return bytes.length;}
        public byte[] getBytes(){return bytes;}
        public java.io.InputStream getInputStream(){return new java.io.ByteArrayInputStream(bytes);}
        public void transferTo(java.io.File destination) throws java.io.IOException {java.nio.file.Files.write(destination.toPath(),bytes);}
    }
}
