package io.metersphere.system.service;

import io.metersphere.system.domain.UserRoleRelation;
import io.metersphere.system.domain.UserRoleRelationExample;
import io.metersphere.system.mapper.UserRoleRelationMapper;
import io.metersphere.system.mapper.ExtUserRoleRelationMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Persists an already authorized relationship without replacing existing identity or audit data. */
@Service
@Transactional(rollbackFor = Exception.class)
public class RoleRelationWriter {
    @Resource
    private UserRoleRelationMapper userRoleRelationMapper;
    @Resource
    private ExtUserRoleRelationMapper extUserRoleRelationMapper;

    public boolean ensurePresent(UserRoleRelation relation) {
        UserRoleRelationExample example = new UserRoleRelationExample();
        example.createCriteria().andUserIdEqualTo(relation.getUserId())
                .andRoleIdEqualTo(relation.getRoleId()).andSourceIdEqualTo(relation.getSourceId());
        List<UserRoleRelation> existing = userRoleRelationMapper.selectByExample(example);
        if (!existing.isEmpty()) {
            requireSameOrganization(existing, relation);
            return false;
        }
        try {
            userRoleRelationMapper.insert(relation);
            return true;
        } catch (DuplicateKeyException conflict) {
            // A locking read sees the committed winner even under MySQL REPEATABLE READ.
            // Only an exact natural-key/organization match is an idempotent replay.
            existing = extUserRoleRelationMapper.selectNaturalKeyForUpdate(relation);
            if (existing.size() != 1 || !StringUtils.equals(existing.getFirst().getOrganizationId(), relation.getOrganizationId())) {
                throw conflict;
            }
            return false;
        }
    }

    private void requireSameOrganization(List<UserRoleRelation> existing, UserRoleRelation relation) {
        if (existing.size() != 1 || !StringUtils.equals(existing.getFirst().getOrganizationId(), relation.getOrganizationId())) {
            throw new IllegalStateException("Role relationship scope is inconsistent");
        }
    }
}
