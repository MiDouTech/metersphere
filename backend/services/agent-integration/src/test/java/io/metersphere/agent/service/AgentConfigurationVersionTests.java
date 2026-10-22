package io.metersphere.agent.service;

import io.metersphere.agent.controller.AgentExecutionExceptionHandler;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

class AgentConfigurationVersionTests {
    @ParameterizedTest
    @ValueSource(strings = {"EnvironmentProfile", "CredentialReference", "LoginProfile", "ModelProfile", "PageObject", "BusinessFlow"})
    void missingAndNegativeVersionsCannotReachPersistence(String kind) throws Exception {
        Class<?> requestType = Class.forName("io.metersphere.agent.dto.Agent" + kind + "Request");
        Class<?> serviceType = Class.forName("io.metersphere.agent.service.Agent" + kind + "Service");
        Object service = serviceType.getConstructor().newInstance();
        Object request = requestType.getConstructor().newInstance();
        var update = serviceType.getMethod("update", String.class, requestType);
        for (Integer version : new Integer[]{null, -1}) {
            requestType.getMethod("setVersion", Integer.class).invoke(request, version);
            var thrown = assertThrows(InvocationTargetException.class, () -> update.invoke(service, "configuration", request));
            var error = assertInstanceOf(MSException.class, thrown.getCause());
            assertEquals("CONFIGURATION_VERSION_REQUIRED", error.getMessage());
            var http = new MockHttpServletRequest();
            http.addHeader("X-Trace-Id", "configuration-version-test");
            var response = new AgentExecutionExceptionHandler(new AgentSafeErrorMapper()).business(error, http);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("CONFIGURATION_VERSION_REQUIRED", response.getBody().getCode());
            assertEquals("configuration-version-test", response.getBody().getTraceId());
            assertTrue(response.getBody().getMessage().contains("刷新"));
        }
    }
}
