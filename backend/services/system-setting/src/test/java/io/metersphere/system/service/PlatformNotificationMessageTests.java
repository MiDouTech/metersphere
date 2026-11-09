package io.metersphere.system.service;

import io.metersphere.project.domain.*;
import io.metersphere.project.mapper.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlatformNotificationMessageTests {
    @Test void ordinaryEventsShareConfigurationButRetainEachSourceProject() {
        var tasks=mock(MessageTaskMapper.class);
        var blobs=mock(MessageTaskBlobMapper.class);
        var robots=mock(ProjectRobotMapper.class);
        var service=new MessageDetailService();
        ReflectionTestUtils.setField(service,"messageTaskMapper",tasks);
        ReflectionTestUtils.setField(service,"messageTaskBlobMapper",blobs);
        ReflectionTestUtils.setField(service,"projectRobotMapper",robots);
        var task=new MessageTask();
        task.setId("task");task.setProjectId(PlatformNotificationScope.ID);
        task.setTaskType("FUNCTIONAL_CASE_TASK");task.setEvent("UPDATE");
        task.setProjectRobotId("robot");task.setReceivers(List.of("CREATE_USER","only-in-A"));
        task.setUseDefaultTemplate(false);task.setUseDefaultSubject(false);task.setSubject("subject");task.setCreateTime(1L);
        var blob=new MessageTaskBlob();blob.setId("task");blob.setTemplate("template");
        var robot=new ProjectRobot();robot.setId("robot");robot.setPlatform("IN_SITE");
        when(tasks.selectByExample(any())).thenAnswer(call -> {
            MessageTaskExample example=call.getArgument(0);
            assertTrue(example.getOredCriteria().getFirst().getAllCriteria().stream()
                    .anyMatch(c -> "project_id =".equals(c.getCondition()) && PlatformNotificationScope.ID.equals(c.getValue())));
            assertTrue(example.getOredCriteria().getFirst().getAllCriteria().stream()
                    .anyMatch(c -> "test_id =".equals(c.getCondition()) && "NONE".equals(c.getValue())));
            return List.of(task);
        });
        when(blobs.selectByExampleWithBLOBs(any())).thenReturn(List.of(blob));
        when(robots.selectByExample(any())).thenReturn(List.of(robot));
        var a=service.searchMessageByTypeAndProjectId("FUNCTIONAL_CASE_TASK","A").getFirst();
        var b=service.searchMessageByTypeAndProjectId("FUNCTIONAL_CASE_TASK","B").getFirst();
        assertEquals("A",a.getProjectId());assertEquals("B",b.getProjectId());
        assertEquals(a.getReceiverIds(),b.getReceiverIds());
        verify(tasks,times(2)).selectByExample(any());
    }
}
