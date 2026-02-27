package com.dragons.core.controller;

import com.dragons.core.dto.ResponseCode;
import com.dragons.core.dto.Result;
import com.dragons.core.dto.TreeHoleMessagePageResult;
import com.dragons.core.security.JwtPrincipal;
import com.dragons.core.service.ITreeHoleMessageVisibleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 树洞消息分享区（TreeHoleMessageVisible 系列）
 *
 * @author aice
 * @since 2026-02-02
 */
@RestController
@RequestMapping("/api/treehole/message/visible")
public class TreeHoleMessageVisibleController {

    private final ITreeHoleMessageVisibleService treeHoleMessageVisibleService;

    @Autowired
    public TreeHoleMessageVisibleController(ITreeHoleMessageVisibleService treeHoleMessageVisibleService) {
        this.treeHoleMessageVisibleService = treeHoleMessageVisibleService;
    }

    /**
     * 获取“分享收件箱”列表：其他树洞主人分享给当前树洞主人的留言
     * GET /api/treehole/message/visible/shared/list?page=1&size=10
     */
    @GetMapping("/shared/list")
    public Result<TreeHoleMessagePageResult> listSharedMessages(@AuthenticationPrincipal JwtPrincipal principal,
                                                                @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                                                                @RequestParam(value = "size", required = false, defaultValue = "10") Integer size) {
        if (principal == null) {
            return Result.error(ResponseCode.UNAUTHORIZED);
        }
        TreeHoleMessagePageResult data = treeHoleMessageVisibleService.listSharedMessages(principal.getUserId(), page, size);
        return Result.success("查询成功", data);
    }
}

