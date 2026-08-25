package io.metersphere.system.wecombot;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.schedule.ScheduleManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class WecomNotificationCronScheduleServiceTests {
    private final WecomNotificationCronScheduleService service = new WecomNotificationCronScheduleService(
            mockProvider(), mock(JdbcTemplate.class));

    @Test
    void createsDailyAndWeeklyQuartzExpressions() {
        assertEquals("0 5 9 * * ?", service.cronFor(Map.of(
                "cycle_type", "DAILY", "execution_time", "09:05")));
        assertEquals("0 30 10 ? * MON,WED,FRI", service.cronFor(Map.of(
                "cycle_type", "WEEKLY", "execution_time", "10:30", "weekdays", "1,3,5")));
    }

    @Test
    void rejectsInvalidScheduleTimeAndWeekday() {
        assertThrows(MSException.class, () -> service.cronFor(Map.of(
                "cycle_type", "DAILY", "execution_time", "24:00")));
        assertThrows(MSException.class, () -> service.cronFor(Map.of(
                "cycle_type", "WEEKLY", "execution_time", "09:00", "weekdays", "0")));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ScheduleManager> mockProvider() {
        return mock(ObjectProvider.class);
    }
}
