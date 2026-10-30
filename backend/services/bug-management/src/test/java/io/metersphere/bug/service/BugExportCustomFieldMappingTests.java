package io.metersphere.bug.service;

import io.metersphere.bug.mapper.ExtBugCustomFieldMapper;
import io.metersphere.bug.dto.response.BugCustomFieldDTO;
import io.metersphere.bug.dto.response.BugDTO;
import io.metersphere.sdk.exception.MSException;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BugExportCustomFieldMappingTests {
    @Test void actualMapperXmlRetainsBugIdWithoutGlobalUnderscoreMapping() throws Exception {
        var ds=mock(DataSource.class);var connection=mock(Connection.class);var statement=mock(PreparedStatement.class);
        var result=mock(ResultSet.class);var metadata=mock(ResultSetMetaData.class);
        when(ds.getConnection()).thenReturn(connection);when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.execute()).thenReturn(true);when(statement.getResultSet()).thenReturn(result);when(statement.getUpdateCount()).thenReturn(-1);
        when(result.getMetaData()).thenReturn(metadata);when(result.next()).thenReturn(true,false);
        String[] names={"name","value","text","bug_id","id","type"};when(metadata.getColumnCount()).thenReturn(names.length);
        for(int i=0;i<names.length;i++){when(metadata.getColumnLabel(i+1)).thenReturn(names[i]);when(metadata.getColumnName(i+1)).thenReturn(names[i]);when(metadata.getColumnType(i+1)).thenReturn(Types.VARCHAR);when(metadata.getColumnClassName(i+1)).thenReturn(String.class.getName());}
        when(result.getString(anyString())).thenAnswer(call->switch((String)call.getArgument(0)){case "bug_id"->"bug-1";case "id"->"field-1";case "value"->"general";case "type"->"SELECT";case "name"->"Severity";default->null;});
        var config=new Configuration(new Environment("test",new JdbcTransactionFactory(),ds));config.setMapUnderscoreToCamelCase(false);
        String resource="io/metersphere/bug/mapper/ExtBugCustomFieldMapper.xml";
        try(var stream=getClass().getClassLoader().getResourceAsStream(resource)){
            assertNotNull(stream);new XMLMapperBuilder(stream,config,resource,config.getSqlFragments()).parse();
        }
        try(var session=new DefaultSqlSessionFactory(config).openSession()){
            var fields=session.getMapper(ExtBugCustomFieldMapper.class).getBugAllCustomFields(List.of("bug-1"),"p");
            assertEquals(1,fields.size());assertEquals("bug-1",fields.getFirst().getBugId());assertEquals("field-1",fields.getFirst().getId());assertEquals("general",fields.getFirst().getValue());
        }
    }
    @Test void validExportFieldsRemainAssociatedWithTheirBug(){
        var service=new BugService();var mapper=mock(ExtBugCustomFieldMapper.class);var common=mock(BugCommonService.class);
        ReflectionTestUtils.setField(service,"extBugCustomFieldMapper",mapper);ReflectionTestUtils.setField(service,"bugCommonService",common);
        ReflectionTestUtils.setField(service,"bugHandleUserRelationService",mock(BugHandleUserRelationService.class));
        var bug=new BugDTO();bug.setId("bug-1");var field=new BugCustomFieldDTO();field.setId("f");field.setBugId("bug-1");field.setValue(null);
        when(mapper.getBugAllCustomFields(List.of("bug-1"),"p")).thenReturn(List.of(field));when(common.getAllStatusMap("p")).thenReturn(new HashMap<>());
        var result=service.handleCustomField(List.of(bug),"p");assertSame(field,result.getFirst().getCustomFields().getFirst());
    }
    @Test void invalidMappingFailsExplicitlyInsteadOfSilentlyDroppingFields(){
        var service=new BugService();var mapper=mock(ExtBugCustomFieldMapper.class);ReflectionTestUtils.setField(service,"extBugCustomFieldMapper",mapper);
        var bug=new BugDTO();bug.setId("bug-1");var invalid=new BugCustomFieldDTO();invalid.setId("f");
        when(mapper.getBugAllCustomFields(List.of("bug-1"),"p")).thenReturn(List.of(invalid));
        assertEquals("BUG_FIELD_MAPPING_INVALID",assertThrows(MSException.class,()->service.handleCustomField(List.of(bug),"p")).getMessage());
    }
}
