package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.sdk.exception.MSException;
import jakarta.annotation.Resource;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.util.*;

@Service
public class AgentExternalImageService {
    @Resource private AgentBusinessAccess access;
    @Resource private AgentOperationExecutor operations;
    @Resource private AgentExternalImageDownloader downloader;
    @Resource private AgentTempAttachmentService uploads;
    @Resource private AgentFunctionalCaseAttachmentService caseAttachments;
    @Resource private AgentBugAttachmentService bugAttachments;
    @Resource private AgentDirectExecutionService executions;
    @Resource private JdbcTemplate jdbc;
    @Value("${agent.external-image.enabled:true}") private boolean enabled;

    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    @SuppressWarnings("unchecked")
    public Map<String,Object> importImage(String kind,Map<String,Object> args) {
        if(!enabled) throw new MSException("EXTERNAL_IMAGE_NOT_AVAILABLE");
        String operation="external.image."+kind;
        String requestId=required(args,"requestId");
        Object name=args.get("fileName");
        if(name!=null && (!(name instanceof String) || ((String)name).isBlank() || ((String)name).length()>200))
            throw new MSException("AGENT_VALIDATION_ERROR");
        required(args,"url");
        Runnable authorize=() -> authorize(kind,args);
        var previous=operations.completed(operation,requestId,args,Map.class,authorize);
        if(previous.isPresent()) return previous.get();
        // Network I/O deliberately runs outside the database/idempotency transaction.
        var image=downloader.download(required(args,"url"),(String)args.get("fileName"));
        return operations.execute(operation,requestId,args,Map.class,authorize,() -> {
            String project=required(args,"projectId");
            if("execution".equals(kind)) {
                var upload=new LinkedHashMap<String,Object>(args);
                upload.remove("url");
                upload.put("fileName",image.fileName()); upload.put("contentType",image.contentType());
                upload.put("base64",Base64.getEncoder().encodeToString(image.bytes()));
                var result=new LinkedHashMap<>(executions.upload(upload));
                result.put("state","STAGED"); return result;
            }
            boolean bug="bug".equals(kind);
            String target=required(args,bug?"bugId":"caseId");
            var uploaded=uploads.upload(new ImageFile(image),project,bug?"BUG_DETAIL":"CASE_DETAIL",null);
            if(bug) bugAttachments.attach(project,target,List.of(uploaded.getAttachmentId()));
            else caseAttachments.attach(project,target,List.of(uploaded.getAttachmentId()));
            var entries=bug?bugAttachments.list(project,target):caseAttachments.list(project,target);
            var association=entries.stream().filter(entry->uploaded.getFileId().equals(entry.getFileId())).findFirst()
                    .orElseThrow(()->new MSException("ATTACHMENT_ASSOCIATION_FAILED"));
            Map<String,Object> result=new LinkedHashMap<>();
            result.put("state","ATTACHED");result.put("projectId",project);
            result.put(bug?"bugId":"caseId",target);
            result.put("attachmentId",uploaded.getAttachmentId()); result.put("fileId",uploaded.getFileId());
            result.put("associationId",association.getAssociationId());result.put("fileName",image.fileName());
            result.put("contentType",image.contentType());result.put("sizeBytes",image.bytes().length);
            result.put("sha256",DigestUtils.sha256Hex(image.bytes()));return result;
        });
    }

    private void authorize(String kind,Map<String,Object> args) {
        String project=required(args,"projectId");
        if("bug".equals(kind)) {
            access.project(project,"PROJECT_BUG:READ+UPDATE",true,AgentTokenScope.BUG_ATTACHMENT);
            Long exists=jdbc.queryForObject("SELECT COUNT(*) FROM bug WHERE id=? AND project_id=? AND deleted=0",Long.class,required(args,"bugId"),project);
            if(exists==null || exists!=1) throw AgentBusinessAccess.denied();
        } else {
            access.caseProject(required(args,"caseId"),project,"FUNCTIONAL_CASE:READ+UPDATE",true,AgentTokenScope.CASE_ATTACHMENT);
            if("execution".equals(kind)) access.caseProject(required(args,"caseId"),project,"FUNCTIONAL_CASE:READ+UPDATE",true,AgentTokenScope.FUNCTIONAL_SUBMIT);
        }
    }
    private static String required(Map<String,Object> args,String key) {
        if(!(args.get(key) instanceof String s) || s.isBlank() || s.length()>("url".equals(key)?8192:128)) throw new MSException("AGENT_VALIDATION_ERROR");
        return s;
    }
    private record ImageFile(AgentExternalImageDownloader.Image image) implements MultipartFile {
        public String getName(){return "file";}
        public String getOriginalFilename(){return image.fileName();}
        public String getContentType(){return image.contentType();}
        public boolean isEmpty(){return image.bytes().length==0;}
        public long getSize(){return image.bytes().length;}
        public byte[] getBytes(){return image.bytes();}
        public InputStream getInputStream(){return new ByteArrayInputStream(image.bytes());}
        public void transferTo(File target)throws IOException{java.nio.file.Files.write(target.toPath(),image.bytes());}
    }
}
