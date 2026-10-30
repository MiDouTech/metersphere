package io.metersphere.system.service;

import io.metersphere.system.domain.UserRoleRelation;
import io.metersphere.system.mapper.ExtUserRoleRelationMapper;
import io.metersphere.system.mapper.UserRoleRelationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleRelationWriterTests {
    @Mock UserRoleRelationMapper mapper;
    @Mock ExtUserRoleRelationMapper extMapper;
    RoleRelationWriter writer;
    UserRoleRelation relation;

    @BeforeEach
    void setup() {
        writer = new RoleRelationWriter();
        ReflectionTestUtils.setField(writer, "userRoleRelationMapper", mapper);
        ReflectionTestUtils.setField(writer, "extUserRoleRelationMapper", extMapper);
        relation = new UserRoleRelation();
        relation.setId("new-id");
        relation.setUserId("user");
        relation.setRoleId("role");
        relation.setSourceId("project");
        relation.setOrganizationId("organization");
    }

    @Test void existingRelationshipIsNotRewritten() {
        when(mapper.selectByExample(any())).thenReturn(List.of(relation));
        assertFalse(writer.ensurePresent(relation));
        verify(mapper, never()).insert(any());
    }

    @Test void missingRelationshipIsInserted() {
        when(mapper.selectByExample(any())).thenReturn(List.of());
        assertTrue(writer.ensurePresent(relation));
        verify(mapper).insert(relation);
    }

    @Test void concurrentExactWinnerIsAcceptedByCurrentRead() {
        when(mapper.selectByExample(any())).thenReturn(List.of());
        when(mapper.insert(any())).thenThrow(new DuplicateKeyException("collision"));
        when(extMapper.selectNaturalKeyForUpdate(relation)).thenReturn(List.of(relation));
        assertFalse(writer.ensurePresent(relation));
    }

    @Test void unrelatedPrimaryKeyCollisionIsNotSwallowed() {
        when(mapper.selectByExample(any())).thenReturn(List.of());
        when(mapper.insert(any())).thenThrow(new DuplicateKeyException("collision"));
        when(extMapper.selectNaturalKeyForUpdate(relation)).thenReturn(List.of());
        assertThrows(DuplicateKeyException.class, () -> writer.ensurePresent(relation));
    }

    @Test void mismatchedOrganizationIsRejected() {
        UserRoleRelation existing = new UserRoleRelation();
        existing.setOrganizationId("another-organization");
        when(mapper.selectByExample(any())).thenReturn(List.of(existing));
        assertThrows(IllegalStateException.class, () -> writer.ensurePresent(relation));
        verify(mapper, never()).insert(any());
    }
}
