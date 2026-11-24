package io.metersphere.agent.service;

import io.metersphere.agent.dto.TestAssetCatalogItemDTO;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.security.AgentTokenProjectAccess;
import io.metersphere.bug.dto.request.BugFileSourceRequest;
import io.metersphere.bug.service.BugAttachmentService;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.utils.SessionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Read-through projection of real evidence. Never copies uploads or treats staging as publication. */
@Service
public class EvidenceCatalogService {
    private final JdbcTemplate jdbc;
    private final AgentBusinessAccess access;
    private final AgentProjectService projects;
    private final org.springframework.beans.factory.ObjectProvider<ExternalExecutorService> executors;
    private final org.springframework.beans.factory.ObjectProvider<BugAttachmentService> bugs;
    private final org.springframework.beans.factory.ObjectProvider<AgentExecutionArtifactService> legacy;
    public EvidenceCatalogService(JdbcTemplate jdbc,AgentBusinessAccess access,AgentProjectService projects,
            org.springframework.beans.factory.ObjectProvider<ExternalExecutorService> executors,org.springframework.beans.factory.ObjectProvider<BugAttachmentService> bugs,org.springframework.beans.factory.ObjectProvider<AgentExecutionArtifactService> legacy){
        this.jdbc=jdbc;this.access=access;this.projects=projects;this.executors=executors;this.bugs=bugs;this.legacy=legacy;
    }
    public record Content(String name,String contentType,byte[] bytes){
        /** MIME is determined from original decoded bytes, never a stored declaration. */
        public String inlineImageType(){
            try{return BugImageValidator.validate(bytes);}catch(MSException invalid){return null;}
        }
    }
    private record Query(String sql,List<Object> arguments){}
    /** Only this service can create the fixed SQL fragment; every identity/project value stays bound. */
    public static final class AuthorizationSql {
        private final String sql;
        private final List<Object> arguments;
        private AuthorizationSql(String sql,List<Object> arguments){this.sql=sql;this.arguments=List.copyOf(arguments);}
        public String getSql(){return sql;}
        public List<Object> getArguments(){return arguments;}
    }
    public AuthorizationSql authorization(String project){
        if(!canRead(project))return new AuthorizationSql("SELECT CAST(NULL AS CHAR) id WHERE 1=0",List.of());
        Query source=query(project);String sql="SELECT proof.id FROM ("+source.sql+") proof";
        for(int i=0;i<source.arguments.size();i++)sql=sql.replaceFirst("\\?",java.util.regex.Matcher.quoteReplacement("#{evidenceAuthorization.arguments["+i+"]}"));
        return new AuthorizationSql(sql,source.arguments);
    }
    private static final String METADATA_JOINS=" LEFT JOIN test_asset_metadata m ON m.project_id=e.project_id AND m.asset_type='EVIDENCE' AND m.asset_id=e.id"
            +" LEFT JOIN test_asset_category c ON c.id=m.category_id AND c.deleted=0";
    private static final String CATALOG_FIELDS="SELECT e.*,COALESCE(m.creation_source,'UNKNOWN') creation_source,m.category_id,c.name category_name,c.path category_path,m.source_reference_type,m.source_reference_id";

    private String user(String project){
        var token=AgentTokenContext.get();
        if(token!=null){var current=access.activeToken();if(!AgentTokenProjectAccess.allows(current,project))throw AgentBusinessAccess.denied();return current.getUserId();}
        String actor=SessionUtils.getUserId();if(actor==null||actor.isBlank())throw AgentBusinessAccess.denied();return actor;
    }
    private Query query(String project){
        String actor=user(project);var sql=new ArrayList<String>();var args=new ArrayList<Object>();
        if(access.userHasPermission(actor,project,"FUNCTIONAL_CASE:READ")){
            sql.add("""
                SELECT CONCAT('PERSONAL:',e.id) id,e.project_id,'PERSONAL_SUBMISSION' source_kind,
                  e.file_name name,'CASE_EXECUTION' category,'PUBLISHED' status,e.case_id related_id,
                  e.user_id owner,e.create_time update_time,e.sha256 source_version,e.content_type
                FROM agent_case_evidence e JOIN functional_case c ON c.id=e.case_id AND c.project_id=e.project_id AND c.deleted=0
                WHERE e.project_id=? AND EXISTS(SELECT 1 FROM agent_case_submission_evidence r
                  JOIN agent_case_submission s ON s.id=r.submission_id AND s.project_id=e.project_id AND s.case_id=e.case_id
                  WHERE r.evidence_id=e.id AND e.submission_id=s.id AND s.gate_status='ACCEPTED')
                """);args.add(project);
        }
        if(access.userHasPermission(actor,project,"BUSINESS_ASSET:READ")){
            var token=AgentTokenContext.get();
            sql.add("""
                SELECT CONCAT('EXECUTOR:',e.id) id,s.project_id,'EXECUTOR' source_kind,
                  CONCAT('executor-',e.id,CASE WHEN e.content_type='image/jpeg' THEN '.jpg' ELSE '.png' END) name,
                  'BUSINESS_OBSERVATION' category,'PUBLISHED' status,s.id related_id,s.actor_id owner,
                  e.create_time update_time,e.content_hash source_version,e.content_type
                FROM external_executor_evidence e JOIN external_executor_session s ON s.id=e.session_id
                WHERE s.project_id=?
                """+(token==null?"":" AND s.token_id=? AND s.actor_id=? ")+"""
                AND (EXISTS(SELECT 1 FROM external_executor_event ev WHERE ev.session_id=s.id AND ev.evidence_id=e.id)
                  OR EXISTS(SELECT 1 FROM business_asset_version v WHERE v.project_id=s.project_id
                    AND v.status NOT IN ('CONFLICT','REJECTED')
                    AND JSON_CONTAINS(JSON_EXTRACT(v.snapshot_json,'$.branches[*].evidenceRefs'),JSON_QUOTE(e.id))
                    AND (NOT EXISTS(SELECT 1 FROM business_asset_reader br WHERE br.asset_id=v.asset_id)
                      OR EXISTS(SELECT 1 FROM business_asset_reader br WHERE br.asset_id=v.asset_id AND br.user_id=?))))
                """);args.add(project);if(token!=null){args.add(token.getId());args.add(actor);}args.add(actor);
        }
        if(access.userHasPermission(actor,project,"PROJECT_BUG:READ")){
            sql.add("""
                SELECT CONCAT('BUG:',a.id) id,b.project_id,'BUG_ATTACHMENT' source_kind,a.file_name name,
                  'BUG_ATTACHMENT' category,'PUBLISHED' status,b.id related_id,a.create_user owner,
                  a.create_time update_time,a.file_id source_version,
                  CASE WHEN LOWER(a.file_name) LIKE '%.png' THEN 'image/png' ELSE 'image/jpeg' END content_type
                FROM bug_local_attachment a JOIN bug b ON b.id=a.bug_id AND b.deleted=0
                WHERE b.project_id=? AND a.source='ATTACHMENT'
                  AND (LOWER(a.file_name) LIKE '%.png' OR LOWER(a.file_name) LIKE '%.jpg' OR LOWER(a.file_name) LIKE '%.jpeg')
                """);args.add(project);
        }
        if(access.userHasPermission(actor,project,"AI_EXECUTION:READ")){
            sql.add("""
                SELECT ar.id,t.project_id,'LEGACY_ARTIFACT' source_kind,ar.file_name name,ar.purpose category,
                  ar.status,ar.task_id related_id,ar.create_user owner,ar.create_time update_time,
                  ar.sha256 source_version,ar.content_type
                FROM ai_execution_artifact ar JOIN ai_execution_task t ON t.id=ar.task_id
                WHERE t.project_id=? AND ar.status='AVAILABLE' AND ar.redacted=1
                """);args.add(project);
        }
        if(sql.isEmpty())throw AgentBusinessAccess.denied();
        return new Query(String.join(" UNION ALL ",sql),args);
    }
    public List<TestAssetCatalogItemDTO> rows(String project){return rows(project,null);}
    private List<TestAssetCatalogItemDTO> rows(String project,String id){
        Query q=query(project);var params=new ArrayList<>(q.arguments);
        String sql=CATALOG_FIELDS+" FROM ("+q.sql+") e"+METADATA_JOINS;
        if(id!=null){sql+=" WHERE e.id=?";params.add(id);}
        return jdbc.query(sql,(rs,n)->{
            return row(rs);
        },params.toArray());
    }
    private TestAssetCatalogItemDTO row(java.sql.ResultSet rs) throws java.sql.SQLException {
            var row=new TestAssetCatalogItemDTO();row.setId(rs.getString("id"));row.setProjectId(rs.getString("project_id"));
            row.setAssetType("EVIDENCE");row.setSourceKind(rs.getString("source_kind"));row.setName(rs.getString("name"));
            row.setCategory(rs.getString("category"));row.setStatus(rs.getString("status"));row.setRelatedId(rs.getString("related_id"));
            row.setOwner(rs.getString("owner"));row.setUpdateTime(rs.getLong("update_time"));row.setSourceVersion(rs.getString("source_version"));
            row.setContentType(rs.getString("content_type"));row.setSummary(row.getSourceKind()+"; source="+row.getRelatedId());
            row.setCreationSource(rs.getString("creation_source"));row.setCategoryId(rs.getString("category_id"));
            row.setCategoryName(rs.getString("category_name"));row.setCategoryPath(rs.getString("category_path"));
            row.setSourceReferenceType(rs.getString("source_reference_type"));row.setSourceReferenceId(rs.getString("source_reference_id"));
            return row;
    }
    public io.metersphere.system.utils.Pager<List<TestAssetCatalogItemDTO>> search(String project,String keyword,String status,
            Long updatedAfter,List<String> sources,String category,boolean descendants,int page,int size){
        Query q=query(project);var args=new ArrayList<>(q.arguments);
        String from=" FROM ("+q.sql+") e"+METADATA_JOINS+" WHERE 1=1";
        if(keyword!=null&&!keyword.isBlank()){from+=" AND (LOWER(e.name) LIKE ? OR LOWER(e.id) LIKE ? OR LOWER(e.related_id) LIKE ?)";String key="%"+keyword.toLowerCase(java.util.Locale.ROOT)+"%";args.add(key);args.add(key);args.add(key);}
        if(status!=null&&!status.isBlank()){from+=" AND e.status=?";args.add(status.toUpperCase(java.util.Locale.ROOT));}
        if(updatedAfter!=null){from+=" AND e.update_time>=?";args.add(updatedAfter);}
        if(sources!=null&&!sources.isEmpty()){from+=" AND COALESCE(m.creation_source,'UNKNOWN') IN ("+String.join(",",Collections.nCopies(sources.size(),"?"))+")";args.addAll(sources);}
        if(category!=null&&!category.isBlank()){
            from+=descendants?" AND (m.category_id=? OR c.path LIKE CONCAT((SELECT path FROM test_asset_category WHERE id=? AND deleted=0),' / %'))":" AND m.category_id=?";
            args.add(category);if(descendants)args.add(category);
        }
        long total=jdbc.queryForObject("SELECT COUNT(*)"+from,Long.class,args.toArray());
        var paging=new ArrayList<>(args);paging.add(size);paging.add((long)(page-1)*size);
        var rows=jdbc.query(CATALOG_FIELDS+from+" ORDER BY e.update_time DESC,e.id LIMIT ? OFFSET ?",(rs,n)->row(rs),paging.toArray());
        return new io.metersphere.system.utils.Pager<>(rows,total,size,page);
    }
    public TestAssetCatalogItemDTO detail(String project,String id){
        // Same source checks on every read, including already known IDs and expired/revoked callers.
        return rows(project,id).stream().filter(r->r.getId().equals(id)).findFirst().orElseThrow(AgentBusinessAccess::denied);
    }
    public boolean canRead(String project){
        String actor=user(project);
        return List.of("FUNCTIONAL_CASE:READ","BUSINESS_ASSET:READ","PROJECT_BUG:READ","AI_EXECUTION:READ")
                .stream().anyMatch(permission->access.userHasPermission(actor,project,permission));
    }
    public io.metersphere.system.utils.Pager<List<io.metersphere.agent.dto.TestAssetVersionDTO>> versions(
            String project,String assetId,String keyword,List<String> sources,String category,boolean descendants,int page,int size){
        Query q=query(project);var args=new ArrayList<>(q.arguments);
        String from=" FROM ("+q.sql+") e JOIN test_asset_version v ON v.project_id=e.project_id AND v.asset_type='EVIDENCE' AND v.asset_id=e.id"
                +" LEFT JOIN test_asset_metadata m ON m.project_id=e.project_id AND m.asset_type='EVIDENCE' AND m.asset_id=e.id"
                +" LEFT JOIN test_asset_category c ON c.id=m.category_id AND c.deleted=0 WHERE 1=1";
        if(assetId!=null&&!assetId.isBlank()){from+=" AND v.asset_id=?";args.add(assetId);}
        if(keyword!=null&&!keyword.isBlank()){from+=" AND (LOWER(e.name) LIKE ? OR LOWER(e.id) LIKE ?)";String k="%"+keyword.toLowerCase(java.util.Locale.ROOT)+"%";args.add(k);args.add(k);}
        if(sources!=null&&!sources.isEmpty()){from+=" AND COALESCE(m.creation_source,'UNKNOWN') IN ("+String.join(",",Collections.nCopies(sources.size(),"?"))+")";args.addAll(sources);}
        if(category!=null&&!category.isBlank()){
            from+=descendants?" AND (m.category_id=? OR c.path LIKE CONCAT((SELECT path FROM test_asset_category WHERE id=? AND deleted=0),' / %'))":" AND m.category_id=?";
            args.add(category);if(descendants)args.add(category);
        }
        long total=jdbc.queryForObject("SELECT COUNT(*)"+from,Long.class,args.toArray());args.add(size);args.add((long)(page-1)*size);
        var values=jdbc.query("SELECT v.*,e.name asset_name,COALESCE(m.creation_source,'UNKNOWN') creation_source,m.category_id,c.name category_name,c.path category_path"+from+" ORDER BY v.created_at DESC,v.id DESC LIMIT ? OFFSET ?",(rs,n)->{
            var value=new io.metersphere.agent.dto.TestAssetVersionDTO();value.setId(rs.getString("id"));value.setProjectId(project);value.setAssetType("EVIDENCE");value.setAssetId(rs.getString("asset_id"));
            value.setAssetName(rs.getString("asset_name"));value.setVersionNo(rs.getInt("version_no"));value.setSourceVersion(rs.getString("source_version"));value.setContentHash(rs.getString("content_hash"));value.setContentSnapshot(rs.getString("content_snapshot"));value.setStatus(rs.getString("status"));value.setCreatedBy(rs.getString("created_by"));value.setCreatedAt(rs.getLong("created_at"));value.setPublishedBy(rs.getString("published_by"));value.setPublishedAt(rs.getObject("published_at")==null?null:rs.getLong("published_at"));value.setCreationSource(rs.getString("creation_source"));value.setCategoryId(rs.getString("category_id"));value.setCategoryName(rs.getString("category_name"));value.setCategoryPath(rs.getString("category_path"));return value;
        },args.toArray());
        return new io.metersphere.system.utils.Pager<>(values,total,size,page);
    }
    public Content content(String project,String id){
        String resolved=projects.resolveProjectId(project);if(resolved!=null)project=resolved;
        var row=detail(project,id);byte[] bytes;String type=row.getContentType();
        switch(row.getSourceKind()){
            case "PERSONAL_SUBMISSION" -> {
                var values=jdbc.query("SELECT payload FROM agent_case_evidence WHERE project_id=? AND id=?",(rs,n)->rs.getBytes(1),project,id.substring(9));
                if(values.size()!=1)throw AgentBusinessAccess.denied();bytes=values.getFirst();
            }
            case "EXECUTOR" -> {var value=executors.getObject().evidence(project,id.substring(9));bytes=Base64.getDecoder().decode(value.get("content"));type=value.get("contentType");}
            case "BUG_ATTACHMENT" -> {
                var files=jdbc.queryForList("SELECT a.file_id FROM bug_local_attachment a JOIN bug b ON b.id=a.bug_id AND b.project_id=? AND b.deleted=0 WHERE a.id=? AND a.bug_id=? AND a.source='ATTACHMENT'",String.class,
                        project,id.substring(4),row.getRelatedId());
                if(files.size()!=1)throw AgentBusinessAccess.denied();
                var request=new BugFileSourceRequest();request.setProjectId(project);request.setBugId(row.getRelatedId());
                request.setFileId(files.getFirst());request.setAssociated(false);
                bytes=bugs.getObject().downloadOrPreview(request).getBody();if(bytes==null)throw AgentBusinessAccess.denied();
                type=BugImageValidator.validate(bytes);
            }
            case "LEGACY_ARTIFACT" -> {var response=legacy.getObject().download(row.getRelatedId(),id);bytes=response.getBody();}
            default -> throw AgentBusinessAccess.denied();
        }
        if(bytes==null||bytes.length==0)throw new MSException("证据不存在或已超过保留期限");
        // Re-authorize after storage access to avoid returning a revoked resource.
        detail(project,id);return new Content(row.getName(),type,bytes);
    }
}
