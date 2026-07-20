package io.metersphere.sdk.config;

import io.metersphere.sdk.constants.KafkaTopicConstants;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KafkaTopicService {

    @Value("${kafka.topic.prefix:}")
    private String topicPrefix;

    @Value("${kafka.consumer.group.suffix:}")
    private String consumerGroupSuffix;

    public String topic(String baseTopic) {
        if (StringUtils.isBlank(topicPrefix)) {
            return baseTopic;
        }
        return topicPrefix + baseTopic;
    }

    public String consumerGroup(String baseGroupId) {
        if (StringUtils.isBlank(consumerGroupSuffix)) {
            return baseGroupId;
        }
        return baseGroupId + consumerGroupSuffix;
    }

    public String pluginTopic() {
        return topic(KafkaTopicConstants.PLUGIN);
    }

    public String exportTopic() {
        return topic(KafkaTopicConstants.EXPORT);
    }

    public String apiReportTopic() {
        return topic(KafkaTopicConstants.API_REPORT_TOPIC);
    }

    public String apiReportTaskTopic() {
        return topic(KafkaTopicConstants.API_REPORT_TASK_TOPIC);
    }

    public String apiReportDebugTopic() {
        return topic(KafkaTopicConstants.API_REPORT_DEBUG_TOPIC);
    }
}
