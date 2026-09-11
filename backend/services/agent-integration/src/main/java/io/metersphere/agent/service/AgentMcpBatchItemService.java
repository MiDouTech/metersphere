package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentCaseSubmitRequest;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

/** Each result commits with its idempotency record; a later failure cannot roll it back. */
@Service
public class AgentMcpBatchItemService {
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private AgentIdempotencyService idempotencyService;
    @Resource private AgentFunctionalCaseSubmitService submitService;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public void submit(AgentCaseSubmitRequest request, String key) {
        var token = AgentTokenContext.get();
        if (token == null) throw new MSException("AUTHENTICATION_REQUIRED");
        // Lock an existing row: SELECT on a missing idempotency record is insufficient
        // to serialize simultaneous first submissions on every transaction isolation level.
        jdbcTemplate.queryForObject("SELECT id FROM agent_token WHERE id=? FOR UPDATE", String.class, token.getId());
        Map<String,Object> args = JSON.parseObject(JSON.toJSONString(request), Map.class);
        String tool = "metersphere.functional.submit.batch.item";
        if (idempotencyService.findCachedResponse(tool, key, args).isPresent()) return;
        submitService.submit(request);
        idempotencyService.save(tool, key, args, Map.of("ok", true));
    }
}
