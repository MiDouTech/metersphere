package io.metersphere.agent.service;

import io.metersphere.sdk.exception.MSException;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Multiplexed data tools reauthorize current resources before every atomic replay. */
@Service
public class AgentDataAssetMcpWriteService {
  private static final Set<String> READS=Set.of("list","detail","content","versions","job","download","legacy_inventory","records","copy_records");
  private static final Set<String> WRITES=Set.of("create","update","delete","batch","retry_job","upload","replace_upload","legacy_import","mark_records_used");
  private final AgentOperationExecutor operations;
  private final GlobalAssetAccess global;
  private final AgentBusinessAccess access;
  private final JdbcTemplate jdbc;
  public AgentDataAssetMcpWriteService(AgentOperationExecutor operations,GlobalAssetAccess global,AgentBusinessAccess access,JdbcTemplate jdbc) {
    this.operations=operations;this.global=global;this.access=access;this.jdbc=jdbc;
  }
  public static boolean isReadAction(Object action) { return action instanceof String name && READS.contains(name); }
  public Map<String,Object> execute(Map<String,Object> raw,Supplier<Map<String,Object>> action) {
    String kind=text(raw,"action");
    if(READS.contains(kind))return action.get(); // Underlying readers retain their own data authorization.
    if(!WRITES.contains(kind))throw new MSException("AGENT_VALIDATION_ERROR");
    var args=new LinkedHashMap<>(raw);
    Object inner=args.get("data") instanceof Map<?,?> map?map.get("requestId"):null;
    if(args.get("requestId")==null && inner instanceof String key)args.put("requestId",key);
    String request=text(args,"requestId");
    if(inner!=null && !request.equals(inner))throw new MSException("IDEMPOTENCY_CONFLICT");
    // Batch keeps its per-row transactions and persists its own request/job replay state.
    if(kind.equals("batch") || kind.equals("retry_job")) { authorize(kind,args); return action.get(); }
    var identity=global.authorize(text(args,"sourceProjectId"),"mark_records_used".equals(kind)?"TEST_DATA_ASSET:READ":"TEST_DATA_ASSET:READ+UPDATE");
    args.put("_replayOrganization",identity.organizationId());args.put("_replayActor",identity.actorId());
    // All mutation results and their database changes commit together; upload dispatch uses afterCommit.
    return operations.execute("data_asset."+kind,request,args,Map.class,
      ()->authorize(kind,args),()->action.get());
  }
  private void authorize(String kind,Map<String,Object> args) {
    String project=text(args,"sourceProjectId");
    var scope=global.authorize(project,"mark_records_used".equals(kind)?"TEST_DATA_ASSET:READ":"TEST_DATA_ASSET:READ+UPDATE");
    if(Set.of("create","upload").contains(kind)) {
      var mapped=jdbc.queryForList("SELECT asset_id FROM data_asset_request WHERE organization_id=? AND actor_id=? AND request_id=?",String.class,scope.organizationId(),scope.actorId(),text(args,"requestId"));
      if(mapped.size()>1)throw AgentBusinessAccess.denied();
      if(!mapped.isEmpty())asset(scope,mapped.getFirst());
      else {
        // A committed receipt without its atomically persisted domain mapping is unsafe to disclose.
        var token=io.metersphere.agent.security.AgentTokenContext.get();
        if(token!=null && operations.completed("data_asset."+kind,text(args,"requestId"),args,Map.class,()->{}).isPresent())
          throw AgentBusinessAccess.denied();
      }
    }
    if(Set.of("update","delete","replace_upload","mark_records_used").contains(kind))asset(scope,text(args,"id"));
    if("batch".equals(kind)) {
      if(!(args.get("items") instanceof List<?> items) || items.isEmpty() || items.size()>100)throw new MSException("AGENT_VALIDATION_ERROR");
      for(Object item:items) {
        if(!(item instanceof Map<?,?> row) || !(row.get("id") instanceof String id))throw new MSException("AGENT_VALIDATION_ERROR");
        asset(scope,id);
      }
    }
    if("retry_job".equals(kind)) {
      String id=text(args,"id");
      Long count=jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM data_asset_parse_job WHERE id=? AND organization_id=? AND actor_id=?)+(SELECT COUNT(*) FROM data_asset_batch_job WHERE id=? AND organization_id=? AND actor_id=?)",Long.class,id,scope.organizationId(),scope.actorId(),id,scope.organizationId(),scope.actorId());
      if(count==null || count!=1)throw AgentBusinessAccess.denied();
    }
    if("legacy_import".equals(kind)) {
      if(!access.userHasPermission(scope.actorId(),project,"PROJECT_FILE_MANAGEMENT:READ"))throw AgentBusinessAccess.denied();
      String id=text(args,"id");
      Long count=jdbc.queryForObject("SELECT COUNT(*) FROM file_metadata WHERE project_id=? AND (id=? OR ref_id=?)",Long.class,project,id,id);
      if(count==null || count<1)throw AgentBusinessAccess.denied();
    }
  }
  private void asset(GlobalAssetAccess.Scope scope,String id) {
    // Soft-deleted rows remain owned: successful deletes must be replayable without repeating a write.
    Long count=jdbc.queryForObject("SELECT COUNT(*) FROM data_asset WHERE id=? AND organization_id=?",Long.class,id,scope.organizationId());
    if(count==null || count!=1)throw AgentBusinessAccess.denied();
  }
  private static String text(Map<String,Object> args,String key) {
    if(!(args.get(key) instanceof String value)||value.isBlank()||value.length()>128)throw new MSException("AGENT_VALIDATION_ERROR");
    return value;
  }
}
