package io.metersphere.functional.controller;

import io.metersphere.functional.handoff.*;
import io.metersphere.system.utils.SessionUtils;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/hifox-handoff")
@RequiresAuthentication
public class HandoffController {
    private final HandoffService service;
    public HandoffController(HandoffService service){this.service=service;}
    private static String configurationScope(String scope) {
        String platform=io.metersphere.system.service.PlatformNotificationScope.ID;
        if(scope!=null&&!platform.equals(scope)) throw new io.metersphere.functional.planreview.PlanReviewFailure("INVALID_CONFIG_SCOPE",400,"配置已统一为平台范围，请从消息通知入口操作");
        return platform;
    }
    public record ConfigWrite(String projectId,long revision,HandoffConfiguration configuration){ }
    public record GrantWrite(String projectId,long revision,Boolean externalGrant){ }
    public record Disable(String projectId,long revision){ }
    public record Preview(String projectId,String planId,String action,boolean newBudget){ }
    public record Confirm(String projectId,String previewId,boolean acknowledged){ }
    @GetMapping("/config") public Map<String,Object> config(@RequestParam(required=false) String projectId){return service.config(configurationScope(projectId),SessionUtils.getUserId());}
    @PostMapping("/config") public Map<String,Object> config(@RequestBody ConfigWrite r){return service.saveConfig(configurationScope(r.projectId()),r.revision(),r.configuration(),SessionUtils.getUserId());}
    @PostMapping("/grant") public Map<String,Object> grant(@RequestBody GrantWrite r){if(r.externalGrant()==null)throw new IllegalArgumentException("请明确出站授权状态");return service.grant(configurationScope(r.projectId()),r.revision(),r.externalGrant(),SessionUtils.getUserId());}
    @PostMapping("/disable") public Map<String,Object> disable(@RequestBody Disable r){return service.disable(configurationScope(r.projectId()),r.revision(),SessionUtils.getUserId());}
    @GetMapping("/object") public Map<String,Object> object(@RequestParam String projectId,@RequestParam String side,@RequestParam String objectId){return service.objectSummary(projectId,side,objectId,SessionUtils.getUserId());}
    @GetMapping("/records") public Map<String,Object> list(@RequestParam String projectId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String keyword,@RequestParam(required=false) String state,@RequestParam(required=false) Boolean sourceChanged,@RequestParam(required=false) Long from,@RequestParam(required=false) Long to){return service.list(projectId,page,size,keyword,state,sourceChanged,from,to,SessionUtils.getUserId());}
    @PostMapping("/preview") public Map<String,Object> preview(@RequestBody Preview r){return service.preview("ENABLE".equals(r.action())?configurationScope(r.projectId()):r.projectId(),r.planId(),r.action(),r.newBudget(),SessionUtils.getUserId());}
    @PostMapping("/confirm") public Map<String,Object> confirm(@RequestBody Confirm r){return service.confirm(r.projectId(),r.previewId(),r.acknowledged(),SessionUtils.getUserId());}
}
