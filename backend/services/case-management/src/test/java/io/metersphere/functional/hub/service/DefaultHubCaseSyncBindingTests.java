package io.metersphere.functional.hub.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class DefaultHubCaseSyncBindingTests {
    @Test
    void syncEntryRejectsStaleBindingBeforeMirrorContentOrRepairIsTouched() {
        var fixture = new CaseAssetSourceBindingGuardTests();
        fixture.setup();
        var jdbc = fixture.jdbc;
        var tx = fixture.tx;
        var guard = fixture.guard;
        var observed = fixture.observed;
        var service = new DefaultHubCaseSyncService();
        var config = org.mockito.Mockito.mock(DefaultHubConfigService.class);
        var hub = org.mockito.Mockito.mock(io.metersphere.system.service.DefaultHubProjectService.class);
        var cases = org.mockito.Mockito.mock(io.metersphere.functional.mapper.FunctionalCaseMapper.class);
        var maps = org.mockito.Mockito.mock(io.metersphere.functional.hub.dao.DefaultHubCaseMapDao.class);
        var blobs = org.mockito.Mockito.mock(io.metersphere.functional.mapper.FunctionalCaseBlobMapper.class);
        var repair = org.mockito.Mockito.mock(CaseAssetSourceMappingRepair.class);
        var source = new io.metersphere.functional.domain.FunctionalCase();
        source.setId("source"); source.setProjectId("project"); source.setRefId("ref"); source.setLatest(true);
        org.mockito.Mockito.when(config.isSyncEnabled()).thenReturn(true);
        org.mockito.Mockito.when(cases.selectByPrimaryKey("source")).thenReturn(source);
        org.mockito.Mockito.when(maps.findByBizCaseId("source")).thenReturn(observed);
        jdbc.execute("ALTER TABLE functional_case ADD project_id varchar");
        jdbc.execute("ALTER TABLE functional_case ADD ref_id varchar");
        jdbc.execute("ALTER TABLE functional_case ADD deleted int DEFAULT 0");
        jdbc.execute("ALTER TABLE functional_case ADD latest int DEFAULT 1");
        jdbc.update("INSERT INTO functional_case(id,project_id) VALUES('source','project')");
        ReflectionTestUtils.setField(service, "defaultHubConfigService", config);
        ReflectionTestUtils.setField(service, "defaultHubProjectService", hub);
        ReflectionTestUtils.setField(service, "functionalCaseMapper", cases);
        ReflectionTestUtils.setField(service, "functionalCaseBlobMapper", blobs);
        ReflectionTestUtils.setField(service, "defaultHubCaseMapDao", maps);
        ReflectionTestUtils.setField(service, "mappingRepair", repair);
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(service, "bindingGuard", guard);
        tx.executeWithoutResult(status -> fixture.detach());
        assertThrows(CaseAssetBindingChangedException.class, () -> tx.executeWithoutResult(status ->
                service.syncCaseUpsert("project", "source", "operator")));
        org.mockito.Mockito.verify(cases).selectByPrimaryKey("source");
        org.mockito.Mockito.verifyNoMoreInteractions(cases);
        org.mockito.Mockito.verifyNoInteractions(blobs, repair);
        assertThrows(CaseAssetBindingChangedException.class, () -> tx.executeWithoutResult(status ->
                service.syncCaseDelete("source", "operator")));
        org.mockito.Mockito.verifyNoMoreInteractions(cases);
        assertEquals("independent", jdbc.queryForObject("SELECT name FROM functional_case WHERE id='asset'", String.class));
    }

}
