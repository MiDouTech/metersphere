package io.metersphere.functional.asset.service;

import io.metersphere.functional.asset.dto.CaseAssetSystemRequest;
import io.metersphere.functional.hub.service.DefaultHubModuleResolver;
import io.metersphere.system.service.DefaultHubProjectService;
import io.metersphere.system.utils.SessionUtils;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.sdk.exception.MSException;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(rollbackFor=Exception.class)
public class CaseAssetSystemService {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(CaseAssetSystemService.class);
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private DefaultHubProjectService defaultHubProjectService;
    @Resource private DefaultHubModuleResolver defaultHubModuleResolver;
    private String org() {
        String id=SessionUtils.getCurrentOrganizationId();
        if(StringUtils.isBlank(id)) throw new MSException("请先进入组织");
        return id;
    }
    private String hub() {
        String id=defaultHubProjectService.getDefaultProjectId();
        if(StringUtils.isBlank(id)) throw new MSException("资产存储项目未配置");
        return id;
    }
    // Serialize classification and directory changes in this organization, including unclassification.
    private void lock() {
        if(jdbcTemplate.queryForList("SELECT id FROM project WHERE id=? AND organization_id=? AND deleted=0 AND enable=1 FOR UPDATE",hub(),org()).isEmpty()) throw new MSException("资产存储项目不属于当前组织");
    }
    private String name(String value) {
        String n=StringUtils.trimToEmpty(value);
        if(n.isEmpty() || n.length()>255) throw new MSException("名称须为1至255个字符");
        return n;
    }
    private Map<String,Object> system(String id) {
        if(StringUtils.isBlank(id)) throw new MSException("请选择系统");
        var rows=jdbcTemplate.queryForList("SELECT id,name FROM functional_business_system WHERE id=? AND workspace_id=? AND enabled=1 FOR UPDATE",id,org());
        if(rows.isEmpty()) throw new MSException("系统不存在或不可用");
        return rows.getFirst();
    }
    private Map<String,Object> module(String system,String id) {
        var rows=jdbcTemplate.queryForList("SELECT id,name,parent_id parentId FROM functional_system_module WHERE id=? AND system_id=? AND workspace_id=? FOR UPDATE",id,system,org());
        if(rows.isEmpty()) throw new MSException("模块不存在或不属于当前系统");
        return rows.getFirst();
    }
    public Map<String,Object> saveSystem(String id,CaseAssetSystemRequest request) {
        lock(); String n=name(request.getName()); long now=System.currentTimeMillis();
        if(id==null) {
            id=IDGenerator.nextStr();
            update("INSERT INTO functional_business_system(id,workspace_id,name,enabled,create_user,update_user,create_time,update_time) VALUES(?,?,?,1,?,?,?,?)",id,org(),n,SessionUtils.getUserId(),SessionUtils.getUserId(),now,now);
        } else {
            system(id);
            update("UPDATE functional_business_system SET name=?,update_user=?,update_time=? WHERE id=?",n,SessionUtils.getUserId(),now,id);
        }
        return Map.of("id",id,"name",n,"caseCount",0,"modules",List.of());
    }
    public Map<String,Object> saveModule(String systemId,String id,CaseAssetSystemRequest request) {
        lock();system(systemId);String n=name(request.getName());long now=System.currentTimeMillis();
        if(id==null) {
            String parent=StringUtils.defaultIfBlank(request.getParentId(),"NONE");
            if(!"NONE".equals(parent)) module(systemId,parent);
            id=IDGenerator.nextStr();
            update("INSERT INTO functional_system_module(id,workspace_id,system_id,name,parent_id,pos,create_user,update_user,create_time,update_time) VALUES(?,?,?,?,?,0,?,?,?,?)",id,org(),systemId,n,parent,SessionUtils.getUserId(),SessionUtils.getUserId(),now,now);
        } else {
            module(systemId,id);
            update("UPDATE functional_system_module SET name=?,update_user=?,update_time=? WHERE id=?",n,SessionUtils.getUserId(),now,id);
        }
        var row=module(systemId,id);return Map.of("id",row.get("id"),"name",row.get("name"),"parentId",row.get("parentId"),"caseCount",0);
    }
    public void deleteSystem(String id) {
        lock();system(id);
        if(count("SELECT COUNT(*) FROM functional_system_module WHERE system_id=?",id)>0
            ||count("SELECT COUNT(*) FROM functional_case WHERE system_id=?",id)>0
            ||count("SELECT COUNT(*) FROM functional_case_system_relation WHERE system_id=?",id)>0)
            throw new MSException("系统存在模块或归属用例，请先处理");
        update("DELETE FROM functional_business_system WHERE id=? AND workspace_id=?",id,org());
    }
    public void deleteModule(String systemId,String id) {
        lock();system(systemId);module(systemId,id);
        if(count("SELECT COUNT(*) FROM functional_system_module WHERE parent_id=?",id)>0
            ||count("SELECT COUNT(*) FROM functional_case WHERE system_module_id=?",id)>0
            ||count("SELECT COUNT(*) FROM functional_case_system_relation WHERE system_module_id=?",id)>0)
            throw new MSException("模块存在子模块或归属用例，请先处理");
        update("DELETE FROM functional_system_module WHERE id=? AND system_id=?",id,systemId);
    }
    private int update(String sql,Object...args) {
        try { return jdbcTemplate.update(sql,args); }
        catch(org.springframework.dao.DataAccessException error) {
            String state="UNKNOWN";int code=0;
            Throwable cause=error.getMostSpecificCause();
            if(cause instanceof java.sql.SQLException db) {
                if(db.getSQLState()!=null && db.getSQLState().matches("[A-Z0-9]{5}"))state=db.getSQLState();
                code=db.getErrorCode();
            }
            String category=error instanceof org.springframework.dao.DuplicateKeyException?"DUPLICATE_NAME":"DATABASE_WRITE";
            LOG.warn("event=asset_system_write_failure stage=persist category={} time={} rootType={} sqlState={} errorCode={} orgId={} actorId={}",category,System.currentTimeMillis(),cause.getClass().getSimpleName(),state,code,org(),SessionUtils.getUserId());
            throw new MSException("DUPLICATE_NAME".equals(category)?"当前目录已存在同名系统或模块":"保存系统分类失败，请稍后重试");
        }
    }
    private long count(String sql,Object...args) {Long n=jdbcTemplate.queryForObject(sql,Long.class,args);return n==null?0:n;}
    private List<String> assetModules() {
        return defaultHubModuleResolver.listDescendantModuleIds(hub(),jdbcTemplate.queryForList("SELECT c.hub_module_id FROM case_asset_catalog c JOIN functional_case_module m ON m.id=c.hub_module_id JOIN project p ON p.id=m.project_id WHERE c.organization_id=? AND c.deleted=0 AND m.project_id=? AND p.organization_id=? AND p.deleted=0 AND p.enable=1",String.class,org(),hub(),org()));
    }
    public Map<String,Object> classify(CaseAssetSystemRequest request) {
        lock();
        if(request.getIds()==null || request.getIds().isEmpty() || request.getIds().size()>500 || request.getIds().stream().anyMatch(StringUtils::isBlank)) throw new MSException("请选择1至500条有效资产用例");
        String sid=StringUtils.trimToNull(request.getSystemId()),mid=StringUtils.trimToNull(request.getModuleId());
        if(sid==null && mid!=null) throw new MSException("模块必须归属系统");
        if(sid!=null) {system(sid);if(mid!=null)module(sid,mid);}
        var allowed=assetModules(); var ids=request.getIds().stream().distinct().sorted().toList();
        for(String id:ids) {
            var rows=jdbcTemplate.queryForList("SELECT module_id moduleId FROM functional_case WHERE id=? AND project_id=? AND deleted=0 FOR UPDATE",id,hub());
            if(rows.isEmpty() || !allowed.contains(String.valueOf(rows.getFirst().get("moduleId")))) throw new MSException("用例不存在或不属于当前组织资产目录");
        }
        for(String id:ids) {
            update("UPDATE functional_case SET system_id=?,system_module_id=?,update_user=?,update_time=? WHERE id=?",sid,mid,SessionUtils.getUserId(),System.currentTimeMillis(),id);
            update("DELETE FROM functional_case_system_relation WHERE case_id=?",id);
            if(sid!=null)update("INSERT INTO functional_case_system_relation(id,case_id,system_id,system_module_id,create_user,create_time) VALUES(?,?,?,?,?,?)",IDGenerator.nextStr(),id,sid,mid,SessionUtils.getUserId(),System.currentTimeMillis());
        }
        return Map.of("count",ids.size());
    }
    public Map<String,Object> list() {
        if(count("SELECT COUNT(*) FROM project WHERE id=? AND organization_id=? AND deleted=0 AND enable=1",hub(),org())!=1) throw new MSException("资产存储项目不属于当前组织或不可用");
        var modules=assetModules();
        var counts=new HashMap<String,Long>();var moduleCounts=new HashMap<String,Long>();long unclassified=0;
        if(!modules.isEmpty()) {
            var args=new ArrayList<Object>();args.add(hub());args.addAll(modules);
            String marks=String.join(",",Collections.nCopies(modules.size(),"?"));
            for(var row:jdbcTemplate.queryForList("SELECT system_id systemId,system_module_id moduleId,COUNT(*) caseCount FROM functional_case WHERE project_id=? AND deleted=0 AND module_id IN ("+marks+") GROUP BY system_id,system_module_id",args.toArray())) {
                String sid=(String)row.get("systemId"),mid=(String)row.get("moduleId");
                long groupedCount=((Number)row.get("caseCount")).longValue();
                if(StringUtils.isBlank(sid))unclassified+=groupedCount;else counts.merge(sid,groupedCount,Long::sum);
                if(StringUtils.isNotBlank(mid))moduleCounts.merge(mid,groupedCount,Long::sum);
            }
        }
        var systems=jdbcTemplate.queryForList("SELECT id,name FROM functional_business_system WHERE workspace_id=? AND enabled=1 ORDER BY name,id",org());
        var modulesBySystem=new HashMap<String,List<Map<String,Object>>>();
        for(var m:jdbcTemplate.queryForList("SELECT id,name,parent_id parentId,system_id systemId FROM functional_system_module WHERE workspace_id=? ORDER BY pos,id",org())) {
            modulesBySystem.computeIfAbsent((String)m.get("systemId"),key->new ArrayList<>()).add(m);
        }
        var result=new ArrayList<Map<String,Object>>();
        for(var s:systems) {
            var children=modulesBySystem.getOrDefault((String)s.get("id"),List.of());
            var canonicalModules=new ArrayList<Map<String,Object>>();
            for(var m:children)canonicalModules.add(Map.of("id",m.get("id"),"name",m.get("name"),"parentId",m.get("parentId"),"caseCount",moduleCounts.getOrDefault(m.get("id"),0L)));
            result.add(Map.of("id",s.get("id"),"name",s.get("name"),"caseCount",counts.getOrDefault(s.get("id"),0L),"modules",canonicalModules));
        }
        return Map.of("systems",result,"unclassifiedCount",unclassified);
    }
}
