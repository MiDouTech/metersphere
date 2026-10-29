package io.metersphere.project.service;

import io.metersphere.sdk.domain.Environment;
import io.metersphere.project.dto.environment.EnvironmentRequest;
import io.metersphere.sdk.mapper.EnvironmentMapper;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.Translator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnvironmentUpdateScopeTests {
    @Test void missingEnvironmentFailsBeforeWrites() {
        var service=new EnvironmentService(); var mapper=mock(EnvironmentMapper.class);
        ReflectionTestUtils.setField(service,"environmentMapper",mapper);
        var request=new EnvironmentRequest();request.setId("missing");request.setProjectId("p");
        try(var text=mockStatic(Translator.class)) {
            text.when(()->Translator.get("api_test_environment_not_exist")).thenReturn("missing environment");
            assertEquals("missing environment",assertThrows(MSException.class,()->service.update(request,"u", List.of())).getMessage());
        }
        verify(mapper,never()).updateByPrimaryKeySelective(any());
    }

    @Test void existingEnvironmentCannotBeMovedToAnAuthorizedButDifferentProject() {
        var service=new EnvironmentService(); var mapper=mock(EnvironmentMapper.class);
        ReflectionTestUtils.setField(service,"environmentMapper",mapper);
        var current=new Environment();current.setId("e");current.setProjectId("original");
        when(mapper.selectByPrimaryKey("e")).thenReturn(current);
        var request=new EnvironmentRequest();request.setId("e");request.setProjectId("other");
        assertThrows(MSException.class,()->service.update(request,"u", List.of()));
        verify(mapper,never()).updateByPrimaryKeySelective(any());
    }
}
