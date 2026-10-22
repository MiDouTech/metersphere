package io.metersphere.functional.service;


import io.metersphere.functional.constants.CaseFileSourceType;
import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.domain.FunctionalCaseComment;
import io.metersphere.functional.domain.FunctionalCaseCommentExample;
import io.metersphere.functional.dto.FunctionalCaseCommentDTO;
import io.metersphere.functional.dto.FunctionalCaseDTO;
import io.metersphere.functional.mapper.FunctionalCaseCommentMapper;
import io.metersphere.functional.mapper.FunctionalCaseMapper;
import io.metersphere.functional.request.FunctionalCaseCommentRequest;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.BeanUtils;
import io.metersphere.sdk.util.Translator;
import io.metersphere.system.domain.User;
import io.metersphere.system.dto.CommentUserInfo;
import io.metersphere.system.mapper.BaseUserMapper;
import io.metersphere.system.mapper.UserMapper;
import io.metersphere.system.notice.NoticeModel;
import io.metersphere.system.notice.constants.NoticeConstants;
import io.metersphere.system.notice.utils.MessageTemplateUtils;
import io.metersphere.system.service.NoticeSendService;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.beanutils.BeanMap;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author guoyuqi
 */

@Service
public class FunctionalCaseCommentService {

    @Resource
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Resource private FunctionalCaseCommentAccess commentAccess;
    @Resource private FunctionalCaseCommentAudit commentAudit;
    @Resource private FunctionalCaseCommentLifecycleService commentLifecycle;
    @Resource private FunctionalCaseCommentOutbox commentOutbox;

    @Resource
    private FunctionalCaseCommentMapper functionalCaseCommentMapper;

    @Resource
    private FunctionalCaseMapper functionalCaseMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private FunctionalCaseNoticeService functionalCaseNoticeService;

    @Resource
    private NoticeSendService noticeSendService;

    @Resource
    private BaseUserMapper baseUserMapper;

    @Resource
    private FunctionalCaseAttachmentService functionalCaseAttachmentService;



    /**
     * 新增评论
     *
     * @param functionalCaseCommentRequest functionalCaseCommentDTO
     * @param userId                       当前操作用户
     * @return FunctionalCaseComment
     */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public FunctionalCaseComment saveComment(FunctionalCaseCommentRequest functionalCaseCommentRequest, String userId) {
        FunctionalCase functionalCase = commentAccess.requireCase(functionalCaseCommentRequest.getCaseId(), userId, true);
        validateContentAndRecipients(functionalCaseCommentRequest, functionalCase.getProjectId());
        if (!List.of(NoticeConstants.Event.COMMENT, NoticeConstants.Event.AT, NoticeConstants.Event.REPLY).contains(Objects.toString(functionalCaseCommentRequest.getEvent(), "")))
            throw FunctionalCaseCommentAccess.invalid("评论事件无效");
        if (!NoticeConstants.Event.REPLY.equals(functionalCaseCommentRequest.getEvent())
                && (StringUtils.isNotBlank(functionalCaseCommentRequest.getParentId()) || StringUtils.isNotBlank(functionalCaseCommentRequest.getReplyUser())))
            throw FunctionalCaseCommentAccess.invalid("回复评论必须指定有效的回复事件");
        FunctionalCaseComment functionalCaseComment = getFunctionalCaseComment(functionalCaseCommentRequest, userId);
        functionalCaseCommentRequest.setId(functionalCaseComment.getId());
        functionalCaseCommentRequest.setProjectId(functionalCase.getProjectId());
        FunctionalCaseComment saved;
        if (StringUtils.equals(functionalCaseCommentRequest.getEvent(), NoticeConstants.Event.REPLY)) {
            saved = saveCommentWidthReply(functionalCaseCommentRequest, functionalCaseComment, userId);
        } else {
            saved = saveCommentOrAt(functionalCaseCommentRequest, functionalCaseComment, userId);
        }
        commentAudit.record(saved.getId(), saved.getCaseId(), userId, "CREATE", saved.getUpdateTime(), "", saved.getContent());
        return saved;
    }

    private void validateContentAndRecipients(FunctionalCaseCommentRequest request, String projectId) {
        if (StringUtils.isBlank(request.getContent()) || request.getContent().length() > 20000)
            throw FunctionalCaseCommentAccess.invalid("评论内容不能为空或超过 20000 字符");
        if (StringUtils.isNotBlank(request.getNotifier())) {
            String[] raw = request.getNotifier().split(";", -1);
            if (raw.length > 100) throw FunctionalCaseCommentAccess.invalid("评论通知人数不能超过 100 人");
            Set<String> recipients = new LinkedHashSet<>(Arrays.asList(raw));
            for (String recipient : recipients) {
                if (StringUtils.isBlank(recipient) || !commentAccess.hasPermission(recipient, projectId, io.metersphere.sdk.constants.PermissionConstants.FUNCTIONAL_CASE_READ))
                    throw FunctionalCaseCommentAccess.denied();
            }
            request.setNotifier(String.join(";", recipients));
        }
    }



    /**
     * 组装除通知人，被回复的id外的其他用例评论属性
     *
     * @param functionalCaseCommentRequest 页面参数
     * @param userId                       当前操作人
     * @return FunctionalCaseComment
     */
    private static FunctionalCaseComment getFunctionalCaseComment(FunctionalCaseCommentRequest functionalCaseCommentRequest, String userId) {
        FunctionalCaseComment functionalCaseComment = new FunctionalCaseComment();
        functionalCaseComment.setId(IDGenerator.nextStr());
        functionalCaseComment.setCaseId(functionalCaseCommentRequest.getCaseId());
        functionalCaseComment.setContent(functionalCaseCommentRequest.getContent());
        functionalCaseComment.setCreateUser(userId);
        functionalCaseComment.setCreateTime(System.currentTimeMillis());
        functionalCaseComment.setUpdateTime(System.currentTimeMillis());
        return functionalCaseComment;
    }

    private FunctionalCase checkCase(String caseId) {
        FunctionalCase functionalCase = functionalCaseMapper.selectByPrimaryKey(caseId);
        if (functionalCase == null || Boolean.TRUE.equals(functionalCase.getDeleted())) {
            throw new MSException(Translator.get("case_comment.case_is_null"));
        }
        return functionalCase;
    }

    /**
     * 非REPLAY事件，保存
     * @param functionalCaseCommentRequest 页面参数
     * @param functionalCaseComment 被组装的半份数据
     * @return FunctionalCaseComment
     */
    public FunctionalCaseComment saveCommentOrAt(FunctionalCaseCommentRequest functionalCaseCommentRequest, FunctionalCaseComment functionalCaseComment, String userId) {
        if (StringUtils.isNotBlank(functionalCaseCommentRequest.getNotifier())) {
            functionalCaseComment.setNotifier(functionalCaseCommentRequest.getNotifier());
        }
        functionalCaseCommentMapper.insert(functionalCaseComment);
        FunctionalCaseDTO functionalCaseDTO = functionalCaseNoticeService.getFunctionalCaseDTO(functionalCaseCommentRequest);
        //保存文件
        functionalCaseAttachmentService.uploadMinioFile(functionalCaseCommentRequest.getCaseId(),functionalCaseCommentRequest.getProjectId(),functionalCaseCommentRequest.getUploadFileIds(), userId, CaseFileSourceType.CASE_COMMENT.toString());
        //发送@ 通知人
        sendNotice(functionalCaseCommentRequest, userId, functionalCaseDTO);
        //发送系统设置的评论通知
        if (StringUtils.isBlank(functionalCaseCommentRequest.getParentId()) && !StringUtils.equals(functionalCaseCommentRequest.getEvent(),NoticeConstants.Event.COMMENT)) {
            functionalCaseCommentRequest.setEvent(NoticeConstants.Event.COMMENT);
            sendNotice(functionalCaseCommentRequest, userId, functionalCaseDTO);
        }
        return functionalCaseComment;
    }

    /**
     * 如果是REPLAY事件，需要再次发送回复被@的通知
     * @param functionalCaseCommentRequest 页面参数
     * @param functionalCaseComment 被组装的半份数据
     * @return FunctionalCaseComment
     */
    public FunctionalCaseComment saveCommentWidthReply(FunctionalCaseCommentRequest functionalCaseCommentRequest, FunctionalCaseComment functionalCaseComment, String userId) {
        setReplyAndNotifier(functionalCaseCommentRequest, functionalCaseComment);
        functionalCaseCommentMapper.insert(functionalCaseComment);
        FunctionalCaseDTO functionalCaseDTOReply = functionalCaseNoticeService.getFunctionalCaseDTO(functionalCaseCommentRequest);
        sendNotice(functionalCaseCommentRequest, userId, functionalCaseDTOReply);
        functionalCaseCommentRequest.setEvent(NoticeConstants.Event.AT);
        FunctionalCaseDTO functionalCaseDTO = functionalCaseNoticeService.getFunctionalCaseDTO(functionalCaseCommentRequest);
        //保存文件
        functionalCaseAttachmentService.uploadMinioFile(functionalCaseCommentRequest.getCaseId(),functionalCaseCommentRequest.getProjectId(),functionalCaseCommentRequest.getUploadFileIds(), userId, CaseFileSourceType.CASE_COMMENT.toString());
        //发通知
        sendNotice(functionalCaseCommentRequest, userId, functionalCaseDTO);
        return functionalCaseComment;
    }

    private void setReplyAndNotifier(FunctionalCaseCommentRequest functionalCaseCommentRequest, FunctionalCaseComment functionalCaseComment) {
        checkParentId(functionalCaseCommentRequest, functionalCaseComment);
        if (StringUtils.isBlank(functionalCaseCommentRequest.getReplyUser())) {
            throw new MSException(Translator.get("case_comment.reply_user_is_null"));
        }
        functionalCaseComment.setReplyUser(functionalCaseCommentRequest.getReplyUser());
        if (StringUtils.isNotBlank(functionalCaseCommentRequest.getNotifier())) {
            functionalCaseComment.setNotifier(functionalCaseCommentRequest.getNotifier());
        } else {
            functionalCaseComment.setNotifier("");
        }
    }


    public void sendNotice(FunctionalCaseCommentRequest functionalCaseCommentRequest, String userId, FunctionalCaseDTO functionalCaseDTO) {
        Map<String, String> defaultTemplateMap = MessageTemplateUtils.getDefaultTemplateMap();
        String template = defaultTemplateMap.get(NoticeConstants.TaskType.FUNCTIONAL_CASE_TASK + "_" + functionalCaseCommentRequest.getEvent());
        Map<String, String> defaultSubjectMap = MessageTemplateUtils.getDefaultTemplateSubjectMap();
        String subject = defaultSubjectMap.get(NoticeConstants.TaskType.FUNCTIONAL_CASE_TASK + "_" + functionalCaseCommentRequest.getEvent());
        List<String> relatedUsers = getRelatedUsers(functionalCaseDTO.getRelatedUsers());
        User user = userMapper.selectByPrimaryKey(userId);
        setLanguage(user.getLanguage());
        BeanMap beanMap = new BeanMap(functionalCaseDTO);
        Map paramMap = new HashMap<>(beanMap);
        paramMap.put(NoticeConstants.RelatedUser.OPERATOR, user.getName());
        NoticeModel noticeModel = NoticeModel.builder()
                .operator(userId)
                .context(template)
                .subject(subject)
                .paramMap(paramMap)
                .event(functionalCaseCommentRequest.getEvent())
                .status((String) paramMap.get("status"))
                .excludeSelf(true)
                .relatedUsers(relatedUsers)
                .build();
        Long version = jdbc.queryForObject("SELECT update_time FROM functional_case_comment WHERE id=?", Long.class, functionalCaseCommentRequest.getId());
        commentOutbox.enqueue(functionalCaseCommentRequest.getId(), Objects.requireNonNull(version), functionalCaseCommentRequest.getCaseId(), noticeModel);
    }

    private static void setLanguage(String language) {
        Locale locale = Locale.SIMPLIFIED_CHINESE;
        if (StringUtils.containsIgnoreCase(language, "US")) {
            locale = Locale.US;
        } else if (StringUtils.containsIgnoreCase(language, "TW")){
            locale = Locale.TAIWAN;
        }
        LocaleContextHolder.setLocale(locale);
    }

    /**
     * 检查被回复的评论是否为空
     * @param functionalCaseCommentRequest 页面参数
     * @param functionalCaseComment 被组装的半份数据
     */
    private void checkParentId(FunctionalCaseCommentRequest functionalCaseCommentRequest, FunctionalCaseComment functionalCaseComment) {
        String parentId = functionalCaseCommentRequest.getParentId();
        if (StringUtils.isBlank(parentId)) {
            throw new MSException(Translator.get("case_comment.parent_id_is_null"));
        }
        var rows = jdbc.queryForList("SELECT id,case_id,parent_id,create_user,deleted FROM functional_case_comment WHERE id=? FOR UPDATE", parentId);
        if (rows.size() != 1 || !Objects.equals(rows.getFirst().get("case_id"), functionalCaseCommentRequest.getCaseId()))
            throw FunctionalCaseCommentAccess.denied();
        var parent = rows.getFirst();
        if (StringUtils.isNotBlank((String) parent.get("parent_id")) && Boolean.TRUE.equals(parent.get("deleted")))
            throw FunctionalCaseCommentAccess.conflict();
        String root = StringUtils.defaultIfBlank((String) parent.get("parent_id"), parentId);
        String replyUser = functionalCaseCommentRequest.getReplyUser();
        Integer related = jdbc.queryForObject("SELECT COUNT(*) FROM functional_case_comment WHERE case_id=? AND create_user=? AND deleted=0 AND (id=? OR parent_id=?)",
                Integer.class, functionalCaseCommentRequest.getCaseId(), replyUser, root, root);
        if (related == null || related == 0 || !commentAccess.hasPermission(replyUser, functionalCaseCommentRequest.getProjectId(), io.metersphere.sdk.constants.PermissionConstants.FUNCTIONAL_CASE_READ))
            throw FunctionalCaseCommentAccess.denied();
        functionalCaseComment.setParentId(root);
        functionalCaseCommentRequest.setParentId(root);
    }

    private List<String> getRelatedUsers(Object relatedUsers) {
        String relatedUser = (String) relatedUsers;
        List<String> relatedUserList = new ArrayList<>();
        if (StringUtils.isNotBlank(relatedUser)) {
            relatedUserList = Arrays.asList(relatedUser.split(";"));
        }
        return relatedUserList;
    }

    public void deleteComment(String commentId, String userId) {
        throw FunctionalCaseCommentAccess.invalid("请刷新页面，使用带评论版本的删除操作");
    }

    public List<FunctionalCaseCommentDTO> getCommentList(String caseId) {
        return commentLifecycle.legacyList(caseId, io.metersphere.system.utils.SessionUtils.getUserId());
    }

    /**
     * 组装需要返回前端的数据结构
     * @param functionalCaseComments 查出来的所有当前用例的评论信息
     * @param userMap 用户信息
     */
    private List<FunctionalCaseCommentDTO> buildData(List<FunctionalCaseComment> functionalCaseComments, Map<String, CommentUserInfo> userMap) {
        List<FunctionalCaseCommentDTO>list = new ArrayList<>();
        for (FunctionalCaseComment functionalCaseComment : functionalCaseComments) {
            FunctionalCaseCommentDTO functionalCaseCommentDTO = new FunctionalCaseCommentDTO();
            List<CommentUserInfo>commentUserInfos = new ArrayList<>();
            BeanUtils.copyBean(functionalCaseCommentDTO,functionalCaseComment);
            CommentUserInfo createUserInfo = userMap.get(functionalCaseComment.getCreateUser());
            commentUserInfos.add(createUserInfo);
            if (StringUtils.isNotBlank(functionalCaseComment.getReplyUser())) {
                CommentUserInfo replyUserInfo = userMap.get(functionalCaseComment.getReplyUser());
                commentUserInfos.add(replyUserInfo);
            }
            if (StringUtils.isNotBlank(functionalCaseComment.getNotifier())) {
                List<String> notifiers = Arrays.asList(functionalCaseComment.getNotifier().split(";"));
                notifiers.forEach(t-> commentUserInfos.add(userMap.get(t)));
            }
            functionalCaseCommentDTO.setCommentUserInfos(commentUserInfos.stream().distinct().toList());
            list.add(functionalCaseCommentDTO);
        }

        List<FunctionalCaseCommentDTO> rootList = list.stream().filter(t -> StringUtils.isBlank(t.getParentId())).sorted(Comparator.comparing(FunctionalCaseComment::getCreateTime).reversed()).toList();
        List<FunctionalCaseCommentDTO> replyList = list.stream().filter(t -> StringUtils.isNotBlank(t.getParentId())).sorted(Comparator.comparing(FunctionalCaseComment::getCreateTime).reversed()).toList();
        Map<String, List<FunctionalCaseCommentDTO>> commentMap = replyList.stream().collect(Collectors.groupingBy(FunctionalCaseComment::getParentId));
        for (FunctionalCaseCommentDTO functionalCaseComment : rootList) {
            List<FunctionalCaseCommentDTO> replyComments = commentMap.get(functionalCaseComment.getId());
            if (CollectionUtils.isNotEmpty(replyComments)) {
                functionalCaseComment.setChildComments(replyComments);
            } else {
                functionalCaseComment.setChildComments(new ArrayList<>());
            }
        }
        return rootList;
    }

    /**
     * 根据userIds 获取user信息
     * @param userIds userIds
     * @return Map<String, User>
     */
    private Map<String, CommentUserInfo> getUserMap(List<String> userIds) {
        List<CommentUserInfo> commentUserInfos = baseUserMapper.getCommentUserInfoByIds(userIds);
        return commentUserInfos.stream().collect(Collectors.toMap(CommentUserInfo::getId, item -> item));
    }

    /**
     * 获取评论里所有人员信息
     * @param functionalCaseComments 评论集合
     * @return List<String>userIds
     */
    private static List<String> getUserIds(List<FunctionalCaseComment> functionalCaseComments) {
        List<String> userIds = new ArrayList<>(functionalCaseComments.stream().flatMap(functionalCaseComment -> Stream.of(functionalCaseComment.getCreateUser(), functionalCaseComment.getReplyUser())).toList());
        List<String> notifierList = functionalCaseComments.stream().map(FunctionalCaseComment::getNotifier).filter(StringUtils::isNotBlank).toList();
        for (String notifierStr : notifierList) {
            List<String> notifiers = Arrays.asList(notifierStr.split(";"));
            userIds.addAll(notifiers);
        }
        userIds = userIds.stream().filter(StringUtils::isNotBlank).distinct().toList();
        return userIds;
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public FunctionalCaseComment updateComment(FunctionalCaseCommentRequest functionalCaseCommentRequest, String userId) {
        FunctionalCase ownerCase = commentAccess.requireCase(functionalCaseCommentRequest.getCaseId(), userId, true);
        if (Boolean.TRUE.equals(ownerCase.getDeleted())) throw new MSException("用例已删除，不能编辑评论");
        if (functionalCaseCommentRequest.getExpectedUpdateTime() == null
                || StringUtils.isBlank(functionalCaseCommentRequest.getContent())
                || functionalCaseCommentRequest.getContent().length() > 20000) {
            throw FunctionalCaseCommentAccess.invalid("请刷新评论后重试，评论内容不能为空或超过限制");
        }
        var rows = jdbc.queryForList("SELECT case_id,create_user,update_time,content,deleted,notifier FROM functional_case_comment WHERE id=? FOR UPDATE",
                functionalCaseCommentRequest.getId());
        if (rows.isEmpty() || !Objects.equals(rows.get(0).get("case_id"), ownerCase.getId())
                || !Objects.equals(rows.get(0).get("create_user"), userId)) {
            throw FunctionalCaseCommentAccess.denied();
        }
        long version = ((Number) rows.get(0).get("update_time")).longValue();
        if (version != functionalCaseCommentRequest.getExpectedUpdateTime() || Boolean.TRUE.equals(rows.getFirst().get("deleted"))) {
            throw FunctionalCaseCommentAccess.conflict();
        }
        functionalCaseCommentRequest.setProjectId(ownerCase.getProjectId());
        boolean changedRecipients = !Objects.equals(StringUtils.defaultString(functionalCaseCommentRequest.getNotifier()),
                Objects.toString(rows.getFirst().get("notifier"), ""));
        if (changedRecipients) validateContentAndRecipients(functionalCaseCommentRequest, ownerCase.getProjectId());
        functionalCaseCommentRequest.setEvent(changedRecipients && StringUtils.isNotBlank(functionalCaseCommentRequest.getNotifier())
                ? NoticeConstants.Event.AT : NoticeConstants.Event.COMMENT);
        FunctionalCaseComment functionalCaseComment = getFunctionalCaseCommentByUpdate(functionalCaseCommentRequest);
        functionalCaseComment.setUpdateTime(Math.max(System.currentTimeMillis(), version + 1));
        commentAudit.record(functionalCaseComment.getId(), ownerCase.getId(), userId, "UPDATE", functionalCaseComment.getUpdateTime(),
                Objects.toString(rows.getFirst().get("content"), ""), functionalCaseComment.getContent());
        // Editing never changes the parent/reply relationship and does not resend unchanged mentions.
        return updateCommentWidthOutNotice(functionalCaseCommentRequest, functionalCaseComment, userId);
    }

    private FunctionalCaseComment updateCommentWidthOutNotice(FunctionalCaseCommentRequest functionalCaseCommentRequest, FunctionalCaseComment functionalCaseComment, String userId) {
        if (StringUtils.isNotBlank(functionalCaseCommentRequest.getNotifier())) {
            functionalCaseComment.setNotifier(functionalCaseCommentRequest.getNotifier());
        } else {
            functionalCaseComment.setNotifier("");
        }
        functionalCaseCommentMapper.updateByPrimaryKeySelective(functionalCaseComment);
        FunctionalCaseDTO functionalCaseDTO = functionalCaseNoticeService.getFunctionalCaseDTO(functionalCaseCommentRequest);
        if (StringUtils.isNotBlank(functionalCaseCommentRequest.getNotifier()) && StringUtils.equals(functionalCaseCommentRequest.getEvent(),NoticeConstants.Event.AT)) {
            sendNotice(functionalCaseCommentRequest, userId, functionalCaseDTO);
        }
        return  functionalCaseComment;
    }

    private FunctionalCaseComment updateCommentWidthNotice(FunctionalCaseCommentRequest functionalCaseCommentRequest, FunctionalCaseComment functionalCaseComment, String userId) {
        setReplyAndNotifier(functionalCaseCommentRequest, functionalCaseComment);
        functionalCaseCommentMapper.updateByPrimaryKeySelective(functionalCaseComment);
        functionalCaseCommentRequest.setEvent(NoticeConstants.Event.AT);
        FunctionalCaseDTO functionalCaseDTO = functionalCaseNoticeService.getFunctionalCaseDTO(functionalCaseCommentRequest);
        //发通知
        if (StringUtils.isNotBlank(functionalCaseCommentRequest.getNotifier())) {
            sendNotice(functionalCaseCommentRequest, userId, functionalCaseDTO);
        }
        return functionalCaseComment;
    }

    private FunctionalCaseComment getFunctionalCaseCommentByUpdate(FunctionalCaseCommentRequest functionalCaseCommentRequest) {
        FunctionalCaseComment functionalCaseComment = new FunctionalCaseComment();
        functionalCaseComment.setId(functionalCaseCommentRequest.getId());
        functionalCaseComment.setCaseId(functionalCaseCommentRequest.getCaseId());
        functionalCaseComment.setContent(functionalCaseCommentRequest.getContent());
        functionalCaseComment.setCreateUser(null);
        functionalCaseComment.setCreateTime(null);
        functionalCaseComment.setUpdateTime(System.currentTimeMillis());
        return functionalCaseComment;
    }
}
