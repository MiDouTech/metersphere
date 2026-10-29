package io.metersphere.system.config;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CheckOwnerEmptyInputTests {
    private Configuration configuration() throws Exception {
        Configuration configuration = new Configuration();
        Path xml = Path.of("src/main/java/io/metersphere/system/mapper/ExtCheckOwnerMapper.xml");
        if (!Files.isRegularFile(xml)) xml = Path.of("backend/services/system-setting").resolve(xml);
        try (var input = Files.newInputStream(xml)) {
            new XMLMapperBuilder(input, configuration, xml.toString(), configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private void verifyRendering(List<String> ids, boolean empty) throws Exception {
        Configuration configuration = configuration();
        for (String method : List.of("checkoutOwner", "checkoutRelationOwner", "checkoutOrganizationOwner",
                "checkoutOrganization", "checkoutOrganizationOwnerByScope", "checkoutProjectOwnerByScope")) {
            var parameters = new HashMap<String, Object>();
            parameters.put("ids", ids);
            parameters.put("userId", "ordinary-user");
            parameters.put("table", "file_metadata");
            parameters.put("relationTable", "file_association");
            parameters.put("column", "project_id");
            var sql = configuration.getMappedStatement("io.metersphere.system.mapper.ExtCheckOwnerMapper." + method)
                    .getBoundSql(parameters);
            String normalized = sql.getSql().replaceAll("\\s+", " ");
            assertFalse(normalized.matches(".*IN\\s*\\(\\s*\\).*"), method);
            assertEquals(empty, normalized.contains("(NULL)"), method);
            assertEquals(empty ? 1 : 2, sql.getParameterMappings().size(), method);
            assertTrue(normalized.contains("user_id = ?"), method);
        }
    }

    @Test void emptyIdsFailClosedWithoutInvalidSql() throws Exception { verifyRendering(List.of(), true); }
    @Test void nullIdsFailClosedWithoutInvalidSql() throws Exception { verifyRendering(null, true); }
    @Test void validIdsRemainBoundParametersWithUserScope() throws Exception { verifyRendering(List.of("file-a"), false); }
}
