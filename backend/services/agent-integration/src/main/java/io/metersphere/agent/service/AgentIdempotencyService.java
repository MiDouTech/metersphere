package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentConstants;
import io.metersphere.agent.constants.AgentErrorCode;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.domain.AgentIdempotencyRecord;
import io.metersphere.system.domain.AgentToken;
import io.metersphere.system.mapper.AgentIdempotencyRecordMapper;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Service
public class AgentIdempotencyService {
    @Resource
    private AgentIdempotencyRecordMapper agentIdempotencyRecordMapper;

    public Optional<Map<String, Object>> findCachedResponse(String toolName, String requestId, Map<String, Object> arguments) {
        if (StringUtils.isBlank(requestId)) {
            return Optional.empty();
        }
        String tokenId = currentTokenId();
        AgentIdempotencyRecord existing = agentIdempotencyRecordMapper.selectByTokenToolRequest(tokenId, toolName, requestId.trim());
        if (existing == null) {
            return Optional.empty();
        }
        String hash = hashArguments(arguments);
        if (!StringUtils.equals(existing.getRequestHash(), hash)) {
            throw new MSException(AgentErrorCode.IDEMPOTENCY_CONFLICT,
                    "相同 requestId 参数不一致: " + requestId);
        }
        if (StringUtils.isBlank(existing.getResponseJson())) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> cached = JSON.parseObject(existing.getResponseJson(), Map.class);
        return Optional.ofNullable(cached);
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(String toolName, String requestId, Map<String, Object> arguments, Map<String, Object> response) {
        if (StringUtils.isBlank(requestId) || response == null) {
            return;
        }
        String tokenId = currentTokenId();
        AgentIdempotencyRecord existing = agentIdempotencyRecordMapper.selectByTokenToolRequest(tokenId, toolName, requestId.trim());
        if (existing != null) {
            String hash = hashArguments(arguments);
            if (!StringUtils.equals(existing.getRequestHash(), hash)) {
                throw new MSException(AgentErrorCode.IDEMPOTENCY_CONFLICT,
                        "相同 requestId 参数不一致: " + requestId);
            }
            return;
        }
        AgentIdempotencyRecord record = new AgentIdempotencyRecord();
        record.setId(IDGenerator.nextStr());
        record.setTokenId(tokenId);
        record.setToolName(toolName);
        record.setRequestId(requestId.trim());
        record.setRequestHash(hashArguments(arguments));
        record.setResponseJson(JSON.toJSONString(response));
        record.setCreateTime(System.currentTimeMillis());
        try {
            agentIdempotencyRecordMapper.insert(record);
        } catch (Exception ex) {
            // 并发插入时回读并做冲突检测
            AgentIdempotencyRecord raced = agentIdempotencyRecordMapper.selectByTokenToolRequest(tokenId, toolName, requestId.trim());
            if (raced == null) {
                throw ex;
            }
            if (!StringUtils.equals(raced.getRequestHash(), hashArguments(arguments))) {
                throw new MSException(AgentErrorCode.IDEMPOTENCY_CONFLICT,
                        "相同 requestId 参数不一致: " + requestId);
            }
        }
    }

    @Scheduled(fixedDelay = 6 * 60 * 60 * 1000L)
    public void cleanupExpired() {
        long expiresBefore = System.currentTimeMillis() - AgentConstants.IDEMPOTENCY_TTL_MS;
        agentIdempotencyRecordMapper.deleteExpired(expiresBefore);
    }

    private String hashArguments(Map<String, Object> arguments) {
        Map<String, Object> normalized = new TreeMap<>();
        if (arguments != null) {
            for (Map.Entry<String, Object> entry : arguments.entrySet()) {
                if ("requestId".equals(entry.getKey())) {
                    continue;
                }
                normalized.put(entry.getKey(), entry.getValue());
            }
        }
        return DigestUtils.sha256Hex(JSON.toJSONString(normalized));
    }

    private String currentTokenId() {
        AgentToken token = AgentTokenContext.get();
        if (token == null || StringUtils.isBlank(token.getId())) {
            return "anonymous";
        }
        return token.getId();
    }

    public Map<String, Object> stripRequestId(Map<String, Object> arguments) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (arguments != null) {
            copy.putAll(arguments);
        }
        return copy;
    }
}
