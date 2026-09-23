package io.metersphere.agent.quality;

import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.service.AgentProjectService;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.AgentToken;
import io.metersphere.system.utils.SessionUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QualityPolicyAccessTests {
    @Test void tokenCannotInheritOwnerPolicyRights() {
        try (MockedStatic<SessionUtils> session = mockStatic(SessionUtils.class)) {
            session.when(SessionUtils::getUserId).thenReturn("owner");
            AgentTokenContext.set(new AgentToken());
            assertThrows(MSException.class, () -> new QualityPolicyAccess(mock(AgentProjectService.class), mock(org.springframework.jdbc.core.JdbcTemplate.class))
                    .require("p", PermissionConstants.QUALITY_POLICY_PUBLISH));
        } finally { AgentTokenContext.clear(); }
    }
    @Test void rejectsAnotherProjectEvenWithSession() {
        try (MockedStatic<SessionUtils> session = mockStatic(SessionUtils.class)) {
            session.when(SessionUtils::getUserId).thenReturn("user");
            session.when(SessionUtils::getCurrentProjectId).thenReturn("other");
            var projects = mock(AgentProjectService.class);
            assertThrows(MSException.class, () -> new QualityPolicyAccess(projects, mock(org.springframework.jdbc.core.JdbcTemplate.class)).require("p", PermissionConstants.QUALITY_POLICY_MANAGE));
            verifyNoInteractions(projects);
        }
    }
    @Test void checksActualPermissionAndProjectMembership() {
        try (MockedStatic<SessionUtils> session = mockStatic(SessionUtils.class);
             MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            session.when(SessionUtils::getUserId).thenReturn("user");
            session.when(SessionUtils::getCurrentProjectId).thenReturn("p");
            var subject = mock(Subject.class);
            security.when(SecurityUtils::getSubject).thenReturn(subject);
            var projects = mock(AgentProjectService.class);
            when(projects.resolveProjectId("p")).thenReturn("p");
            var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
            when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("p"))).thenReturn(1);
            when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("user"), eq("p"), eq(PermissionConstants.QUALITY_POLICY_PUBLISH))).thenReturn(1);
            assertEquals("user", new QualityPolicyAccess(projects, jdbc).require("p", PermissionConstants.QUALITY_POLICY_PUBLISH));
            verify(subject).checkPermission(PermissionConstants.QUALITY_POLICY_PUBLISH);
            verify(projects).resolveProjectId("p");
            when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("user"), eq("p"), eq(PermissionConstants.QUALITY_POLICY_PUBLISH))).thenReturn(0);
            assertThrows(MSException.class, () -> new QualityPolicyAccess(projects, jdbc).require("p", PermissionConstants.QUALITY_POLICY_PUBLISH));
        }
    }

    @Test void disabledProjectRemainsReadableButCannotBeManagedOrPublished() {
        try (MockedStatic<SessionUtils> session = mockStatic(SessionUtils.class);
             MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            session.when(SessionUtils::getUserId).thenReturn("user");
            session.when(SessionUtils::getCurrentProjectId).thenReturn("p");
            security.when(SecurityUtils::getSubject).thenReturn(mock(Subject.class));
            var projects = mock(AgentProjectService.class);
            when(projects.resolveProjectId("p")).thenReturn("p");
            var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
            when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("user"), eq("p"), anyString())).thenReturn(1);
            when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("p"))).thenReturn(0);
            var access = new QualityPolicyAccess(projects, jdbc);
            assertEquals("user", access.require("p", PermissionConstants.QUALITY_READ));
            for (String permission : new String[]{PermissionConstants.QUALITY_POLICY_MANAGE, PermissionConstants.QUALITY_POLICY_PUBLISH}) {
                assertEquals("QUALITY_POLICY_PROJECT_DISABLED", assertThrows(MSException.class, () -> access.require("p", permission)).getMessage());
            }
        }
    }
}
