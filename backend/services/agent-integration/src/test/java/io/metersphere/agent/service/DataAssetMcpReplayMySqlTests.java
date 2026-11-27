package io.metersphere.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;

@EnabledIfSystemProperty(named="quality.mysql",matches="true")
class DataAssetMcpReplayMySqlTests {
 @Test void atomicWritesReauthorizeReplayRejectChangedPayloadAndBindCurrentIdentity() {
  String schema="quality_mcp_replay_"+UUID.randomUUID().toString().replace("-","");
  var admin=new JdbcTemplate(source("mysql"));admin.execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
  try {
   var ds=source(schema);var jdbc=new JdbcTemplate(ds);
   jdbc.execute("CREATE TABLE agent_idempotency_record(id varchar(64) PRIMARY KEY,token_id varchar(64),tool_name varchar(128),request_id varchar(128),request_hash varchar(64),create_time bigint,status varchar(32),hash_version varchar(32),identity_version varchar(32),response_json longtext,completed_time bigint, UNIQUE KEY replay_key(token_id,tool_name,request_id))");
   jdbc.execute("CREATE TABLE data_asset_request(organization_id varchar(64),actor_id varchar(64),request_id varchar(128),asset_id varchar(64),PRIMARY KEY(organization_id,actor_id,request_id))");
   jdbc.execute("CREATE TABLE data_asset(id varchar(64),organization_id varchar(64),deleted boolean)");
   jdbc.update("INSERT INTO data_asset VALUES('asset','org',false)");
   var access=mock(GlobalAssetAccess.class);var business=mock(AgentBusinessAccess.class);
   var allowed=new AtomicBoolean(true);var actor=new AtomicReference<>("actor");
   when(access.authorize(eq("p"),anyString())).thenAnswer(i->{if(!allowed.get())throw AgentBusinessAccess.denied();return new GlobalAssetAccess.Scope("org",actor.get());});
   var router=new AgentDataAssetMcpWriteService(new AgentOperationExecutor(jdbc,new DataSourceTransactionManager(ds)),access,business,jdbc);
   var token=new io.metersphere.system.domain.AgentToken();token.setId("token");token.setUserId("actor");
   io.metersphere.agent.security.AgentTokenContext.set(token);
   try {
    for(String kind:List.of("create","update","delete","upload","replace_upload","mark_records_used")) {
     var arguments=new LinkedHashMap<String,Object>(Map.of("sourceProjectId","p","action",kind,"id","asset","requestId","request-"+kind,"data",Map.of("name","original")));
     var writes=new AtomicInteger();java.util.function.Supplier<Map<String,Object>> write=()->{
       if(Set.of("create","upload").contains(kind))jdbc.update("INSERT INTO data_asset_request VALUES('org','actor',?,'asset')","request-"+kind);
       return Map.of("sequence",writes.incrementAndGet());
     };
     assertEquals(router.execute(arguments,write),router.execute(arguments,write));assertEquals(1,writes.get());
     if(Set.of("create","upload").contains(kind)) {
       jdbc.update("DELETE FROM data_asset_request WHERE request_id=?","request-"+kind);
       assertThrows(io.metersphere.sdk.exception.MSException.class,()->router.execute(arguments,write));
       jdbc.update("INSERT INTO data_asset_request VALUES('org','actor',?,'asset')","request-"+kind);
     }
     var changed=new LinkedHashMap<>(arguments);changed.put("data",Map.of("name","changed"));
     assertThrows(io.metersphere.sdk.exception.MSException.class,()->router.execute(changed,write));assertEquals(1,writes.get());
     allowed.set(false);assertThrows(io.metersphere.sdk.exception.MSException.class,()->router.execute(arguments,write));allowed.set(true);
     actor.set("different-actor");assertThrows(io.metersphere.sdk.exception.MSException.class,()->router.execute(arguments,write));actor.set("actor");
     jdbc.update("UPDATE data_asset SET organization_id='other'");
     assertThrows(io.metersphere.sdk.exception.MSException.class,()->router.execute(arguments,write));
     jdbc.update("UPDATE data_asset SET organization_id='org',deleted=true");
     assertEquals(1,router.execute(arguments,write).get("sequence"));assertEquals(1,writes.get());
    }
   } finally {io.metersphere.agent.security.AgentTokenContext.clear();}
  } finally {admin.execute("DROP DATABASE "+schema);}
 }
 private static DriverManagerDataSource source(String schema) {return new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/"+schema+"?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only");}
}
