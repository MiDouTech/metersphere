package io.metersphere.system.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Authentication methods implemented by this distribution. Miduo SSO has its own status/bridge API. */
@RestController
@RequestMapping("/authentication")
public class AuthenticationController {
    @GetMapping(value = "/get-list", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    public List<String> list() {
        return List.of("LOCAL");
    }
}
