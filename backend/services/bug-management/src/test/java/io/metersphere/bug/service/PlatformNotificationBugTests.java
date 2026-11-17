package io.metersphere.bug.service;

import io.metersphere.bug.domain.Bug;
import io.metersphere.project.domain.ProjectRobot;
import io.metersphere.project.mapper.ProjectRobotMapper;
import io.metersphere.system.domain.User;
import io.metersphere.system.mapper.ExtSystemProjectMapper;
import io.metersphere.system.notice.utils.WeComClient;
import io.metersphere.system.service.PlatformNotificationScope;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformNotificationBugTests {
    @Test void specialBugConsumerUsesPlatformRobotAndOriginMembershipWithoutFallback() {
        var service=new BugSyncNoticeService();
        var robots=mock(ProjectRobotMapper.class);var members=mock(ExtSystemProjectMapper.class);
        ReflectionTestUtils.setField(service,"projectRobotMapper",robots);
        ReflectionTestUtils.setField(service,"sourceMembers",members);
        var robot=new ProjectRobot();robot.setWebhook("https://robot.invalid/hook");
        when(robots.selectByExample(any())).thenAnswer(call -> {
            io.metersphere.project.domain.ProjectRobotExample example=call.getArgument(0);
            assertTrue(example.getOredCriteria().getFirst().getAllCriteria().stream()
                    .anyMatch(c -> "project_id =".equals(c.getCondition()) && PlatformNotificationScope.ID.equals(c.getValue())));
            return List.of(robot);
        });
        var bug=new Bug();bug.setProjectId("B");bug.setTitle("bug");bug.setHandleUser("[\"only-A\"]");
        var creator=new User();creator.setName("creator");
        when(members.getEnableProjectMemberByUserId("B",List.of("only-A"))).thenReturn(List.of());
        try(var client=mockStatic(WeComClient.class)) {
            ReflectionTestUtils.invokeMethod(service,"sendWeComRobotCreateNotice",bug,creator);
            client.verifyNoInteractions();
            var handler=new User();handler.setId("only-A");handler.setWecomUserid("mapped");
            when(members.getEnableProjectMemberByUserId("B",List.of("only-A"))).thenReturn(List.of(handler));
            ReflectionTestUtils.invokeMethod(service,"sendWeComRobotCreateNotice",bug,creator);
            client.verify(() -> WeComClient.send(eq("https://robot.invalid/hook"),contains("bug"),isNull(),eq(List.of("mapped"))));
        }
    }
}
