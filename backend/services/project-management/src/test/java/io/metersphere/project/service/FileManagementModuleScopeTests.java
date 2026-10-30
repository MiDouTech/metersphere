package io.metersphere.project.service;

import io.metersphere.project.domain.FileModuleExample;
import io.metersphere.project.mapper.FileModuleMapper;
import io.metersphere.sdk.constants.ModuleConstants;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileManagementModuleScopeTests {
    private final FileModuleMapper mapper = mock(FileModuleMapper.class);
    private final FileManagementService service = new FileManagementService();

    FileManagementModuleScopeTests() {
        ReflectionTestUtils.setField(service, "fileModuleMapper", mapper);
    }

    @Test
    void explicitBlankTargetCannotBecomeAnInvalidStoredModule() {
        assertThrows(MSException.class, () -> service.checkModule("", ModuleConstants.NODE_TYPE_DEFAULT, "project-a"));
        verifyNoInteractions(mapper);
    }

    @Test
    void sharedDefaultModuleStillRequiresProjectIdentity() {
        assertThrows(MSException.class, () -> service.checkModule(ModuleConstants.DEFAULT_NODE_ID, ModuleConstants.NODE_TYPE_DEFAULT, null));
        assertDoesNotThrow(() -> service.checkModule(ModuleConstants.DEFAULT_NODE_ID, ModuleConstants.NODE_TYPE_DEFAULT, "project-a"));
        verifyNoInteractions(mapper);
    }

    @Test
    void moduleExistenceMustBeCheckedWithinTargetProjectAndType() {
        when(mapper.countByExample(any())).thenReturn(0L);
        assertThrows(MSException.class, () -> service.checkModule("module-b", ModuleConstants.NODE_TYPE_DEFAULT, "project-a"));
        ArgumentCaptor<FileModuleExample> query = ArgumentCaptor.forClass(FileModuleExample.class);
        verify(mapper).countByExample(query.capture());
        var criteria = query.getValue().getOredCriteria().getFirst().getAllCriteria();
        assertTrue(criteria.stream().anyMatch(item -> item.getCondition().equals("id =") && item.getValue().equals("module-b")));
        assertTrue(criteria.stream().anyMatch(item -> item.getCondition().equals("project_id =") && item.getValue().equals("project-a")));
        assertTrue(criteria.stream().anyMatch(item -> item.getCondition().equals("module_type =") && item.getValue().equals(ModuleConstants.NODE_TYPE_DEFAULT)));
    }
}
