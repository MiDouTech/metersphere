package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentModuleAliasDTO;
import io.metersphere.agent.resolver.ModuleTreeMatcher;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.AgentModuleAlias;
import io.metersphere.system.mapper.AgentModuleAliasMapper;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class AgentModuleAliasService {
    @Resource
    private AgentModuleAliasMapper agentModuleAliasMapper;
    @Resource
    private ModuleTreeMatcher moduleTreeMatcher;

    public List<AgentModuleAliasDTO> list(String projectId) {
        return agentModuleAliasMapper.selectByProjectId(projectId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public AgentModuleAliasDTO add(AgentModuleAliasDTO request) {
        validateModuleExists(request.getProjectId(), request.getModuleId());
        String alias = normalizeAlias(request.getAlias());
        if (agentModuleAliasMapper.selectByProjectAndAlias(request.getProjectId(), alias) != null) {
            throw new MSException("别名已存在: " + alias);
        }
        AgentModuleAlias record = new AgentModuleAlias();
        record.setId(IDGenerator.nextStr());
        record.setProjectId(request.getProjectId());
        record.setAlias(alias);
        record.setModuleId(request.getModuleId());
        record.setCreateTime(System.currentTimeMillis());
        record.setCreateUser(SessionUtils.getUserId());
        agentModuleAliasMapper.insert(record);
        return toDto(record);
    }

    public Set<String> resolveModuleIds(String projectId, String query) {
        if (StringUtils.isBlank(query)) {
            return Collections.emptySet();
        }
        String alias = normalizeAlias(query);
        AgentModuleAlias aliasRecord = agentModuleAliasMapper.selectByProjectAndAlias(projectId, alias);
        if (aliasRecord == null) {
            return Collections.emptySet();
        }
        return moduleTreeMatcher.expandModuleSubtree(projectId, aliasRecord.getModuleId());
    }

    private void validateModuleExists(String projectId, String moduleId) {
        boolean exists = moduleTreeMatcher.flatten(projectId).stream()
                .anyMatch(node -> StringUtils.equals(node.getId(), moduleId));
        if (!exists) {
            throw new MSException("模块不存在: " + moduleId);
        }
    }

    private String normalizeAlias(String alias) {
        return StringUtils.trim(alias).toUpperCase(Locale.ROOT);
    }

    private AgentModuleAliasDTO toDto(AgentModuleAlias source) {
        AgentModuleAliasDTO target = new AgentModuleAliasDTO();
        target.setId(source.getId());
        target.setProjectId(source.getProjectId());
        target.setAlias(source.getAlias());
        target.setModuleId(source.getModuleId());
        target.setCreateTime(source.getCreateTime());
        return target;
    }
}
