package io.metersphere.system.service.ai.agent;

import io.metersphere.system.dto.ai.agent.AiAgentDeviceDTO;
import io.metersphere.system.dto.ai.agent.AiUserAgentConnectionDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class AiUserAgentRepository {
    private final JdbcTemplate jdbcTemplate;

    public AiUserAgentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AiUserAgentConnectionDTO> listConnections(String userId) {
        return jdbcTemplate.query("""
                SELECT c.id, c.provider, c.connection_mode, c.display_name, c.external_account_id,
                       c.status, c.capabilities, c.device_id, c.expires_at, c.last_health_time,
                       c.create_time, d.device_name, d.status device_status, d.bridge_version
                FROM ai_user_agent_connection c
                LEFT JOIN ai_agent_device d ON d.id=c.device_id AND d.user_id=c.user_id AND d.deleted=0
                WHERE c.user_id=? AND c.deleted=0 ORDER BY c.create_time DESC
                """, (rs, rowNum) -> {
            AiUserAgentConnectionDTO dto = new AiUserAgentConnectionDTO();
            dto.setId(rs.getString("id"));
            dto.setProvider(rs.getString("provider"));
            dto.setConnectionMode(rs.getString("connection_mode"));
            dto.setDisplayName(rs.getString("display_name"));
            dto.setMaskedAccount(rs.getString("external_account_id"));
            dto.setStatus(rs.getString("status"));
            dto.setCapabilities(rs.getString("capabilities"));
            dto.setDeviceId(rs.getString("device_id"));
            dto.setDeviceName(rs.getString("device_name"));
            dto.setDeviceStatus(rs.getString("device_status"));
            dto.setBridgeVersion(rs.getString("bridge_version"));
            dto.setExpiresAt(nullableLong(rs.getLong("expires_at"), rs.wasNull()));
            dto.setLastHealthTime(nullableLong(rs.getLong("last_health_time"), rs.wasNull()));
            dto.setCreateTime(rs.getLong("create_time"));
            return dto;
        }, userId);
    }

    public AiUserAgentConnectionDTO findConnection(String id, String userId) {
        return listConnections(userId).stream().filter(item -> item.getId().equals(id)).findFirst().orElse(null);
    }

    public List<Map<String, Object>> listConnectionRoutes(String deviceId) {
        return jdbcTemplate.queryForList("""
                SELECT id,provider FROM ai_user_agent_connection
                WHERE device_id=? AND deleted=0 AND status<>'REVOKED'
                ORDER BY create_time ASC
                """, deviceId);
    }

    public List<AiAgentDeviceDTO> listDevices(String userId) {
        return jdbcTemplate.query("""
                SELECT id, device_name, status, bridge_version, protocol_version, os_type,
                       last_heartbeat_time, create_time
                FROM ai_agent_device WHERE user_id=? AND deleted=0 ORDER BY create_time DESC
                """, (rs, rowNum) -> {
            AiAgentDeviceDTO dto = new AiAgentDeviceDTO();
            dto.setId(rs.getString("id"));
            dto.setDeviceName(rs.getString("device_name"));
            dto.setStatus(rs.getString("status"));
            dto.setBridgeVersion(rs.getString("bridge_version"));
            dto.setProtocolVersion(rs.getString("protocol_version"));
            dto.setOsType(rs.getString("os_type"));
            long heartbeat = rs.getLong("last_heartbeat_time");
            dto.setLastHeartbeatTime(nullableLong(heartbeat, rs.wasNull()));
            dto.setCreateTime(rs.getLong("create_time"));
            return dto;
        }, userId);
    }

    public boolean ownsOnlineDevice(String id, String userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM ai_agent_device
                WHERE id=? AND user_id=? AND status IN ('ONLINE','OFFLINE') AND deleted=0
                """, Integer.class, id, userId);
        return count != null && count > 0;
    }

    public void insertConnection(String id, String userId, String provider, String displayName,
                                 String deviceId, long now) {
        jdbcTemplate.update("""
                INSERT INTO ai_user_agent_connection
                (id,user_id,provider,connection_mode,display_name,status,capabilities,device_id,
                 create_time,update_time,deleted)
                VALUES (?,?,?,'LOCAL_BRIDGE',?,'PENDING','{}',?,?,?,0)
                """, id, userId, provider, displayName, deviceId, now, now);
    }

    public int revokeConnection(String id, String userId, long now) {
        return jdbcTemplate.update("""
                UPDATE ai_user_agent_connection
                SET status='REVOKED', credential_reference=NULL, version=version+1, update_time=?
                WHERE id=? AND user_id=? AND deleted=0 AND status<>'REVOKED'
                """, now, id, userId);
    }

    public int revokeDevice(String id, String userId, long now) {
        int updated = jdbcTemplate.update("""
                UPDATE ai_agent_device
                SET status='REVOKED', access_token_hash=NULL, access_token_expires_at=NULL,
                    revoked_time=?, version=version+1, update_time=?
                WHERE id=? AND user_id=? AND deleted=0 AND status<>'REVOKED'
                """, now, now, id, userId);
        if (updated > 0) {
            jdbcTemplate.update("""
                    UPDATE ai_user_agent_connection SET status='REVOKED', credential_reference=NULL,
                        version=version+1, update_time=?
                    WHERE device_id=? AND user_id=? AND deleted=0 AND status<>'REVOKED'
                    """, now, id, userId);
        }
        return updated;
    }

    public void insertPairing(String id, String userId, String provider, String deviceName,
                              String codeHash, long expiresAt, long now) {
        jdbcTemplate.update("""
                INSERT INTO ai_agent_bridge_pairing
                (id,user_id,provider,expected_device_name,code_hash,status,expires_at,create_time,update_time)
                VALUES (?,?,?,?,?,'PENDING',?,?,?)
                """, id, userId, provider, deviceName, codeHash, expiresAt, now, now);
    }

    public Map<String, Object> findUsablePairing(String codeHash, long now) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id,user_id,provider,expected_device_name FROM ai_agent_bridge_pairing
                WHERE code_hash=? AND status='PENDING' AND expires_at>=?
                """, codeHash, now);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public int consumePairing(String id, String deviceId, long now) {
        return jdbcTemplate.update("""
                UPDATE ai_agent_bridge_pairing SET status='CONSUMED', consumed_at=?, device_id=?, update_time=?
                WHERE id=? AND status='PENDING' AND expires_at>=?
                """, now, deviceId, now, id, now);
    }

    public void insertDevice(String id, String userId, String deviceName, String publicKey,
                             String fingerprint, String bridgeVersion, String protocolVersion,
                             String osType, String tokenHash, long tokenExpiresAt, long now) {
        jdbcTemplate.update("""
                INSERT INTO ai_agent_device
                (id,user_id,device_name,public_key,certificate_fingerprint,status,bridge_version,
                 protocol_version,os_type,last_heartbeat_time,access_token_hash,access_token_expires_at,
                 create_time,update_time,deleted)
                VALUES (?,?,?,?,?,'ONLINE',?,?,?,?,?,?,?,?,0)
                """, id, userId, deviceName, publicKey, fingerprint, bridgeVersion, protocolVersion,
                osType, now, tokenHash, tokenExpiresAt, now, now);
    }

    public Map<String, Object> findActiveDevice(String id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id,user_id,public_key,status,bridge_version,protocol_version,
                       access_token_hash,access_token_expires_at
                FROM ai_agent_device WHERE id=? AND deleted=0 AND status<>'REVOKED'
                """, id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public void insertChallenge(String id, String deviceId, String nonceHash, long expiresAt, long now) {
        jdbcTemplate.update("""
                INSERT INTO ai_agent_device_challenge (id,device_id,nonce_hash,expires_at,create_time)
                VALUES (?,?,?,?,?)
                """, id, deviceId, nonceHash, expiresAt, now);
    }

    public Map<String, Object> findChallenge(String id, String deviceId, long now) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id,nonce_hash FROM ai_agent_device_challenge
                WHERE id=? AND device_id=? AND consumed_at IS NULL AND expires_at>=?
                """, id, deviceId, now);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public int consumeChallenge(String id, String deviceId, long now) {
        return jdbcTemplate.update("""
                UPDATE ai_agent_device_challenge SET consumed_at=?
                WHERE id=? AND device_id=? AND consumed_at IS NULL AND expires_at>=?
                """, now, id, deviceId, now);
    }

    public void rotateDeviceToken(String id, String tokenHash, long expiresAt, long now) {
        jdbcTemplate.update("""
                UPDATE ai_agent_device SET access_token_hash=?, access_token_expires_at=?,
                    status='ONLINE', version=version+1, update_time=?
                WHERE id=? AND status<>'REVOKED' AND deleted=0
                """, tokenHash, expiresAt, now, id);
    }

    public int heartbeat(String id, long now) {
        return jdbcTemplate.update("""
                UPDATE ai_agent_device SET status='ONLINE', last_heartbeat_time=?, update_time=?
                WHERE id=? AND status<>'REVOKED' AND deleted=0
                """, now, now, id);
    }

    public void markDeviceOffline(String id, long now) {
        jdbcTemplate.update("""
                UPDATE ai_agent_device SET status='OFFLINE', version=version+1, update_time=?
                WHERE id=? AND status='ONLINE' AND deleted=0
                """, now, id);
        jdbcTemplate.update("""
                UPDATE ai_user_agent_connection SET status='OFFLINE', version=version+1, update_time=?
                WHERE device_id=? AND status='CONNECTED' AND deleted=0
                """, now, id);
    }

    public int markStaleDevicesOffline(long heartbeatCutoff, long now) {
        List<String> deviceIds = jdbcTemplate.queryForList("""
                SELECT id FROM ai_agent_device
                WHERE status='ONLINE' AND last_heartbeat_time<? AND deleted=0
                """, String.class, heartbeatCutoff);
        deviceIds.forEach(deviceId -> markDeviceOffline(deviceId, now));
        return deviceIds.size();
    }

    public int updateConnectionStatus(String id, String deviceId, String userId, String status,
                                      String maskedAccount, String capabilities, Long expiresAt, long now) {
        return jdbcTemplate.update("""
                UPDATE ai_user_agent_connection
                SET status=?, external_account_id=?, capabilities=?, expires_at=?, last_health_time=?,
                    version=version+1, update_time=?
                WHERE id=? AND device_id=? AND user_id=? AND deleted=0 AND status<>'REVOKED'
                """, status, maskedAccount, capabilities, expiresAt, now, now, id, deviceId, userId);
    }

    private static Long nullableLong(long value, boolean wasNull) {
        return wasNull ? null : value;
    }
}
