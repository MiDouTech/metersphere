package io.metersphere.bug.service;

import io.metersphere.bug.domain.Bug;
import io.metersphere.bug.domain.BugExample;
import io.metersphere.bug.enums.BugPlatform;
import io.metersphere.bug.mapper.BugMapper;
import io.metersphere.plugin.platform.dto.SelectOption;
import io.metersphere.plugin.platform.spi.Platform;
import io.metersphere.project.service.ProjectApplicationService;
import io.metersphere.sdk.constants.TemplateScene;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.system.service.BaseStatusFlowSettingService;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class BugStatusService {
    @Resource
    private BugMapper bugMapper;
    @Resource
    private ProjectApplicationService projectApplicationService;
    @Resource
    private BaseStatusFlowSettingService baseStatusFlowSettingService;
    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 获取表头缺陷状态选项
     * @param projectId 项目ID
     * @return 选项集合
     */
    public List<SelectOption> getHeaderStatusOption(String projectId) {
        String platformName = projectApplicationService.getPlatformName(projectId);
        if (StringUtils.equals(platformName, BugPlatform.LOCAL.getName())) {
            // Local状态流
            return getAllLocalStatusOptions(projectId);
        } else {
            // 第三方平台状态流
            Platform platform = projectApplicationService.getPlatform(projectId, true);
            String projectConfig = projectApplicationService.getProjectBugThirdPartConfig(projectId);
            String issueKey;
            if (StringUtils.equals(platformName, BugPlatform.JIRA.name())) {
                // 如果是Jira平台, 获取一条最新的缺陷默认Key作为参数
                issueKey = getJiraPlatformBugKeyLatest(projectId);
            } else {
                // 其余平台获取表头状态流暂不需要issue key参数
                issueKey = platformName;
            }
            List<SelectOption> platformStatusOption = new ArrayList<>();
            try {
                platformStatusOption = platform.getStatusTransitions(projectConfig, issueKey, null);
            } catch (Exception e) {
                LogUtils.error("获取平台状态选项有误: " + e.getMessage());
            }
            return platformStatusOption;
        }
    }

    /**
     * 获取缺陷下一批状态流转选项
     * @param projectId 项目ID
     * @param fromStatusId 当前状态选项值ID
     * @param platformBugKey 平台缺陷Key
     * @return 选项集合
     */
   public List<SelectOption> getToStatusItemOption(String projectId, String fromStatusId, String platformBugKey, Boolean showLocal) {
       String platformName = projectApplicationService.getPlatformName(projectId);
       if (StringUtils.equals(platformName, BugPlatform.LOCAL.getName()) || BooleanUtils.isTrue(showLocal)) {
           // Local状态流
           return getToStatusItemOptionOnLocal(projectId, fromStatusId);
       } else {
           // 第三方平台状态流
           // 获取配置平台, 获取第三方平台状态流
           Platform platform = projectApplicationService.getPlatform(projectId, true);
           String projectConfig = projectApplicationService.getProjectBugThirdPartConfig(projectId);
           List<SelectOption> platformOption = new ArrayList<>();
           try {
               platformOption =  platform.getStatusTransitions(projectConfig, platformBugKey, fromStatusId);
           } catch (Exception e) {
               LogUtils.error("获取平台状态选项有误: " + e.getMessage());
           }
           return platformOption;
       }
   }

   /**
    * 获取缺陷下一批状态流转选项
    * @param projectId 项目ID
    * @param fromStatusId 当前状态选项值ID
    * @return 选项集合
    */
   public List<SelectOption> getToStatusItemOptionOnLocal(String projectId, String fromStatusId) {
       String flowId = getPublishedGlobalFlowId();
       if (StringUtils.isNotBlank(flowId)) {
           if (StringUtils.isBlank(fromStatusId)) {
               return jdbcTemplate.query("SELECT id, name FROM status_item WHERE flow_id = ? AND initial_status = b'1' "
                               + "AND enabled = b'1' ORDER BY pos", (rs, rowNum) -> new SelectOption(rs.getString("name"), rs.getString("id")), flowId);
           }
           return jdbcTemplate.query("SELECT target.id, target.name FROM status_flow sf "
                           + "JOIN status_item target ON target.id = sf.to_id AND target.flow_id = sf.flow_id "
                           + "WHERE sf.flow_id = ? AND sf.from_id = ? AND sf.enabled = b'1' AND target.enabled = b'1' ORDER BY target.pos",
                   (rs, rowNum) -> new SelectOption(rs.getString("name"), rs.getString("id")), flowId, fromStatusId);
       }
       return baseStatusFlowSettingService.getStatusTransitions(projectId, TemplateScene.BUG.name(), fromStatusId);
   }

    /**
     * 获取Local状态流选项
     * @param projectId 项目ID
     * @return 状态流选项
     */
   public List<SelectOption> getAllLocalStatusOptions(String projectId) {
       String flowId = getPublishedGlobalFlowId();
       if (StringUtils.isNotBlank(flowId)) {
           return jdbcTemplate.query("SELECT id, name FROM status_item WHERE flow_id = ? AND enabled = b'1' ORDER BY pos",
                   (rs, rowNum) -> new SelectOption(rs.getString("name"), rs.getString("id")), flowId);
       }
       return baseStatusFlowSettingService.getAllStatusOption(projectId, TemplateScene.BUG.name());
   }

   private String getPublishedGlobalFlowId() {
       List<String> ids = jdbcTemplate.query("SELECT id FROM workflow_definition WHERE scene = 'BUG' AND scope_type = 'SYSTEM' "
                       + "AND scope_id = 'system' AND lifecycle = 'PUBLISHED' AND default_flow = b'1' AND enabled = b'1' LIMIT 1",
               (rs, rowNum) -> rs.getString(1));
       return ids.isEmpty() ? null : ids.getFirst();
   }

    /**
     * 获取当前项目最新的Jira平台缺陷Key (表头状态筛选需要)
     * @param projectId 项目ID
     * @return JiraKey
     */
   public String getJiraPlatformBugKeyLatest(String projectId) {
       BugExample example = new BugExample();
       example.createCriteria().andPlatformEqualTo(BugPlatform.JIRA.name()).andProjectIdEqualTo(projectId);
       example.setOrderByClause("create_time desc");
       List<Bug> bugs = bugMapper.selectByExample(example);
       if (CollectionUtils.isNotEmpty(bugs)) {
           return bugs.getFirst().getPlatformBugId();
       } else {
           return StringUtils.EMPTY;
       }
   }
}
