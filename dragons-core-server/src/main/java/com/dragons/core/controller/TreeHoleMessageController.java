package com.dragons.core.controller;

import com.dragons.core.dto.ResponseCode;
import com.dragons.core.dto.Result;
import com.dragons.core.security.JwtPrincipal;
import com.dragons.core.service.ITreeHoleMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 树洞信息内容表 前端控制器
 * </p>
 *
 * @author aice
 * @since 2026-01-17
 */
@RestController
@RequestMapping("/api/treehole/messages")
public class TreeHoleMessageController {

    private final ITreeHoleMessageService treeHoleMessageService;

    @Autowired
    public TreeHoleMessageController(ITreeHoleMessageService treeHoleMessageService) {
        this.treeHoleMessageService = treeHoleMessageService;
    }

    /**
     * 树洞主人将留言标记为已读
     * PUT /api/treehole/messages/{messageId}/read
     */
    @PutMapping("/{messageId}/read")
    public Result<Void> markRead(@PathVariable("messageId") Long messageId,
                                 @AuthenticationPrincipal JwtPrincipal principal) {
        if (principal == null) {
            return Result.error(ResponseCode.UNAUTHORIZED);
        }
        if (messageId == null || messageId <= 0) {
            return Result.error(ResponseCode.BAD_REQUEST);
        }
        treeHoleMessageService.markMessageRead(messageId, principal.getUserId());
        return Result.success("已读成功", null);
    }

    /**
     * 树洞主人删除留言（全局删除）
     * DELETE /api/treehole/messages/{messageId}
     */
    @DeleteMapping("/{messageId}")
    public Result<Void> deleteByOwner(@PathVariable("messageId") Long messageId,
                                      @AuthenticationPrincipal JwtPrincipal principal) {
        if (principal == null) {
            return Result.error(ResponseCode.UNAUTHORIZED);
        }
        if (messageId == null || messageId <= 0) {
            return Result.error(ResponseCode.BAD_REQUEST);
        }
        treeHoleMessageService.deleteMessageByOwner(messageId, principal.getUserId());
        return Result.success("删除成功", null);
    }

    /**
     * 发送者删除留言（仅对发送者不可见）
     * DELETE /api/treehole/messages/{messageId}/sender
     */
    @DeleteMapping("/{messageId}/sender")
    public Result<Void> deleteBySender(@PathVariable("messageId") Long messageId,
                                       @AuthenticationPrincipal JwtPrincipal principal) {
        if (principal == null) {
            return Result.error(ResponseCode.UNAUTHORIZED);
        }
        if (messageId == null || messageId <= 0) {
            return Result.error(ResponseCode.BAD_REQUEST);
        }
        treeHoleMessageService.deleteMessageBySender(messageId, principal.getUserId());
        return Result.success("删除成功", null);
    }
}
