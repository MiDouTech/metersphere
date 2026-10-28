package io.metersphere.agent.service;

import io.metersphere.functional.domain.FunctionalCaseBlob;
import io.metersphere.functional.mapper.FunctionalCaseBlobMapper;
import io.metersphere.system.config.interceptor.FunctionalCaseInterceptor;
import io.metersphere.system.interceptor.MybatisInterceptor;
import io.metersphere.sdk.util.CompressUtils;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="quality.mysql",matches="true")
class FunctionalCaseBlobRoundTripTests {
    @Test void actualMapperPluginKeepsOneLayerAcrossReadEditAndCopy() throws Exception {
        var admin=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only"));
        String schema="body_roundtrip_"+UUID.randomUUID().toString().replace("-","");
        admin.execute("CREATE DATABASE "+schema);
        try {
            var ds=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/"+schema+"?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only");
            var jdbc=new JdbcTemplate(ds);
            jdbc.execute("CREATE TABLE functional_case_blob(id VARCHAR(50) PRIMARY KEY,steps LONGBLOB,text_description LONGBLOB,expected_result LONGBLOB,prerequisite LONGBLOB,description LONGBLOB)");
            var config=new Configuration(new Environment("quality",new JdbcTransactionFactory(),ds));
            var plugin=new MybatisInterceptor();
            plugin.setInterceptorConfigList(new FunctionalCaseInterceptor().functionalCaseCompressConfigs());
            config.addInterceptor(plugin);config.addMapper(FunctionalCaseBlobMapper.class);
            var factory=new SqlSessionFactoryBuilder().build(config);
            byte[] original="[{\"description\":\"hello\"}]".getBytes(StandardCharsets.UTF_8);
            try(var session=factory.openSession(true)) {
                var mapper=session.getMapper(FunctionalCaseBlobMapper.class);
                var blob=new FunctionalCaseBlob();blob.setId("one");blob.setSteps(original);
                mapper.insertSelective(blob);
                for(int i=0;i<3;i++) {
                    session.clearCache();
                    var loaded=mapper.selectByPrimaryKey("one");
                    assertArrayEquals(original,loaded.getSteps());
                    mapper.updateByPrimaryKeySelective(loaded);
                    byte[] stored=jdbc.queryForObject("SELECT steps FROM functional_case_blob WHERE id='one'",byte[].class);
                    assertArrayEquals(original,(byte[])CompressUtils.unzip(stored));
                }
                var copy=mapper.selectByPrimaryKey("one");copy.setId("two");mapper.insertSelective(copy);
                session.clearCache();assertArrayEquals(original,mapper.selectByPrimaryKey("two").getSteps());
                jdbc.update("INSERT INTO functional_case_blob(id,steps) VALUES ('legacy',?)",CompressUtils.zip(CompressUtils.zip(original)));
                session.clearCache();assertArrayEquals(original,mapper.selectByPrimaryKey("legacy").getSteps());
                jdbc.update("INSERT INTO functional_case_blob(id,steps) VALUES ('bad',?)",new byte[]{80,75,3,4,0});
                session.clearCache();assertThrows(RuntimeException.class,()->mapper.selectByPrimaryKey("bad"));
            }
        } finally {admin.execute("DROP DATABASE "+schema);}
    }
}
