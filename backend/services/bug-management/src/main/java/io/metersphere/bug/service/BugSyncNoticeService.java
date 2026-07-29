package io.metersphere.bug.service;

import com.google.common.collect.Maps;
import io.metersphere.bug.domain.Bug;
import io.metersphere.project.domain.ProjectRobot;
import io.metersphere.project.domain.ProjectRobotExample;
import io.metersphere.project.mapper.ProjectRobotMapper;
import io.metersphere.project.service.ProjectApplicationService;
import io.metersphere.sdk.util.JSON;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.system.domain.User;
import io.metersphere.system.domain.UserExample;
import io.metersphere.system.mapper.UserMapper;
import io.metersphere.system.notice.MessageDetail;
import io.metersphere.system.notice.NoticeModel;
import io.metersphere.system.notice.Receiver;
import io.metersphere.system.notice.constants.NoticeConstants;
import io.metersphere.system.notice.constants.NotificationConstants;
import io.metersphere.system.notice.sender.impl.InSiteNoticeSender;
import io.metersphere.system.notice.utils.MessageTemplateUtils;
import io.metersphere.system.notice.utils.WeComClient;
import io.metersphere.system.service.NoticeSendService;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class BugSyncNoticeService {

    @Resource
    private UserMapper userMapper;
    @Resource
    private NoticeSendService noticeSendService;
    @Resource
    private InSiteNoticeSender inSiteNoticeSender;
    @Resource
    private ProjectApplicationService projectApplicationService;
    @Resource
    private ProjectRobotMapper projectRobotMapper;

    @Async
    public void sendNotice(int total, String currentUser, String language, String triggerMode, String projectId) {
        String platformName = projectApplicationService.getPlatformName(projectId);
        User user = userMapper.selectByPrimaryKey(currentUser);
        setLanguage(user.getLanguage());
        Map<String, String> defaultTemplateMap = MessageTemplateUtils.getDefaultTemplateMap();
        String template = defaultTemplateMap.get(NoticeConstants.TemplateText.BUG_SYNC_TASK_EXECUTE_COMPLETED);
        Map<String, String> defaultSubjectMap = MessageTemplateUtils.getDefaultTemplateSubjectMap();
        String subject = defaultSubjectMap.get(NoticeConstants.TemplateText.BUG_SYNC_TASK_EXECUTE_COMPLETED);
        // ${OPERATOR}同步了${total}条缺陷
        Map<String, Object> paramMap = new HashMap<>(4);
        paramMap.put(NoticeConstants.RelatedUser.OPERATOR, user.getName());
        paramMap.put("total", total);
        paramMap.put("projectId", projectId);
        paramMap.put("Language", language);
        paramMap.put("platform", platformName);
        paramMap.put("triggerMode", triggerMode);
        NoticeModel noticeModel = NoticeModel.builder().operator(currentUser).excludeSelf(false)
                .context(template).subject(subject).paramMap(paramMap).event(NoticeConstants.Event.EXECUTE_COMPLETED).build();
        noticeSendService.send(NoticeConstants.TaskType.BUG_SYNC_TASK, noticeModel);
    }

    /**
     * 处理人站内通知；新建时若项目已配置企微机器人，额外 webhook @处理人
     *
     * @param bug         缺陷
     * @param currentUser 当前用户
     * @param isCreate    是否新建
     */
    @Async
    public void sendHandleUserNotice(Bug bug, String currentUser, boolean isCreate) {
        User user = userMapper.selectByPrimaryKey(currentUser);
        setLanguage(user.getLanguage());
        Map<String, String> defaultTemplateMap = MessageTemplateUtils.getDefaultTemplateMap();
        String context = defaultTemplateMap.get(NoticeConstants.TemplateText.BUG_TASK_ASSIGN);
        Map<String, String> defaultSubjectMap = MessageTemplateUtils.getDefaultTemplateSubjectMap();
        String subject = defaultSubjectMap.get(NoticeConstants.TemplateText.BUG_TASK_ASSIGN);
        // ${OPERATOR}给你分配了一个缺陷: ${title}
        Map<String, Object> paramMap = Maps.newHashMapWithExpectedSize(8);
        paramMap.put(NoticeConstants.RelatedUser.OPERATOR, user.getName());
        paramMap.put("id", bug.getId());
        paramMap.put("title", bug.getTitle());
        paramMap.put("projectId", bug.getProjectId());
        MessageDetail messageDetail = new MessageDetail();
        messageDetail.setProjectId(bug.getProjectId());
        messageDetail.setTaskType(NoticeConstants.TaskType.BUG_TASK);
        List<Receiver> receivers = parseHandleUserIds(bug.getHandleUser()).stream()
                .map(userId -> new Receiver(userId, NotificationConstants.Type.SYSTEM_NOTICE.name()))
                .toList();
        if (!receivers.isEmpty()) {
            NoticeModel noticeModel = NoticeModel.builder().operator(currentUser).excludeSelf(true).receivers(receivers)
                    .context(context).subject(subject).paramMap(paramMap).event(NoticeConstants.Event.ASSIGN).build();
            inSiteNoticeSender.sendAnnouncement(messageDetail, noticeModel, MessageTemplateUtils.getContent(context, paramMap), subject);
        }
        if (isCreate) {
            sendWeComRobotCreateNotice(bug, user);
        }
    }

    /**
     * 新建缺陷：项目启用企微机器人时，推送「创建人 + 缺陷名称」并 @处理人
     */
    private void sendWeComRobotCreateNotice(Bug bug, User creator) {
        try {
            ProjectRobotExample example = new ProjectRobotExample();
            example.createCriteria()
                    .andProjectIdEqualTo(bug.getProjectId())
                    .andPlatformEqualTo(NoticeConstants.Type.WE_COM)
                    .andEnableEqualTo(true);
            List<ProjectRobot> robots = projectRobotMapper.selectByExample(example);
            if (CollectionUtils.isEmpty(robots)) {
                return;
            }
            List<String> handleUserIds = parseHandleUserIds(bug.getHandleUser());
            if (CollectionUtils.isEmpty(handleUserIds)) {
                return;
            }
            UserExample userExample = new UserExample();
            userExample.createCriteria().andIdIn(handleUserIds);
            List<User> handlers = userMapper.selectByExample(userExample);
            List<String> mobileList = handlers.stream()
                    .map(User::getPhone)
                    .filter(StringUtils::isNotBlank)
                    .distinct()
                    .collect(Collectors.toList());
            List<String> wecomUserIds = handlers.stream()
                    .map(User::getWecomUserid)
                    .filter(StringUtils::isNotBlank)
                    .distinct()
                    .collect(Collectors.toList());
            String creatorName = creator != null && StringUtils.isNotBlank(creator.getName())
                    ? creator.getName() : (creator != null ? creator.getId() : StringUtils.EMPTY);
            String content = creatorName + " 创建了缺陷：" + StringUtils.defaultString(bug.getTitle());
            for (ProjectRobot robot : robots) {
                if (StringUtils.isBlank(robot.getWebhook())) {
                    continue;
                }
                WeComClient.send(robot.getWebhook(), content, mobileList, wecomUserIds);
            }
        } catch (Exception e) {
            LogUtils.error("send wecom robot create bug notice failed", e);
        }
    }

    private List<String> parseHandleUserIds(String raw) {
        if (StringUtils.isBlank(raw)) {
            return List.of();
        }
        String value = raw.trim();
        try {
            if (StringUtils.startsWith(value, "[")) {
                List<String> ids = JSON.parseArray(value, String.class);
                if (ids == null) {
                    return List.of();
                }
                return ids.stream().filter(StringUtils::isNotBlank).distinct().toList();
            }
        } catch (Exception e) {
            LogUtils.warn("parse handleUser json failed: " + value);
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
    }

    /**
     * 设置本地语言
     * @param language 语言
     */
    private static void setLanguage(String language) {
        Locale locale = Locale.SIMPLIFIED_CHINESE;
        if (StringUtils.containsIgnoreCase(language, "US")) {
            locale = Locale.US;
        } else if (StringUtils.containsIgnoreCase(language, "TW")){
            locale = Locale.TAIWAN;
        }
        LocaleContextHolder.setLocale(locale);
    }
}
