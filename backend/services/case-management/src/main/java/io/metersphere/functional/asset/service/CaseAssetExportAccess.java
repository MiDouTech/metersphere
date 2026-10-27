package io.metersphere.functional.asset.service;

import io.metersphere.functional.hub.service.DefaultHubModuleResolver;
import io.metersphere.functional.request.FunctionalCaseExportRequest;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.dto.sdk.SessionUser;
import io.metersphere.system.service.UserLoginService;
import io.metersphere.system.utils.SessionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Organization asset permission never grants unrestricted access to the hub project. */
@Service
public class CaseAssetExportAccess {
    public static final String EXPORT="CASE_ASSET:READ+EXPORT";
    private final JdbcTemplate jdbc;private final UserLoginService users;private final DefaultHubModuleResolver modules;
    public CaseAssetExportAccess(JdbcTemplate jdbc,UserLoginService users,DefaultHubModuleResolver modules){this.jdbc=jdbc;this.users=users;this.modules=modules;}

    public Map<String,Object> catalog(String id) {
        var rows=jdbc.queryForList("SELECT c.organization_id organizationId,c.hub_module_id moduleId,m.project_id projectId FROM case_asset_catalog c JOIN functional_case_module m ON m.id=c.hub_module_id JOIN project p ON p.id=m.project_id AND p.deleted=0 AND p.enable=1 WHERE c.id=? AND c.deleted=0",id);
        if(rows.size()!=1)throw denied();return rows.getFirst();
    }

    public void authorizeCatalog(String id,String user) {
        var catalog=catalog(id);var dto=users.getUserDTO(user);
        if(dto==null||!SessionUtils.hasPermission(SessionUser.fromUser(dto,""),(String)catalog.get("organizationId"),null,EXPORT))throw denied();
    }

    public void authorize(FunctionalCaseExportRequest request,String user) {
        if (!"PROJECT".equals(request.getDimension()) || (request.getProjectIds()!=null && !request.getProjectIds().isEmpty()) || request.isSelectAll()) throw new MSException("请使用当前目录内明确勾选的资产用例导出");
        var catalog=catalog(request.getAssetCatalogId());
        var dto=users.getUserDTO(user);
        if(dto==null||!SessionUtils.hasPermission(SessionUser.fromUser(dto,""),(String)catalog.get("organizationId"),null,EXPORT))throw denied();
        if(!Objects.equals(catalog.get("projectId"),request.getProjectId()))throw denied();
        List<String> allowed=modules.listDescendantModuleIds(request.getProjectId(),List.of((String)catalog.get("moduleId")));
        if(request.getModuleIds()==null||request.getModuleIds().isEmpty())request.setModuleIds(allowed);
        else if(!allowed.containsAll(request.getModuleIds()))throw denied();
        if(!request.isSelectAll())validateIds(request.getAssetCatalogId(),request.getSelectIds());
    }

    public void validateIds(String catalogId,List<String> ids) {
        if(ids==null||ids.isEmpty())throw new MSException("请选择要导出的资产用例");
        var catalog=catalog(catalogId);
        List<String> allowed=modules.listDescendantModuleIds((String)catalog.get("projectId"),List.of((String)catalog.get("moduleId")));
        for(int i=0;i<ids.size();i+=500) {
            var batch=ids.subList(i,Math.min(i+500,ids.size()));
            var args=new ArrayList<Object>(batch);args.addAll(allowed);args.add(catalog.get("projectId"));
            Long count=jdbc.queryForObject("SELECT COUNT(*) FROM functional_case WHERE id IN ("+String.join(",",Collections.nCopies(batch.size(),"?"))+") AND module_id IN ("+String.join(",",Collections.nCopies(allowed.size(),"?"))+") AND project_id=? AND deleted=0",Long.class,args.toArray());
            if(count==null||count!=batch.stream().distinct().count())throw denied();
        }
    }

    private MSException denied(){return new MSException("资产导出范围已变化或当前账号无权访问");}
}
