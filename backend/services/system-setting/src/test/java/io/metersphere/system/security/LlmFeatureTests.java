package io.metersphere.system.security;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.controller.handler.result.CommonResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class LlmFeatureTests {
    public static class Operations {
        @RequiresLlmFeature public String generate() { return "generated"; }
        public String history() { return "history"; }
        public String mcp() { return "mcp"; }
    }

    @Test void disabledByDefaultBlocksInvocationButKeepsHistoryAndExternalOperations() {
        var feature = new LlmFeature();
        var factory = new AspectJProxyFactory(new Operations());
        factory.addAspect(feature);
        Operations proxy = factory.getProxy();
        assertFalse(feature.isEnabled());
        assertEquals(CommonResultCode.FEATURE_DISABLED,
                assertThrows(MSException.class, proxy::generate).getErrorCode());
        assertEquals("history", proxy.history());
        assertEquals("mcp", proxy.mcp());
        ReflectionTestUtils.setField(feature, "enabled", true);
        assertEquals("generated", proxy.generate());
    }
}
