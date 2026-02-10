package com.dragons.core.controller;

import com.dragons.core.dto.ResponseCode;
import com.dragons.core.dto.Result;
import com.dragons.core.dto.TreeHoleBlockRequest;
import com.dragons.core.exception.BusinessException;
import com.dragons.core.security.JwtPrincipal;
import com.dragons.core.service.ITreeHoleBlacklistService;
import com.dragons.core.service.ITreeHoleService;
import com.dragons.core.entity.TreeHole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 树洞黑名单表 前端控制器
 * </p>
 *
 * @author aice
 * @since 2026-02-03
 */
@RestController
@RequestMapping("/api/treeholeBlacklist")
public class TreeHoleBlacklistController {

    private final ITreeHoleBlacklistService treeHoleBlacklistService;
    private final ITreeHoleService treeHoleService;

    @Autowired
    public TreeHoleBlacklistController(ITreeHoleBlacklistService treeHoleBlacklistService,
                                       ITreeHoleService treeHoleService) {
        this.treeHoleBlacklistService = treeHoleBlacklistService;
        this.treeHoleService = treeHoleService;
    }

    /**
     * 树洞主人拉黑用户
     * POST /api/treeholeBlacklist/block
     * 请求体：{ "blockedUserId": 被拉黑用户ID, "reason": "原因（可选）" }
     * 仅树洞主人可调用（当前用户须拥有树洞）；保证 owner_id + blocked_user_id 唯一，已存在且生效中则静默成功，已解除则改回生效，不存在则新增。
     */
    @PostMapping("/block")
    public Result<Void> block(@AuthenticationPrincipal JwtPrincipal principal,
                             @RequestBody TreeHoleBlockRequest request) {
        if (principal == null) {
            return Result.error(ResponseCode.UNAUTHORIZED);
        }
        if (request == null || request.getBlockedUserId() == null) {
            return Result.error(ResponseCode.BAD_REQUEST);
        }
        Long ownerId = principal.getUserId();
        TreeHole treeHole = treeHoleService.getOne(
                new LambdaQueryWrapper<TreeHole>().eq(TreeHole::getOwnerId, ownerId));
        if (treeHole == null) {
            throw new BusinessException(ResponseCode.FORBIDDEN);
        }
        treeHoleBlacklistService.addBlock(ownerId, request.getBlockedUserId(), request.getReason());
        return Result.success("成功，该用户已被拉黑", null);
    }
}
