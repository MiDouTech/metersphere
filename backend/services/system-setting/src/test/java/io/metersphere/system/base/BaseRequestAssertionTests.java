package io.metersphere.system.base;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Standalone tests of the test client itself; no application fixtures or database are used. */
class BaseRequestAssertionTests {
    private static class Client extends BaseTest { }

    @RestController
    @org.springframework.context.annotation.Profile("request-helper-contract-only")
    static class Endpoint {
        @GetMapping("/helper/{id}")
        ResponseEntity<?> get(@PathVariable("id") String id) {
            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(Map.of("id", id));
        }

        @PostMapping("/helper/{id}")
        ResponseEntity<?> post(@PathVariable("id") String id) {
            return ResponseEntity.unprocessableEntity().contentType(MediaType.APPLICATION_JSON).body(Map.of("id", id));
        }
    }

    @Test
    void getPreservesUriArgumentsAndActuallyChecksInlineMatchers() throws Exception {
        check(client -> {
            client.requestGet("/helper/{id}", "get-id", status().isBadRequest(), jsonPath("$.id").value("get-id"));
            assertThrows(AssertionError.class,
                    () -> client.requestGet("/helper/{id}", status().is5xxServerError(), "get-id"));
        });
    }

    @Test
    void postActuallyChecksInlineMatchers() throws Exception {
        check(client -> {
            client.requestPost("/helper/{id}", Map.of(), "post-id", status().isUnprocessableEntity());
            assertThrows(AssertionError.class,
                    () -> client.requestPost("/helper/{id}", Map.of(), "post-id", status().is5xxServerError()));
        });
    }

    @Test
    void multipartActuallyChecksInlineMatchers() throws Exception {
        check(client -> {
            client.requestMultipart("/helper/{id}", new LinkedMultiValueMap<>(), "multipart-id", status().isUnprocessableEntity());
            assertThrows(AssertionError.class,
                    () -> client.requestMultipart("/helper/{id}", new LinkedMultiValueMap<>(), "multipart-id", status().is5xxServerError()));
        });
    }

    private void check(Checked test) throws Exception {
        var client = new Client();
        var previous = BaseTest.adminAuthInfo;
        try {
            BaseTest.adminAuthInfo = client.new AuthInfo("helper-session", "helper-csrf");
            client.mockMvc = MockMvcBuilders.standaloneSetup(new Endpoint()).build();
            test.run(client);
        } finally {
            BaseTest.adminAuthInfo = previous;
        }
    }

    private interface Checked {
        void run(Client client) throws Exception;
    }
}
