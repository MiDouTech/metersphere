package io.metersphere.agent.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Shared scan lease plus local fallback backoff even when the database is unavailable. */
@Service
public class AgentRecoveryScanHealth {
    private final JdbcTemplate jdbc;
    private final String owner=UUID.randomUUID().toString();
    private volatile long localNext;
    private int failures;
    public AgentRecoveryScanHealth(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public boolean begin() {
        long now=System.currentTimeMillis();
        if(now<localNext)return false;
        return jdbc.update("UPDATE agent_recovery_scan_health SET lease_owner=?,lease_until=?,update_time=? WHERE scan_name='WRITEBACK' AND lease_until<=? AND next_scan_at<=?",owner,now+120000,now,now,now)==1;
    }
    public void success() {
        long now=System.currentTimeMillis();
        jdbc.update("UPDATE agent_recovery_scan_health SET lease_until=0,next_scan_at=?,last_success_at=?,consecutive_failures=0,error_category=NULL,update_time=? WHERE scan_name='WRITEBACK' AND lease_owner=?",now+30000,now,now,owner);
        failures=0;localNext=now+30000;
    }
    public boolean failure(Throwable error) {
        failures++;
        String category=category(error);
        long delay=retryDelay(category,failures)+ThreadLocalRandom.current().nextLong(1000);
        long now=System.currentTimeMillis();localNext=now+delay;
        try {
            jdbc.update("UPDATE agent_recovery_scan_health SET lease_until=0,next_scan_at=?,last_failure_at=?,consecutive_failures=consecutive_failures+1,error_category=?,update_time=? WHERE scan_name='WRITEBACK' AND (lease_owner=? OR lease_until<=?)",localNext,now,category,now,owner,now);
        } catch(Exception unavailable) {
            // Local cooldown remains authoritative until the shared store can be reached.
        }
        return failures==1 || (failures & (failures-1))==0;
    }
    static long retryDelay(String category,int failures) {
        return "STRUCTURE".equals(category)?300000:Math.min(900000,30000L << Math.min(5,Math.max(0,failures-1)));
    }
    static String category(Throwable error) {
        for(Throwable current=error;current!=null;current=current.getCause()) {
            if(current instanceof SQLException sql) {
                String state=sql.getSQLState();
                if(state!=null && (state.startsWith("42") || state.startsWith("22") || "HY000".equals(state) && sql.getErrorCode()==1267))return "STRUCTURE";
                if(state!=null && state.startsWith("08"))return "CONNECTION";
            }
        }
        return "TRANSIENT";
    }
    public List<Map<String,Object>> status() {
        return jdbc.queryForList("SELECT scan_name AS scanName,last_success_at AS lastSuccessAt,last_failure_at AS lastFailureAt,next_scan_at AS nextScanAt,consecutive_failures AS consecutiveFailures,error_category AS errorCategory FROM agent_recovery_scan_health ORDER BY scan_name");
    }
}
