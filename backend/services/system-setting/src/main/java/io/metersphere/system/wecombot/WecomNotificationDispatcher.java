package io.metersphere.system.wecombot;

import io.metersphere.sdk.util.JSON;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

@Component
public class WecomNotificationDispatcher {
    private final JdbcTemplate jdbc;
    private final WecomBotBridgeClient bridge;
    private final WecomBotService service;
    private final TransactionTemplate transaction;

    public WecomNotificationDispatcher(JdbcTemplate jdbc, WecomBotBridgeClient bridge, WecomBotService service,
                                       PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.bridge = bridge;
        this.service = service;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${ms.wecom-bot.dispatch-delay-ms:3000}")
    public void dispatch() {
        if (!service.isEnabled() || !service.status().ready()) return;
        for (Map<String, Object> row : claim(20)) {
            String id = String.valueOf(row.get("id"));
            if (!service.isEnabled()) {
                jdbc.update("UPDATE wecom_notification_outbox SET status='CANCELLED',lease_until=NULL,next_retry_at=NULL,update_time=? WHERE id=? AND status='SENDING'",
                        System.currentTimeMillis(), id);
                continue;
            }
            String requestId = id;
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = JSON.parseObject(String.valueOf(row.get("payload")), Map.class);
            WecomBotModels.BridgeResult result = bridge.send(new WecomBotModels.BridgeSendRequest(
                    requestId, id, String.valueOf(row.get("target_type")), String.valueOf(row.get("target_id")),
                    String.valueOf(row.get("message_type")), payload));
            jdbc.update("UPDATE wecom_notification_outbox SET request_id=?,update_time=? WHERE id=?",
                    requestId, System.currentTimeMillis(), id);
            service.finishDelivery(id, result.success(), result.retryable(), result.errorCode(), result.errorMessage());
        }
    }

    private List<Map<String, Object>> claim(int batchSize) {
        List<Map<String, Object>> claimed = transaction.execute(status -> {
            long now = System.currentTimeMillis();
            jdbc.update("UPDATE wecom_notification_outbox SET status='RETRY',lease_until=NULL,next_retry_at=?,error_code='LEASE_EXPIRED',error_message='Previous dispatcher lease expired',retryable=1,update_time=? WHERE status='SENDING' AND lease_until<?",
                    now, now, now);
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,target_type,target_id,message_type,payload FROM wecom_notification_outbox WHERE status IN ('PENDING','RETRY') AND (next_retry_at IS NULL OR next_retry_at<=?) ORDER BY create_time LIMIT ? FOR UPDATE SKIP LOCKED",
                    now, batchSize);
            for (Map<String, Object> row : rows) {
                jdbc.update("UPDATE wecom_notification_outbox SET status='SENDING',attempts=attempts+1,lease_until=?,update_time=? WHERE id=?",
                        now + 60_000L, now, row.get("id"));
            }
            return rows;
        });
        return claimed == null ? List.of() : claimed;
    }
}
