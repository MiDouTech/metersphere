package io.metersphere.functional.service;

import io.metersphere.sdk.util.JSON;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiSourceDocumentEventService {
    private static final long EMITTER_TIMEOUT_MS = 30L * 60L * 1000L;
    private final Map<String, Set<SseEmitter>> projectEmitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String projectId, String userId) {
        String key = key(projectId, userId);
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        projectEmitters.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        Runnable cleanup = () -> remove(key, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data("{}"));
        } catch (IOException ex) {
            cleanup.run();
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    public void publish(String projectId, String userId, String documentId, String status, String message) {
        String key = key(projectId, userId);
        StatusEvent payload = new StatusEvent();
        payload.setDocumentId(documentId);
        payload.setStatus(status);
        payload.setMessage(message);
        payload.setTimestamp(System.currentTimeMillis());
        for (SseEmitter emitter : projectEmitters.getOrDefault(key, Set.of())) {
            try {
                emitter.send(SseEmitter.event()
                        .id(documentId + ":" + payload.getTimestamp())
                        .name("document-status")
                        .data(JSON.toJSONString(payload)));
            } catch (IOException | IllegalStateException ex) {
                remove(key, emitter);
            }
        }
    }

    @Scheduled(fixedRate = 15_000L)
    public void heartbeat() {
        projectEmitters.forEach((key, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException | IllegalStateException ex) {
                    remove(key, emitter);
                }
            }
        });
    }

    private void remove(String key, SseEmitter emitter) {
        Set<SseEmitter> emitters = projectEmitters.get(key);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            projectEmitters.remove(key, emitters);
        }
    }

    private String key(String projectId, String userId) {
        return projectId + ":" + userId;
    }

    @Data
    public static class StatusEvent {
        private String documentId;
        private String status;
        private String message;
        private long timestamp;
    }
}
