package io.metersphere.agent.tool.functional;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.service.AgentBusinessAccess;
import io.metersphere.agent.tool.*;
import io.metersphere.functional.request.FunctionalCaseFileRequest;
import io.metersphere.functional.service.FunctionalCaseAttachmentService;
import io.metersphere.sdk.exception.MSException;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class FunctionalAttachmentReadTool implements AgentMcpToolHandler {
    @Resource private AgentBusinessAccess access;
    @Resource private FunctionalCaseAttachmentService attachments;
    @Resource private JdbcTemplate jdbc;
    public String name(){return "metersphere.functional.attachment.read";}
    public String description(){return "Read an authorized published PNG/JPEG attachment as MCP image content, max 5 MiB. Optional submissionId reads retained historical evidence.";}
    public String requiredScope(){return AgentTokenScope.FUNCTIONAL_READ;}
    public Map<String,Object> inputSchema(){return AgentMcpToolSchemas.object(Map.of("projectId",AgentMcpToolSchemas.string(1,128),"caseId",AgentMcpToolSchemas.string(1,128),"fileId",AgentMcpToolSchemas.string(1,64),"submissionId",AgentMcpToolSchemas.string(1,64)),List.of("projectId","caseId","fileId"));}
    public Map<String,Object> annotations(){return AgentMcpToolSchemas.annotations(requiredScope(),true,false,true);}
    private String text(Map<String,Object> args,String key){if(!(args.get(key) instanceof String s)||s.isBlank()||s.length()>128)throw new MSException("AGENT_VALIDATION_ERROR");return s;}
    public Object execute(Map<String,Object> args){
        String project=text(args,"projectId"),id=text(args,"caseId"),file=text(args,"fileId");
        access.caseProject(id,project,"FUNCTIONAL_CASE:READ",false,requiredScope());
        byte[] bytes;
        if(args.containsKey("submissionId")) {
            var rows=jdbc.query("SELECT e.payload FROM agent_case_evidence e JOIN agent_case_submission_evidence r ON r.evidence_id=e.id JOIN agent_case_submission s ON s.id=r.submission_id WHERE e.id=? AND e.case_id=? AND e.project_id=? AND s.id=? AND s.case_id=e.case_id AND s.project_id=e.project_id AND s.gate_status='ACCEPTED'",
                (rs,n)->rs.getBytes(1),file,id,project,text(args,"submissionId"));
            if(rows.size()!=1)throw AgentBusinessAccess.denied(); bytes=rows.getFirst();
        } else {
            var sizes=jdbc.queryForList("SELECT size FROM functional_case_attachment WHERE case_id=? AND file_id=?",Long.class,id,file);
            if(sizes.size()!=1 || sizes.getFirst()==null || sizes.getFirst()>5L*1024*1024)throw new MSException("QUALITY_EVIDENCE_INVALID");
            var request=new FunctionalCaseFileRequest();request.setProjectId(project);request.setCaseId(id);request.setFileId(file);
            bytes=attachments.getFileByte(request);
        }
        if(bytes==null||bytes.length==0||bytes.length>5*1024*1024)throw new MSException("QUALITY_EVIDENCE_INVALID");
        String type;
        try(var input=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))){
            var readers=javax.imageio.ImageIO.getImageReaders(input);if(!readers.hasNext())throw new MSException("QUALITY_EVIDENCE_INVALID");
            var reader=readers.next();try {reader.setInput(input);if((long)reader.getWidth(0)*reader.getHeight(0)>20_000_000L || reader.read(0)==null)throw new MSException("QUALITY_EVIDENCE_INVALID");String format=reader.getFormatName();type="png".equalsIgnoreCase(format)?"image/png":"JPEG".equalsIgnoreCase(format)?"image/jpeg":null;} finally{reader.dispose();}
        }catch(java.io.IOException error){throw new MSException("QUALITY_EVIDENCE_INVALID");}
        if(type==null)throw new MSException("QUALITY_EVIDENCE_INVALID");
        return new AgentMcpImageResult(Base64.getEncoder().encodeToString(bytes),type,Map.of("fileId",file,"caseId",id,"sizeBytes",bytes.length,"sha256",org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes)));
    }
}
