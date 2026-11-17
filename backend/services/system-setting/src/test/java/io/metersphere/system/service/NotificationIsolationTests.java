package io.metersphere.system.service;

import io.metersphere.project.domain.NotificationExample;
import io.metersphere.project.mapper.NotificationMapper;
import io.metersphere.system.mapper.BaseNotificationMapper;
import io.metersphere.system.dto.sdk.request.NotificationRequest;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationIsolationTests {
    NotificationService service;NotificationMapper mapper;BaseNotificationMapper base;
    @BeforeEach void setup(){
        service=new NotificationService();mapper=mock(NotificationMapper.class);base=mock(BaseNotificationMapper.class);
        ReflectionTestUtils.setField(service,"notificationMapper",mapper);ReflectionTestUtils.setField(service,"baseNotificationMapper",base);
        when(base.listNotification(any())).thenReturn(List.of());
    }
    @Test void forgedReceiverCannotReadAnotherInbox(){
        var request=new NotificationRequest();request.setReceiver("another-user");service.listNotification(request,"current-user");
        verify(base).listNotification(argThat(value->"current-user".equals(value.getReceiver())));
    }
    @Test void forgedReceiverCannotCountAnotherInbox(){
        var request=new NotificationRequest();request.setReceiver("another-user");service.countNotification(request,"current-user");
        verify(base).listNotification(argThat(value->"current-user".equals(value.getReceiver())));
    }
    @Test void markingAResourceReadRetainsReceiverAndTypeInTheSameConjunction(){
        service.readAll("TEST_PLAN","current-user");
        verify(mapper).updateByExampleSelective(any(),argThat((NotificationExample value)->
            value.getOredCriteria().size()==1
                &&value.getOredCriteria().getFirst().getAllCriteria().stream().anyMatch(criterion->"receiver =".equals(criterion.getCondition())&&"current-user".equals(criterion.getValue()))
                &&value.getOredCriteria().getFirst().getAllCriteria().stream().anyMatch(criterion->"resource_type like".equals(criterion.getCondition())&&"%TEST_PLAN%".equals(criterion.getValue()))));
    }
}
