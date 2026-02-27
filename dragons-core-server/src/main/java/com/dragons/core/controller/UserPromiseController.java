package com.dragons.core.controller;

import com.dragons.core.dto.ResponseCode;
import com.dragons.core.dto.Result;
import com.dragons.core.security.JwtPrincipal;
import com.dragons.core.service.IUserPromiseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 用户上传前承诺记录 前端控制器
 * </p>
 *
 * 说明：
 * - 接口路径：POST /api/user/{currentUserId}/promise
 * - 必须登录，从 JWT 中获取当前用户 ID
 * - 为避免越权，要求路径中的 currentUserId 必须等于登录用户 ID
 * - 在 user_promise 表中插入一条记录（user_id 为当前用户）
 *
 * @author aice
 * @since 2026-02-27
 */
@RestController
@RequestMapping("/api/user")
public class UserPromiseController {

    private final IUserPromiseService userPromiseService;

    @Autowired
    public UserPromiseController(IUserPromiseService userPromiseService) {
        this.userPromiseService = userPromiseService;
    }

    /**
     * 记录用户上传前承诺
     *
     * POST /api/user/{currentUserId}/promise
     *
     * 仅允许当前登录用户为自己记录承诺：
     * - 如果未登录：返回 401 UNAUTHORIZED
     * - 如果路径中的 currentUserId 与登录用户 ID 不一致：返回 403 FORBIDDEN
     */
    @PostMapping("/{currentUserId}/promise")
    public Result<Void> createUserPromise(@AuthenticationPrincipal JwtPrincipal principal,
                                          @PathVariable("currentUserId") Long currentUserId) {
        if (principal == null) {
            return Result.error(ResponseCode.UNAUTHORIZED);
        }
        if (currentUserId == null || !currentUserId.equals(principal.getUserId())) {
            return Result.error(ResponseCode.FORBIDDEN);
        }

        userPromiseService.recordUserPromise(principal.getUserId());
        return Result.success("承诺记录成功");
    }
}
