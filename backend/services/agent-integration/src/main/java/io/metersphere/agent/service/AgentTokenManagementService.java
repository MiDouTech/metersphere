package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentConstants;
import io.metersphere.agent.dto.AgentTokenCreateRequest;
import io.metersphere.agent.dto.AgentTokenCreateResponse;
import io.metersphere.agent.dto.AgentTokenListItemDTO;
import io.metersphere.agent.dto.AgentTokenPageRequest;
import io.metersphere.agent.dto.AgentTokenUpdateRequest;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.AgentToken;
import io.metersphere.system.domain.User;
import io.metersphere.system.mapper.AgentTokenMapper;
import io.metersphere.system.mapper.ExtUserMapper;
import io.metersphere.system.mapper.UserMapper;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.Pager;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class AgentTokenManagementService {
    @Resource
    private AgentTokenMapper agentTokenMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private ExtUserMapper extUserMapper;

    public AgentTokenCreateResponse create(AgentTokenCreateRequest request) {
        String boundUserId = resolveBoundUserId(request.getUserId());
        String rawToken = generateRawToken();
        AgentToken token = new AgentToken();
        token.setId(IDGenerator.nextStr());
        token.setName(request.getName());
        token.setTokenPrefix(AgentConstants.TOKEN_PREFIX);
        token.setTokenHash(DigestUtils.sha256Hex(rawToken));
        token.setUserId(boundUserId);
        token.setProjectId(request.getProjectId());
        token.setScopes(request.getScopes());
        token.setExpireTime(request.getExpireTime());
        token.setEnable(true);
        token.setCreateTime(System.currentTimeMillis());
        token.setCreateUser(SessionUtils.getUserId());
        agentTokenMapper.insert(token);

        AgentTokenCreateResponse response = new AgentTokenCreateResponse();
        response.setId(token.getId());
        response.setName(token.getName());
        response.setToken(rawToken);
        response.setScopes(token.getScopes());
        response.setExpireTime(token.getExpireTime());
        return response;
    }

    public Pager<List<AgentTokenListItemDTO>> page(AgentTokenPageRequest request) {
        long current = Math.max(request.getCurrent(), 1);
        long pageSize = Math.max(request.getPageSize(), 1);
        long offset = (current - 1) * pageSize;
        long total = agentTokenMapper.countPage(request.getKeyword());
        List<AgentTokenListItemDTO> list = agentTokenMapper.selectPage(request.getKeyword(), offset, pageSize).stream()
                .map(this::toListItem)
                .collect(Collectors.toList());
        return new Pager<>(list, total, pageSize, current);
    }

    public void update(AgentTokenUpdateRequest request) {
        AgentToken existing = agentTokenMapper.selectByPrimaryKey(request.getId());
        if (existing == null) {
            throw new MSException("Token 不存在");
        }
        AgentToken update = new AgentToken();
        update.setId(request.getId());
        update.setName(request.getName());
        update.setProjectId(request.getProjectId());
        update.setScopes(request.getScopes());
        update.setExpireTime(request.getExpireTime());
        update.setEnable(request.getEnable());
        agentTokenMapper.updateByPrimaryKeySelective(update);
    }

    public void delete(String id) {
        agentTokenMapper.deleteByPrimaryKey(id);
    }

    private String generateRawToken() {
        return AgentConstants.TOKEN_PREFIX
                + UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 支持平台用户 ID 或企微 wecom_userid；最终落库为 user.id。
     */
    String resolveBoundUserId(String input) {
        String key = StringUtils.trimToEmpty(input);
        if (StringUtils.isBlank(key)) {
            throw new MSException("关联用户不能为空");
        }
        User byId = userMapper.selectByPrimaryKey(key);
        if (byId != null && !BooleanUtils.isTrue(byId.getDeleted())) {
            return byId.getId();
        }
        User byWecom = extUserMapper.selectByWecomUserid(key);
        if (byWecom != null && !BooleanUtils.isTrue(byWecom.getDeleted())) {
            return byWecom.getId();
        }
        throw new MSException("用户不存在，请填写平台用户ID或企微UserID: " + key);
    }

    private AgentTokenListItemDTO toListItem(AgentToken source) {
        AgentTokenListItemDTO target = new AgentTokenListItemDTO();
        BeanUtils.copyProperties(source, target);
        return target;
    }
}
