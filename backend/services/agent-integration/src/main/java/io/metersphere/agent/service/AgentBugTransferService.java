package io.metersphere.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.dto.AgentBugCreateRequest;
import io.metersphere.agent.dto.AgentBugDTO;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.bug.dto.BugExportColumn;
import io.metersphere.bug.dto.request.BugExportRequest;
import io.metersphere.bug.service.BugService;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.exception.MSException;
import jakarta.annotation.Resource;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.util.*;
import java.util.zip.ZipInputStream;

/** Bounded file transfer for MCP; no UI-only controller permissions are bypassed. */
@Service
public class AgentBugTransferService {
    private static final ObjectMapper JSON = new ObjectMapper();
    static final int MAX_BYTES = 5 * 1024 * 1024;
    @Resource private AgentBugWriteService bugs;
    @Resource private BugService bugService;
    @Resource private AgentProjectService projects;
    @Resource private AgentBusinessAccess access;
    @Resource private io.metersphere.project.service.ProjectApplicationService applications;
    @Resource private AgentTempAttachmentService attachments;
    @Resource private AgentOperationExecutor operations;
    @Resource private AgentSafeErrorMapper errors;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AgentBugTransferService.class);

    private String authorize(Map<String,Object> args, boolean write) {
        String project = projects.resolveProjectId(text(args,"projectId"));
        access.project(project, write ? PermissionConstants.PROJECT_BUG_ADD : PermissionConstants.PROJECT_BUG_EXPORT,
                write, write ? AgentTokenScope.BUG_WRITE : AgentTokenScope.BUG_READ);
        if(write && !"Local".equalsIgnoreCase(applications.getPlatformName(project))) fail("BUG_IMPORT_LOCAL_ONLY");
        return project;
    }
    public Object columns(Map<String,Object> args) { return bugService.getExportColumns(authorize(args,false)); }
    public Map<String,Object> export(Map<String,Object> args) {
        String project = authorize(args,false);
        return operations.execute("bug.export",text(args,"requestId"),args,Map.class,()->authorize(args,false),()->{
            List<String> ids = JSON.convertValue(args.get("bugIds"), JSON.getTypeFactory().constructCollectionType(List.class,String.class));
            if(ids == null || ids.isEmpty() || ids.size()>100 || new HashSet<>(ids).size()!=ids.size()) fail("BUG_TRANSFER_LIMIT_EXCEEDED");
            for(String id:ids) {
                if(StringUtils.isBlank(id) || !project.equals(bugs.get(id).getProjectId())) throw AgentBusinessAccess.denied();
            }
            var request = new BugExportRequest(); request.setProjectId(project); request.setSelectAll(false);
            request.setSelectIds(ids); request.setUseTrash(false); request.setSort("pos desc");
            // Reuse the existing export column contract. XLSX imports use their own stable headers.
            request.setExportColumns(List.of(new BugExportColumn("num","ID","system"),
                    new BugExportColumn("name","title","system"),new BugExportColumn("content","description","system")));
            if(args.get("exportColumns")!=null) {
                var selected = JSON.convertValue(args.get("exportColumns"), JSON.getTypeFactory().constructCollectionType(List.class,BugExportColumn.class));
                if(!(selected instanceof List<?> list) || list.isEmpty() || list.size()>50) fail("AGENT_VALIDATION_ERROR");
                List<BugExportColumn> columns=(List<BugExportColumn>)selected;
                var config=bugService.getExportColumns(project);Set<String> seen=new HashSet<>();
                for(var column:columns){
                    Map<String,String> available=switch(StringUtils.defaultString(column.getColumnType())) {
                        case "system"->config.getSystemColumns();case "custom"->config.getCustomColumns();case "other"->config.getOtherColumns();default->Map.of();};
                    if(!available.containsKey(column.getKey()) || !seen.add(column.getColumnType()+":"+column.getKey()))fail("AGENT_VALIDATION_ERROR");
                    column.setText(available.get(column.getKey()));
                }
                request.setExportColumns(columns);
            }
            byte[] bytes;
            try { bytes=bugService.export(request).getBody(); }
            catch(MSException business) { throw business; }
            catch(Exception exception) {
                LOG.error("MCP bug export generation failed projectId={} count={}",project,ids.size(),exception);
                throw new MSException("BUG_EXPORT_GENERATION_FAILED");
            }
            if(bytes==null || bytes.length==0) fail("BUG_EXPORT_EMPTY");
            if(bytes.length>MAX_BYTES) fail("BUG_TRANSFER_LIMIT_EXCEEDED");
            io.metersphere.agent.dto.AgentTempAttachmentUploadResponse stored;
            try { stored=attachments.upload(new BytesFile("metersphere-bugs.zip","application/zip",bytes),project,"BUG_EXPORT",null); }
            catch(Exception exception) {
                LOG.error("MCP bug export storage failed projectId={} count={}",project,ids.size(),exception);
                throw new MSException("BUG_EXPORT_STORAGE_FAILED");
            }
            Map<String,Object> result=JSON.convertValue(stored,Map.class);
            result.put("sha256",DigestUtils.sha256Hex(bytes)); result.put("count",ids.size());
            result.put("format","ZIP_XLSX"); return result;
        });
    }
    public Map<String,Object> preview(Map<String,Object> args) {
        String project=authorize(args,true); var rows=readRows(args);
        var result=new LinkedHashMap<String,Object>(); result.put("projectId",project);
        result.put("previewHash",previewHash(args,project)); result.put("total",rows.size());
        List<Map<String,Object>> reports=new ArrayList<>();
        for(int i=0;i<rows.size();i++) {
            try { bugs.validateCreate(rowRequest(args,project,rows.get(i))); reports.add(Map.of("row",i+1,"valid",true)); }
            catch(MSException invalid) {var safe=errors.toApiError(invalid,null);reports.add(Map.of("row",i+1,"valid",false,"code",safe.getCode(),"message",safe.getMessage()));}
        }
        result.put("rows",reports); result.put("validCount",reports.stream().filter(r->Boolean.TRUE.equals(r.get("valid"))).count());return result;
    }
    public Map<String,Object> commit(Map<String,Object> args) {
        String project=authorize(args,true);String requestId=text(args,"requestId");
        if(!requestId.matches("[A-Za-z0-9_.:-]{1,128}"))fail("IDEMPOTENCY_KEY_REQUIRED");
        if(!previewHash(args,project).equals(text(args,"previewHash"))) fail("BUG_IMPORT_PREVIEW_CHANGED");
        Map<String,Object> original=new LinkedHashMap<>(args);original.remove("previewHash");
        String importHash=operations.hash(original);
        var rows=readRows(args);List<Map<String,Object>> reports=new ArrayList<>();
        for(int i=0;i<rows.size();i++) {
            final var request=rowRequest(args,project,rows.get(i));
            String key=DigestUtils.sha256Hex(requestId+":"+(i+1)); request.setRequestId(key);
            try {
                var bug=operations.execute("bug.import.row",key,Map.of("request",request,"importHash",importHash),AgentBugDTO.class,
                        ()->authorize(args,true),()->{bugs.validateCreate(request); return bugs.importRow(request);});
                reports.add(Map.of("row",i+1,"status","CREATED","bugId",bug.getId(),"num",bug.getNum()));
            } catch(Exception invalid) {
                var safe=errors.toApiError(invalid,null); LOG.warn("Bug import row failed traceId={} row={}",safe.getTraceId(),i+1,invalid);
                reports.add(Map.of("row",i+1,"status","FAILED","code",safe.getCode(),"message",safe.getMessage(),"traceId",safe.getTraceId()));
            }
        }
        long succeeded=reports.stream().filter(r->"CREATED".equals(r.get("status"))).count();
        return Map.of("total",rows.size(),"created",succeeded,"failed",rows.size()-succeeded,"rows",reports,
                "status",succeeded==rows.size()?"SUCCEEDED":succeeded==0?"FAILED":"PARTIAL");
    }
    private String previewHash(Map<String,Object> args,String project) {
        Map<String,Object> input=new LinkedHashMap<>(args);input.remove("requestId");input.remove("previewHash");
        input.put("projectId",project);input.put("template",bugs.getTemplate(project,(String)args.get("templateId")));
        return operations.hash(input);
    }
    private AgentBugCreateRequest rowRequest(Map<String,Object> args,String project,Map<String,Object> row) {
        if (!Set.of("title","description","handleUser","customFields").containsAll(row.keySet())
                || row.entrySet().stream().anyMatch(e->!"customFields".equals(e.getKey()) && e.getValue()!=null && !(e.getValue() instanceof String))) fail("AGENT_VALIDATION_ERROR");
        var request=new AgentBugCreateRequest();request.setProjectId(project);request.setTemplateId((String)args.get("templateId"));
        request.setTitle((String)row.get("title"));request.setDescription((String)row.get("description"));
        Map<String,String> fields=new LinkedHashMap<>();
        if(row.get("customFields")!=null && !(row.get("customFields") instanceof Map<?,?>)) fail("AGENT_VALIDATION_ERROR");
        if(row.get("customFields") instanceof Map<?,?> map) {
            if(map.entrySet().stream().anyMatch(e->!(e.getKey() instanceof String) || !(e.getValue() instanceof String))) fail("AGENT_VALIDATION_ERROR");
            map.forEach((k,v)->fields.put(k.toString(),v.toString()));
        }
        if(row.containsKey("handleUser"))fields.put("handleUser",String.valueOf(row.get("handleUser")));
        request.setCustomFields(fields);return request;
    }
    @SuppressWarnings("unchecked")
    static List<Map<String,Object>> readRows(Map<String,Object> args) {
        boolean structured=args.get("rows")!=null, file=args.get("base64")!=null;
        if(structured==file)fail("AGENT_VALIDATION_ERROR");
        if(structured) {
            if(!(args.get("rows") instanceof List<?> rows) || rows.isEmpty() || rows.size()>100) {fail("BUG_TRANSFER_LIMIT_EXCEEDED");}
            var rows=(List<?>)args.get("rows");
            if(rows.stream().anyMatch(r->!(r instanceof Map))) fail("AGENT_VALIDATION_ERROR");
            if(JSON.valueToTree(rows).toString().length()>MAX_BYTES)fail("BUG_TRANSFER_LIMIT_EXCEEDED");
            return (List<Map<String,Object>>)(List<?>)rows;
        }
        String name=text(args,"fileName");String encoded=text(args,"base64");
        if(!name.toLowerCase(Locale.ROOT).endsWith(".xlsx") || encoded.length()>6990508)fail("BUG_IMPORT_FILE_INVALID");
        byte[] bytes;
        try {bytes=Base64.getDecoder().decode(encoded);}catch(IllegalArgumentException invalid){throw new MSException("BUG_IMPORT_FILE_INVALID");}
        if(bytes.length>MAX_BYTES)fail("BUG_TRANSFER_LIMIT_EXCEEDED");
        if(bytes.length<4 || bytes[0]!='P' || bytes[1]!='K' || bytes[2]!=3 || bytes[3]!=4)fail("BUG_IMPORT_FILE_INVALID");
        // Check uncompressed size and entry count before POI allocation.
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            int entries=0;long total=0;byte[] buffer=new byte[8192];
            while(zip.getNextEntry()!=null){if(++entries>500)fail("BUG_IMPORT_FILE_INVALID");int n;while((n=zip.read(buffer))!=-1){total+=n;if(total>20L*1024*1024)fail("BUG_TRANSFER_LIMIT_EXCEEDED");}}
        }catch(IOException invalid){throw new MSException("BUG_IMPORT_FILE_INVALID");}
        try(var workbook=WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if(workbook.getNumberOfSheets()!=1)fail("BUG_IMPORT_FILE_INVALID");
            Sheet sheet=workbook.getSheetAt(0);if(sheet.getLastRowNum()>100)fail("BUG_TRANSFER_LIMIT_EXCEEDED");
            Row header=sheet.getRow(0);if(header==null || header.getLastCellNum()>100)fail("BUG_IMPORT_FILE_INVALID");
            List<String> names=new ArrayList<>();DataFormatter format=new DataFormatter(Locale.ROOT);
            for(int i=0;i<header.getLastCellNum();i++) {
                String value=cell(header.getCell(i),format).trim();
                if(!(Set.of("title","description","handleUser").contains(value)||value.matches("custom:[A-Za-z0-9_-]{1,128}")) || names.contains(value))fail("BUG_IMPORT_FILE_INVALID");names.add(value);
            }
            if(!names.contains("title"))fail("BUG_IMPORT_FILE_INVALID");
            List<Map<String,Object>> rows=new ArrayList<>();
            for(int i=1;i<=sheet.getLastRowNum();i++) {
                Row row=sheet.getRow(i);if(row==null)continue;
                if(row.getLastCellNum()>names.size())fail("BUG_IMPORT_FILE_INVALID");
                Map<String,Object> result=new LinkedHashMap<>();Map<String,String> custom=new LinkedHashMap<>();
                boolean empty=true;
                for(int j=0;j<names.size();j++) {String value=cell(row.getCell(j),format);if(!value.isBlank())empty=false;String key=names.get(j);if(key.startsWith("custom:"))custom.put(key.substring(7),value);else result.put(key,value);}
                if(empty)continue;result.put("customFields",custom);rows.add(result);
            }
            if(rows.isEmpty())fail("BUG_IMPORT_FILE_INVALID");return rows;
        }catch(MSException invalid){throw invalid;}catch(Exception invalid){throw new MSException("BUG_IMPORT_FILE_INVALID");}
    }
    private static String cell(Cell cell,DataFormatter formatter) {
        if(cell==null)return "";if(cell.getCellType()==CellType.FORMULA)fail("BUG_IMPORT_FILE_INVALID");
        String value=formatter.formatCellValue(cell);if(value.length()>100000)fail("BUG_TRANSFER_LIMIT_EXCEEDED");return value;
    }
    private static String text(Map<String,Object> args,String key){Object value=args.get(key);if(!(value instanceof String s)||s.isBlank())throw new MSException("AGENT_VALIDATION_ERROR");return (String)value;}
    private static void fail(String code){throw new MSException(code);}
    private record BytesFile(String name,String contentType,byte[] bytes) implements MultipartFile {
        public String getName(){return "file";}public String getOriginalFilename(){return name;}public String getContentType(){return contentType;}
        public boolean isEmpty(){return bytes.length==0;}public long getSize(){return bytes.length;}public byte[] getBytes(){return bytes;}
        public InputStream getInputStream(){return new ByteArrayInputStream(bytes);}public void transferTo(File dest)throws IOException{java.nio.file.Files.write(dest.toPath(),bytes);}
    }
}
