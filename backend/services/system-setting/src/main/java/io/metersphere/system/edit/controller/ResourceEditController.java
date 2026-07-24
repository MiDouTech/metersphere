package io.metersphere.system.edit.controller;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.dto.sdk.SessionUser;
import io.metersphere.system.edit.dto.*;
import io.metersphere.system.edit.service.ResourceEditService;
import io.metersphere.system.utils.SessionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/resource-edit")
@Tag(name = "资源编辑锁与快照")
public class ResourceEditController {

    @Resource
    private ResourceEditService resourceEditService;

    @GetMapping("/autosave-enabled")
    @Operation(summary = "自动保存是否开启")
    public Boolean autosaveEnabled() {
        return resourceEditService.isAutosaveEnabled();
    }

    @GetMapping("/writepath-snapshot-enabled")
    @Operation(summary = "写入路径单次快照是否开启")
    public Boolean writePathSnapshotEnabled() {
        return resourceEditService.isWritePathSnapshotEnabled();
    }

    @PostMapping("/lock/acquire")
    @Operation(summary = "获取编辑锁")
    public ResourceEditLockResponse acquire(@Validated @RequestBody ResourceEditLockRequest request) {
        return resourceEditService.acquire(request, currentUserId(), currentUserName());
    }

    @PostMapping("/lock/heartbeat")
    @Operation(summary = "编辑锁续期")
    public ResourceEditLockResponse heartbeat(@Validated @RequestBody ResourceEditLockRequest request) {
        return resourceEditService.heartbeat(request, currentUserId(), currentUserName());
    }

    @PostMapping("/lock/release")
    @Operation(summary = "释放编辑锁")
    public void release(@Validated @RequestBody ResourceEditLockRequest request) {
        resourceEditService.release(request, currentUserId());
    }

    @PostMapping("/snapshot")
    @Operation(summary = "业务保存成功后登记快照")
    public void snapshot(@Validated @RequestBody ResourceEditSnapshotRequest request) {
        resourceEditService.afterSuccessfulSave(request, currentUserId());
    }

    @GetMapping("/meta/{resourceType}/{resourceId}")
    @Operation(summary = "查询可 Undo/Redo 步数")
    public ResourceEditMetaResponse meta(@PathVariable String resourceType, @PathVariable String resourceId) {
        return resourceEditService.meta(resourceType, resourceId);
    }

    @PostMapping("/undo")
    @Operation(summary = "整单撤销一步")
    public ResourceEditUndoResponse undo(@Validated @RequestBody ResourceEditLockRequest request) {
        return resourceEditService.undo(request, currentUserId());
    }

    @PostMapping("/redo")
    @Operation(summary = "整单重做一步")
    public ResourceEditUndoResponse redo(@Validated @RequestBody ResourceEditLockRequest request) {
        return resourceEditService.redo(request, currentUserId());
    }

    private String currentUserId() {
        String id = SessionUtils.getUserId();
        if (StringUtils.isBlank(id)) {
            throw new MSException("未登录");
        }
        return id;
    }

    private String currentUserName() {
        SessionUser user = SessionUtils.getUser();
        if (user == null) {
            return currentUserId();
        }
        return StringUtils.defaultIfBlank(user.getName(), user.getId());
    }
}
