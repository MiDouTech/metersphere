package io.metersphere.agent.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.util.*;

/** Same real source projection tests on native MySQL JSON operators, never H2 JSON aliases. */
@EnabledIfSystemProperty(named="quality.mysql",matches="true")
public class EvidenceCatalogMySqlTests extends EvidenceCatalogServiceTests {
    private final List<SingleConnectionDataSource> connections=new ArrayList<>();
    private final List<String> schemas=new ArrayList<>();
    @Override protected boolean nativeJson(){return true;}
    @Override protected JdbcTemplate database(){
        var source=new SingleConnectionDataSource("jdbc:mysql://127.0.0.1:13326/quality_verify?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only",true);
        connections.add(source);var db=new JdbcTemplate(source);
        String schema="evidence_catalog_"+UUID.randomUUID().toString().replace("-","");
        if(!schema.matches("evidence_catalog_[a-f0-9]{32}"))throw new IllegalStateException("Invalid isolated test schema");
        db.execute("CREATE DATABASE "+schema);schemas.add(schema);db.execute("USE "+schema);initialize(db);return db;
    }
    @AfterEach void dropOwnSchemas(){
        for(int i=0;i<schemas.size();i++){
            String schema=schemas.get(i);if(!schema.matches("evidence_catalog_[a-f0-9]{32}"))throw new IllegalStateException("Refuse unrelated schema cleanup");
            new JdbcTemplate(connections.get(i)).execute("DROP DATABASE "+schema);
        }
        connections.forEach(SingleConnectionDataSource::destroy);
    }
}
