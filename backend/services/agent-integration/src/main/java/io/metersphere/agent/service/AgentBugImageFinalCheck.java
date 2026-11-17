package io.metersphere.agent.service;

import io.metersphere.agent.security.AgentTokenContext;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/**
 * Pins the database predicates with InnoDB shared record AND next-key locks while the final storage
 * generation is checked. The storage stat is the decision point: database revocations,
 * deletes and rebindings cannot commit between their validation and that stat.
 * SERIALIZABLE also makes the final ORM SELECTs locking current reads: an authorization
 * query cannot adopt a newly inserted, unprotected grant. Empty ranges are protected,
 * not only rows present during the explicit prelock scan. REQUIRES_NEW starts with no
 * earlier read view; clearCache removes session values predating these locks.
 * Storage generation strength remains a separate storage-provider prerequisite.
 */
@Service
public class AgentBugImageFinalCheck {
    private final JdbcTemplate jdbc;
    private final SqlSessionTemplate sessions;
    private final TransactionTemplate transaction;

    public AgentBugImageFinalCheck(JdbcTemplate jdbc, SqlSessionTemplate sessions, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.sessions = sessions; transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
    }

    public void run(String project, String bug, String file, Runnable finalValidation) {
        var context = AgentTokenContext.get();
        if (context == null || context.getId() == null || context.getUserId() == null) throw AgentBusinessAccess.denied();
        transaction.executeWithoutResult(status -> {
            lock("SELECT id FROM agent_token WHERE id=? FOR SHARE", context.getId());
            lock("SELECT id FROM `user` WHERE id=? FOR SHARE", context.getUserId());
            var roles = jdbc.queryForList("SELECT role_id FROM user_role_relation WHERE user_id=? ORDER BY id FOR SHARE", String.class, context.getUserId());
            for (String role : new TreeSet<>(roles)) {
                lock("SELECT id FROM user_role WHERE id=? FOR SHARE", role);
                lock("SELECT role_id FROM user_role_permission WHERE role_id=? FOR SHARE", role);
            }
            lock("SELECT id FROM project WHERE id=? FOR SHARE", project);
            lock("SELECT id FROM bug WHERE id=? FOR SHARE", bug);
            lock("SELECT bug_id FROM bug_content WHERE bug_id=? FOR SHARE", bug);
            lock("SELECT id FROM bug_local_attachment WHERE bug_id=? ORDER BY id FOR SHARE", bug);
            lock("SELECT id FROM file_association WHERE source_id=? AND source_type='BUG' ORDER BY id FOR SHARE", bug);
            lock("SELECT id FROM file_metadata WHERE id=? FOR SHARE", file);
            lock("SELECT file_metadata_id FROM file_metadata_repository WHERE file_metadata_id=? FOR SHARE", file);
            lock("SELECT file_module_id FROM file_module_repository WHERE file_module_id IN (SELECT module_id FROM file_metadata WHERE id=?) FOR SHARE", file);
            // No cached ORM value predating these locks may participate in the decision.
            sessions.clearCache();
            finalValidation.run();
        });
    }

    private void lock(String sql, String id) { jdbc.queryForList(sql, id); }
}
