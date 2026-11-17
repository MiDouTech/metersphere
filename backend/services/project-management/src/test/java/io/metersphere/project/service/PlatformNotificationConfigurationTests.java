package io.metersphere.project.service;

import io.metersphere.project.domain.ProjectRobot;
import io.metersphere.project.mapper.*;
import io.metersphere.sdk.util.Translator;
import io.metersphere.system.mapper.UserMapper;
import io.metersphere.system.service.PlatformNotificationScope;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlatformNotificationConfigurationTests {
    @Test void firstReadBuildsUsableDefaultsWithoutSavingOrChoosingAProject() throws Exception {
        var service=new NoticeMessageTaskService();
        var tasks=mock(MessageTaskMapper.class);var blobs=mock(MessageTaskBlobMapper.class);
        var robots=mock(ProjectRobotMapper.class);var users=mock(UserMapper.class);
        ReflectionTestUtils.setField(service,"platformScope",mock(PlatformNotificationScope.class));
        ReflectionTestUtils.setField(service,"messageTaskMapper",tasks);
        ReflectionTestUtils.setField(service,"messageTaskBlobMapper",blobs);
        ReflectionTestUtils.setField(service,"projectRobotMapper",robots);
        ReflectionTestUtils.setField(service,"userMapper",users);
        when(tasks.selectByExample(any())).thenReturn(List.of());
        when(blobs.selectByExampleWithBLOBs(any())).thenReturn(List.of());
        when(users.selectByExample(any())).thenReturn(List.of());
        var robot=new ProjectRobot();robot.setId("platform-site");robot.setPlatform("IN_SITE");robot.setName("robot_in_site");
        when(robots.selectByExample(any())).thenReturn(List.of(robot));
        MessageSource previous=(MessageSource)ReflectionTestUtils.getField(Translator.class,"messageSource");
        new Translator().setMessageSource(new StaticMessageSource());
        try (var beans=mockStatic(io.metersphere.sdk.util.CommonBeanFactory.class)) {
            beans.when(() -> io.metersphere.sdk.util.CommonBeanFactory.getBean(UserMapper.class)).thenReturn(users);
            var result=service.getMessageList(PlatformNotificationScope.ID);
            assertFalse(result.isEmpty());
            var rows=result.stream().flatMap(module -> module.getMessageTaskTypeDTOList().stream())
                    .flatMap(type -> type.getMessageTaskDetailDTOList().stream()).toList();
            assertFalse(rows.isEmpty());
            rows.forEach(row -> {
                assertNotNull(row.getProjectRobotConfigMap().get("platform-site"));
                assertFalse(row.getProjectRobotConfigMap().get("platform-site").getEnable());
            });
            verify(tasks,never()).insert(any());
            verify(robots,never()).insert(any());
        } finally { new Translator().setMessageSource(previous); }
    }
}
