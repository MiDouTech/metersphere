package io.metersphere.system.wecombot;

import io.metersphere.sdk.util.JSON;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/wecom-bot/events")
public class WecomBotEventController {
    private final WecomCallbackVerifier verifier;
    private final WecomBotService service;

    public WecomBotEventController(WecomCallbackVerifier verifier, WecomBotService service) {
        this.verifier = verifier;
        this.service = service;
    }

    @PostMapping("/{type:status|chat|delivery}")
    public void callback(@PathVariable String type, @RequestHeader("x-ms-timestamp") String timestamp,
                         @RequestHeader("x-ms-nonce") String nonce,
                         @RequestHeader("x-ms-signature") String signature, @RequestBody String body) {
        verifier.verify(timestamp, nonce, signature, body);
        WecomBotModels.CallbackEvent event = JSON.parseObject(body, WecomBotModels.CallbackEvent.class);
        service.callback(event, type.toUpperCase(), nonce);
    }
}
