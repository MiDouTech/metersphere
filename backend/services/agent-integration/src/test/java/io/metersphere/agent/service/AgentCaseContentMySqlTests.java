package io.metersphere.agent.service;

import io.metersphere.agent.mapper.TestAssetMapper;
import io.metersphere.agent.dto.TestAssetVersionDTO;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import io.metersphere.sdk.util.CompressUtils;
import io.metersphere.sdk.util.JSON;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="quality.mysql",matches="true")
class AgentCaseContentMySqlTests {
    @Test void productionMapperReadsRawZipAndPublishedReaderPreservesBothFormats() throws Exception {
        var ds=new SingleConnectionDataSource("jdbc:mysql://127.0.0.1:13326/quality_verify?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only",true);
        try {
            var jdbc=new JdbcTemplate(ds);
            jdbc.execute("CREATE TEMPORARY TABLE functional_case(id VARCHAR(64),project_id VARCHAR(64),ref_id VARCHAR(64),name VARCHAR(64),case_edit_type VARCHAR(16),tags TEXT,latest BOOLEAN,deleted BOOLEAN)");
            jdbc.execute("CREATE TEMPORARY TABLE functional_case_blob(id VARCHAR(64),steps LONGBLOB,text_description LONGBLOB,expected_result LONGBLOB,prerequisite LONGBLOB)");
            var bean=new SqlSessionFactoryBean();bean.setDataSource(ds);bean.setMapperLocations(new ClassPathResource("io/metersphere/agent/mapper/TestAssetMapper.xml"));
            try(var session=bean.getObject().openSession()) {
                var mapper=session.getMapper(TestAssetMapper.class);var reader=new AgentCaseContentReader();
                for(String type:List.of("STEP","TEXT")) {
                    jdbc.update("INSERT INTO functional_case VALUES (?, 'p1', ?, 'content', ?, '[]', 1, 0)",type,type,type);
                    String steps="[{\"num\":1,\"desc\":\"打开订单\",\"result\":\"订单显示\"}]";
                    byte[] raw=(byte[])CompressUtils.zip(steps.getBytes(StandardCharsets.UTF_8));
                    jdbc.update("INSERT INTO functional_case_blob VALUES (?,?,?,?,?)",type,raw,CompressUtils.zip("操作".getBytes(StandardCharsets.UTF_8)),CompressUtils.zip("预期".getBytes(StandardCharsets.UTF_8)),null);
                    var source=mapper.selectExecutableSnapshot("p1","CASE",type);
                    assertArrayEquals(raw,source.getCaseStepsRaw());
                    Map<String,Object> content=JSON.parseObject(source.getRawContentJson(),Map.class);
                    content.put("steps",reader.decodeRaw(source.getCaseStepsRaw()));content.put("textDescription",reader.decodeRaw(source.getCaseDescriptionRaw()));content.put("expectedResult",reader.decodeRaw(source.getCaseExpectedRaw()));
                    var version=new TestAssetVersionDTO();version.setContentSnapshot(JSON.toJSONString(Map.of("content",content)));version.setContentHash(DigestUtils.sha256Hex(version.getContentSnapshot()));
                    assertEquals(1,reader.published(version).getSteps().size());
                    assertNull(mapper.selectExecutableSnapshot("p2","CASE",type));
                    assertArrayEquals(raw,jdbc.queryForObject("SELECT steps FROM functional_case_blob WHERE id=?",byte[].class,type));
                }
            }
        } finally {ds.destroy();}
    }
}
