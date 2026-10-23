package io.metersphere.system.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthenticationControllerTests {
    @Test void loginMethodEndpointExistsAndOnlyAdvertisesSupportedLocalAuthentication() throws Exception {
        MockMvcBuilders.standaloneSetup(new AuthenticationController()).build()
                .perform(get("/authentication/get-list"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("LOCAL"),
                        io.metersphere.sdk.util.JSON.parseArray(result.getResponse().getContentAsString(), String.class)));
    }
}
