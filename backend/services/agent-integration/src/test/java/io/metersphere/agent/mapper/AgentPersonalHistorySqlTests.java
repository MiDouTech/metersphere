package io.metersphere.agent.mapper;

import io.metersphere.agent.dto.AgentExecutionHistoryRequest;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AgentPersonalHistorySqlTests {
    @Test void countAndPageUseSameIsolationAndBoundFilters() throws Exception {
        String resource="io/metersphere/agent/mapper/AgentExecutionMapper.xml";
        Configuration configuration=new Configuration();
        try(var input=getClass().getClassLoader().getResourceAsStream(resource)){
            assertNotNull(input);new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();
        }
        var request=new AgentExecutionHistoryRequest();request.setStatus("FAILED");request.setKeyword("' OR 1=1 --");request.setCreatedAfter(100L);request.setCreatedBefore(200L);
        for(String name:new String[]{"countPersonalHistory","searchPersonalHistory"}){
            var sql=configuration.getMappedStatement(AgentExecutionMapper.class.getName()+"."+name).getBoundSql(Map.of("projectId","project","request",request,"likeKeyword",request.getKeyword(),"offset",20));
            assertTrue(sql.getSql().contains("task_origin = 'PERSONAL_MCP'"));
            assertTrue(sql.getSql().contains("executor_channel = 'EXTERNAL_MCP_AGENT'"));
            assertFalse(sql.getSql().contains("OR 1=1"));assertTrue(sql.getSql().contains("create_time >= ?"));assertTrue(sql.getSql().contains("create_time <= ?"));
            assertFalse(sql.getSql().contains("status = 'QUEUED'"));
            if(name.startsWith("search"))assertTrue(sql.getSql().contains("ORDER BY create_time DESC, id DESC"));
        }
    }
}
