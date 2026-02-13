package com.dragons.core.controller;

import com.dragons.core.dto.ResponseCode;
import com.dragons.core.dto.Result;
import com.dragons.core.dto.TreeHoleMessagePageResult;
import com.dragons.core.dto.TreeHoleSendMessageRequest;
import com.dragons.core.dto.TreeHoleSendMessageResult;
import com.dragons.core.dto.TreeHoleShareRequest;
import com.dragons.core.dto.TreeHoleUpdateStateRequest;
import com.dragons.core.entity.TreeHole;
import com.dragons.core.security.JwtPrincipal;
import com.dragons.core.service.ITreeHoleMessageService;
import com.dragons.core.service.ITreeHoleMessageVisibleService;
import com.dragons.core.service.ITreeHoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 树洞表 前端控制器
 * </p>
 *
 * @author aice
 * @since 2026-01-17
 */
@RestController
@RequestMapping("/api/treehole")
public class TreeHoleController {

    private final ITreeHoleMessageService treeHoleMessageService;
    private final ITreeHoleService treeHoleService;
    private final ITreeHoleMessageVisibleService treeHoleMessageVisibleService;

    @Autowired
    public TreeHoleController(ITreeHoleMessageService treeHoleMessageService,
                              ITreeHoleService treeHoleService,
                              ITreeHoleMessageVisibleService treeHoleMessageVisibleService) {
        this.treeHoleMessageService = treeHoleMessageService;
        this.treeHoleService = treeHoleService;
        this.treeHoleMessageVisibleService = treeHoleMessageVisibleService;
    }

    /**
     * 向树洞投递留言（sent）
     * POST /api/treehole/{ownerId}/sent/messages
     */
    @PostMapping("/{ownerId}/sent/messages")
    public Result<TreeHoleSendMessageResult> sendMessage(@PathVariable("ownerId") Long ownerId,
                                                         @AuthenticationPrincipal JwtPrincipal principal,
                                                         @RequestBody TreeHoleSendMessageRequest request) {
        if (principal == null) {
            return Result.error(ResponseCode.UNAUTHORIZED);
        }
        if (request == null) {
            return Result.error(ResponseCode.BAD_REQUEST);
        }
        Long messageId = treeHoleMessageService.sendMessage(
                ownerId, principal.getUserId(), request.getContent(), request.getRootMessageId());
        return Result.success("投递成功", new TreeHoleSendMessageResult(messageId));
    }

    /**
     * 获取树洞留言列表（树洞正常消息展示）
     * GET /api/treehole/{ownerId}/messages?page=1&size=10
     */
    @GetMapping("/{ownerId}/messages")
    public Result<TreeHoleMessagePageResult> listMessages(@PathVariable("ownerId") Long ownerId,
                                                          @AuthenticationPrincipal JwtPrincipal principal,
                                                          @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                                                          @RequestParam(value = "size", required = false, defaultValue = "10") Integer size) {
        if (principal == null) {
            return Result.error(ResponseCode.UNAUTHORIZED);
        }
        TreeHoleMessagePageResult data = treeHoleMessageService.listMessages(ownerId, principal.getUserId(), page, size);
        return Result.success("查询成功", data);
    }

    /**
     * 树洞主人将一条留言分享给多个树洞主人
     * POST /api/treehole/{ownerId}/messages/{messageId}/share
     */
    @PostMapping("/{ownerId}/messages/{messageId}/share")
    public Result<Void> shareMessage(@PathVariable("ownerId") Long ownerId,
                                    @PathVariable("messageId") Long messageId,
                                    @AuthenticationPrincipal JwtPrincipal principal,
                                    @RequestBody TreeHoleShareRequest request) {
        if (principal == null) {
            return Result.error(ResponseCode.UNAUTHORIZED);
        }
        if (request == null || request.getOwnerIds() == null || request.getOwnerIds().isEmpty()) {
            return Result.error(ResponseCode.BAD_REQUEST);
        }
        treeHoleMessageVisibleService.shareMessage(ownerId, messageId, request.getOwnerIds(), principal.getUserId());
        return Result.success("成功，分享完成", null);
    }

    /**
     * 获取树洞信息（用于查询树洞状态）
     * GET /api/treehole/{ownerId}
     */
    @GetMapping("/{ownerId}")
    public Result<TreeHole> getTreeHole(@PathVariable("ownerId") Long ownerId,
                                        @AuthenticationPrincipal JwtPrincipal principal) {
        if (principal == null) {
            return Result.error(ResponseCode.UNAUTHORIZED);
        }
        TreeHole treeHole = treeHoleService.getByOwnerId(ownerId);
        if (treeHole == null) {
            return Result.error(ResponseCode.NOT_FOUND);
        }
        return Result.success("查询成功", treeHole);
    }

    /**
     * 树洞主人或管理员设置树洞状态（允许/禁止投递）
     * PUT /api/treehole/{ownerId}/state
     */
    @PutMapping("/{ownerId}/state")
    public Result<Void> updateTreeHoleState(@PathVariable("ownerId") Long ownerId,
                                            @AuthenticationPrincipal JwtPrincipal principal,
                                            @RequestBody TreeHoleUpdateStateRequest request) {
        if (principal == null) {
            return Result.error(ResponseCode.UNAUTHORIZED);
        }
        if (request == null) {
            return Result.error(ResponseCode.BAD_REQUEST);
        }
        // 0=正常；2=关闭（不允许任何人投递）；
        if (request.getState() != 0 && request.getState() != 2) {
            return Result.error(ResponseCode.BAD_REQUEST);
        }
        treeHoleService.updateTreeHoleState(ownerId, principal.getUserId(), request.getState());
        return Result.success("更新成功", null);
    }
}
