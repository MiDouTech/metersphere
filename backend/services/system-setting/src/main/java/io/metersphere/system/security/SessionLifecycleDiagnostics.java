package io.metersphere.system.security;

import org.springframework.context.event.EventListener;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/** Observe lifecycle delivery without recording session keys or changing expiration semantics. */
@Component
public class SessionLifecycleDiagnostics {
    private final LongAdder destroyed=new LongAdder();
    @EventListener
    public void destroyed(SessionDestroyedEvent event) {
        destroyed.increment();
        org.slf4j.LoggerFactory.getLogger(getClass()).debug("Session lifecycle delivered type={}, fingerprint={}",
                event.getClass().getSimpleName(),org.apache.commons.codec.digest.DigestUtils.sha256Hex(event.getSessionId()).substring(0,12));
    }
    public Map<String,Object> snapshot(){return Map.of("destroyedEventsDelivered",destroyed.sum(),"scope","CURRENT_INSTANCE");}
}
