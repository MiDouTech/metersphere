package io.metersphere.system.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/** Prepare only the six built-in mention/reply rules. Failure prevents application readiness. */
@Component
public class PlatformNotificationInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public PlatformNotificationInitializer(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
    }

    @Override
    public void run(ApplicationArguments arguments) {
        prepare();
    }

    public void prepare() {
        transaction.executeWithoutResult(status -> {
            // Serialize repeated resource preparation, including across application instances.
            // Do not change the robot's enable flag or any existing task/template value.
            var robots = jdbc.queryForList("SELECT id FROM project_robot WHERE project_id=? AND platform='IN_SITE' FOR UPDATE",
                    String.class, PlatformNotificationScope.ID);
            if (robots.size() != 1) throw incomplete();
            String robot = robots.getFirst();
            List<String[]> rules = List.of(
                    new String[]{"BUG_TASK", "AT"}, new String[]{"BUG_TASK", "REPLY"},
                    new String[]{"FUNCTIONAL_CASE_TASK", "AT"}, new String[]{"FUNCTIONAL_CASE_TASK", "REPLY"},
                    new String[]{"FUNCTIONAL_CASE_TASK", "REVIEW_AT"}, new String[]{"FUNCTIONAL_CASE_TASK", "EXECUTE_AT"});
            var existing = jdbc.queryForList("""
                    SELECT id,task_type,event,test_id,project_robot_id FROM message_task
                    WHERE project_id=? AND ((task_type='BUG_TASK' AND event IN ('AT','REPLY'))
                    OR (task_type='FUNCTIONAL_CASE_TASK' AND event IN ('AT','REPLY','REVIEW_AT','EXECUTE_AT')))
                    """, PlatformNotificationScope.ID);
            if (!existing.isEmpty()) {
                // Partial or ambiguous data is an explicit failure, never silently repaired or overwritten.
                if (existing.size() != rules.size()) throw incomplete();
                for (String[] rule : rules) {
                    var matches = existing.stream().filter(row -> rule[0].equals(row.get("task_type"))
                            && rule[1].equals(row.get("event"))).toList();
                    if (matches.size() != 1) throw incomplete();
                    var row = matches.getFirst();
                    if (!robot.equals(row.get("project_robot_id")) || !"NONE".equals(row.get("test_id"))
                            || jdbc.queryForObject("SELECT COUNT(*) FROM message_task_blob WHERE id=?", Long.class, row.get("id")) != 1L)
                        throw incomplete();
                }
                return;
            }
            for (int index = 0; index < rules.size(); index++) {
                String id = "msp-platform-built-in-" + index;
                long now = System.currentTimeMillis();
                jdbc.update("""
                        INSERT INTO message_task(id,event,receivers,project_robot_id,task_type,test_id,project_id,
                        enable,create_user,create_time,update_user,update_time,use_default_template,use_default_subject,subject)
                        VALUES (?,?,'[]',?,?,'NONE',?,1,'admin',?,'admin',?,1,1,'')
                        """, id, rules.get(index)[1], robot, rules.get(index)[0], PlatformNotificationScope.ID, now, now);
                jdbc.update("INSERT INTO message_task_blob(id,template) VALUES (?,'')", id);
            }
        });
    }

    private static IllegalStateException incomplete() {
        return new IllegalStateException("平台内置通知初始化失败：规则或模板不完整/冲突，禁止切换；请核查后重试");
    }
}
