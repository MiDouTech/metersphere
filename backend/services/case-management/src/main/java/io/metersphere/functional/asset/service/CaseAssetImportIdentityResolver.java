package io.metersphere.functional.asset.service;

import io.metersphere.functional.hub.service.DefaultHubModuleResolver;
import io.metersphere.functional.request.FunctionalCaseImportRequest;
import io.metersphere.sdk.exception.MSException;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.ArrayList;

/** Asset-library name conflicts are scoped to the authorized catalog, never another project/catalog. */
@Service
public class CaseAssetImportIdentityResolver {
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private DefaultHubModuleResolver moduleResolver;

    public void lockCatalog(FunctionalCaseImportRequest request) {
        if (jdbcTemplate.queryForList("SELECT id FROM functional_case_module WHERE id=? AND project_id=? FOR UPDATE",
                request.getModuleId(), request.getProjectId()).isEmpty()) throw new MSException("导入目标目录不存在");
    }

    public String resolve(FunctionalCaseImportRequest request, String name) {
        var modules = moduleResolver.listDescendantModuleIds(request.getProjectId(), java.util.List.of(request.getModuleId()));
        if (modules.isEmpty()) throw new MSException("导入目标目录不存在");
        var args = new ArrayList<Object>();
        args.add(request.getProjectId()); args.add(name); args.addAll(modules);
        var ids = jdbcTemplate.queryForList("SELECT id FROM functional_case WHERE project_id=? AND name=? AND deleted=0 AND latest=1 AND module_id IN ("
                + String.join(",", Collections.nCopies(modules.size(), "?")) + ") ORDER BY id LIMIT 2", String.class, args.toArray());
        if (ids.size() > 1) throw new MSException("目录内存在多个同名用例，请先明确目标用例后再导入");
        return ids.isEmpty() ? null : ids.getFirst();
    }
}
