package io.metersphere.system.uid;

import io.metersphere.sdk.util.CommonBeanFactory;
import io.metersphere.system.uid.impl.DefaultUidGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IDGeneratorLifecycleTests {
    @Test
    void unavailableContextDoesNotPoisonLaterInitialization() throws Exception {
        try (var beans = mockStatic(CommonBeanFactory.class)) {
            Class.forName(IDGenerator.class.getName());
            assertThrows(IllegalStateException.class, IDGenerator::nextStr);
            var worker = mock(DefaultUidGenerator.class);
            when(worker.getUID()).thenReturn(42L);
            beans.when(() -> CommonBeanFactory.getBean(DefaultUidGenerator.class)).thenReturn(worker);
            assertEquals("42", IDGenerator.nextStr());
            verify(worker).getUID();
        }
    }

    @Test
    void contextReplacementUsesItsManagedWorkerWithoutChangingIdRepresentation() {
        var first = mock(DefaultUidGenerator.class);
        var second = mock(DefaultUidGenerator.class);
        when(first.getUID()).thenReturn(101L);
        when(second.getUID()).thenReturn(202L);
        try (var beans = mockStatic(CommonBeanFactory.class)) {
            beans.when(() -> CommonBeanFactory.getBean(DefaultUidGenerator.class)).thenReturn(first, second);
            assertEquals("101", IDGenerator.nextStr());
            assertEquals(202L, IDGenerator.nextNum());
            verify(first).getUID();
            verify(second).getUID();
        }
    }
}
