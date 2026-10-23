package io.metersphere.functional.service;

import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.util.EncryptUtils;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.notice.NoticeModel;
import io.metersphere.system.notice.constants.NoticeConstants;
import io.metersphere.system.service.NoticeSendService;
import jakarta.annotation.Resource;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** Transactional comment notifications. Local delivery is atomic; ambiguous external sends are not blindly replayed. */
@Service
public class FunctionalCaseCommentOutbox {
    @Resource private JdbcTemplate jdbc;
    @Resource private NoticeSendService sender;
    @Resource private FunctionalCaseCommentAccess access;
    @Resource private PlatformTransactionManager transactionManager;

    public Map<String,Object> list(String caseId,String user,int current,int size) {
        var owner=access.requireCase(caseId,user,false);
        if(current<1||size<1||size>100)throw FunctionalCaseCommentAccess.invalid("分页参数无效");
        boolean moderator=access.mayModerate(user,owner.getProjectId());
        String filter=" FROM functional_case_comment_notice n JOIN functional_case_comment c ON c.id=n.comment_id WHERE n.case_id=?";
        List<Object> parameters=new ArrayList<>(List.of(caseId));
        if(!moderator){filter+=" AND c.create_user=?";parameters.add(user);}
        long total=jdbc.queryForObject("SELECT COUNT(*)"+filter,Long.class,parameters.toArray());
        parameters.add(size);parameters.add((current-1L)*size);
        var items=jdbc.queryForList("SELECT n.id,n.channel,n.state,n.update_time AS updateTime"+filter+" ORDER BY n.update_time DESC,n.id LIMIT ? OFFSET ?",parameters.toArray());
        return Map.of("total",total,"items",items);
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public void retry(String caseId,String id,String user,long expectedUpdateTime) {
        var owner=access.requireCase(caseId,user,true);
        var rows=jdbc.queryForList("SELECT n.*,c.create_user FROM functional_case_comment_notice n JOIN functional_case_comment c ON c.id=n.comment_id WHERE n.id=? AND n.case_id=? AND c.deleted=0 FOR UPDATE",id,caseId);
        if(rows.size()!=1)throw FunctionalCaseCommentAccess.denied();
        var row=rows.getFirst();
        if(!user.equals(row.get("create_user"))&&!access.mayModerate(user,owner.getProjectId()))throw FunctionalCaseCommentAccess.denied();
        if(((Number)row.get("update_time")).longValue()!=expectedUpdateTime)throw FunctionalCaseCommentAccess.conflict();
        if(!Set.of("FAILED","UNCERTAIN","SUBMITTED").contains(row.get("state")))throw FunctionalCaseCommentAccess.invalid("当前通知不可重试，请刷新查看状态");
        long now=Math.max(System.currentTimeMillis(),expectedUpdateTime+1);
        jdbc.update("UPDATE functional_case_comment_notice SET state='PENDING',attempts=0,next_attempt=?,update_time=?,error_code='USER_REQUESTED_RETRY' WHERE id=?",now,now,id);
    }

    public void enqueue(String commentId, long version, String caseId, NoticeModel model) {
        for (var delivery : sender.prepareDeliveries(NoticeConstants.TaskType.FUNCTIONAL_CASE_TASK, model)) {
            String receiver = delivery.model().getReceivers().getFirst().getUserId();
            String project = delivery.configuration().getProjectId();
            String channel = delivery.configuration().getType();
            String key = DigestUtils.sha256Hex(JSON.toJSONString(List.of(commentId, version, model.getEvent(),
                    Objects.toString(delivery.configuration().getId(), channel), receiver)));
            long now = System.currentTimeMillis();
            jdbc.update("""
                INSERT INTO functional_case_comment_notice(id,comment_id,case_id,project_id,receiver_id,channel,payload,next_attempt,update_time)
                VALUES (?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id
                """, key, commentId, caseId, project, receiver, channel,
                    EncryptUtils.aesEncrypt(JSON.toJSONString(delivery)), now, now);
        }
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 30000)
    public void drain() {
        long now = System.currentTimeMillis();
        // A process may die after the remote system accepted the message. Preserve that uncertainty.
        jdbc.update("UPDATE functional_case_comment_notice SET state='UNCERTAIN',error_code='DELIVERY_UNCONFIRMED' WHERE state='SENDING' AND update_time<?", now - 300000);
        var ids = jdbc.queryForList("SELECT id FROM functional_case_comment_notice WHERE state='PENDING' AND next_attempt<=? ORDER BY next_attempt LIMIT 20", String.class, now);
        for (String id : ids) {
            try { dispatch(id); }
            catch (Exception failure) {
                jdbc.update("""
                    UPDATE functional_case_comment_notice SET attempts=attempts+1,
                    state=IF(attempts>=8,'FAILED','PENDING'),next_attempt=?,update_time=?,error_code='DELIVERY_FAILED'
                    WHERE id=? AND state='PENDING'
                    """, now + 60000, now, id);
            }
        }
    }

    void dispatch(String id) {
        var tx = new TransactionTemplate(transactionManager);
        NoticeSendService.Delivery remote = tx.execute(status -> {
            var rows = jdbc.queryForList("SELECT * FROM functional_case_comment_notice WHERE id=? AND state='PENDING' FOR UPDATE SKIP LOCKED", id);
            if (rows.isEmpty()) return null;
            var row = rows.getFirst();
            boolean active = Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT (c.deleted OR f.deleted) FROM functional_case_comment c JOIN functional_case f ON f.id=c.case_id WHERE c.id=?", Boolean.class, row.get("comment_id")));
            if (!active || !access.hasPermission((String) row.get("receiver_id"), (String) row.get("project_id"), PermissionConstants.FUNCTIONAL_CASE_READ)) {
                finish(id, "CANCELLED", null); return null;
            }
            var delivery = JSON.parseObject(EncryptUtils.aesDecrypt((String) row.get("payload")), NoticeSendService.Delivery.class);
            if (NoticeConstants.Type.IN_SITE.equals(row.get("channel"))) {
                sender.deliver(delivery);
                finish(id, "SENT", null); // notification INSERT and outbox transition share one transaction
                return null;
            }
            finish(id, "SENDING", null);
            return delivery;
        });
        if (remote == null) return;
        // Legacy external adapters do not all return delivery receipts. Never label their return as confirmed delivery.
        try { sender.deliver(remote); finish(id, "SUBMITTED", "EXTERNAL_RECEIPT_UNAVAILABLE"); }
        catch (Exception failure) { finish(id, "UNCERTAIN", "DELIVERY_UNCONFIRMED"); }
    }

    private void finish(String id, String state, String error) {
        jdbc.update("UPDATE functional_case_comment_notice SET state=?,error_code=?,update_time=? WHERE id=?", state, error, System.currentTimeMillis(), id);
    }
}
