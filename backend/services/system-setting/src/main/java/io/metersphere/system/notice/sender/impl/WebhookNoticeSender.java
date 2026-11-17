package io.metersphere.system.notice.sender.impl;


import io.metersphere.sdk.util.LogUtils;
import io.metersphere.system.notice.utils.ClientPost;
import io.metersphere.system.notice.MessageDetail;
import io.metersphere.system.notice.NoticeModel;
import io.metersphere.system.notice.Receiver;
import io.metersphere.system.notice.sender.AbstractNoticeSender;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class WebhookNoticeSender extends AbstractNoticeSender {


    private void send(MessageDetail messageDetail, NoticeModel noticeModel, String context) {
        List<Receiver> receivers = super.getReceivers(noticeModel.getReceivers(), noticeModel.isExcludeSelf(), noticeModel.getOperator());
        if (CollectionUtils.isEmpty(receivers)) {
            return;
        }
        if (CollectionUtils.isNotEmpty(receivers)) {
            List<String> userIds = receivers.stream()
                    .map(Receiver::getUserId)
                    .distinct()
                    .collect(Collectors.toList());

            LogUtils.info("Webhook收件人: {}", userIds);
        }

        ClientPost.executeJson(messageDetail.getWebhook(), context);
    }

    @Override
    public void send(MessageDetail messageDetail, NoticeModel noticeModel) {
        String context = super.getContext(messageDetail, noticeModel);
        send(messageDetail, noticeModel, context);
    }

}
