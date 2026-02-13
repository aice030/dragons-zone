package com.dragons.core.controller;

import com.dragons.core.dto.DeregisterRequest;
import com.dragons.core.dto.ForgotPasswordRequest;
import com.dragons.core.dto.LoginRequest;
import com.dragons.core.dto.RegisterRequest;
import com.dragons.core.dto.ResetPasswordByPhoneRequest;
import com.dragons.core.dto.UpdateUserLevelRequest;
import com.dragons.core.dto.UpdateUserStateRequest;
import com.dragons.core.dto.Result;
import com.dragons.core.dto.ResponseCode;
import com.dragons.core.security.JwtPrincipal;
import com.dragons.core.service.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 用户表 前端控制器
 * </p>
 *
 * @author aice
 * @since 2026-01-17
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final IUserService userService;

    /**
     * 构造器注入
     * 使用 @Autowired 注解明确标识依赖注入
     * 注意：Spring 4.3+ 如果类只有一个构造函数，可以省略 @Autowired 注解
     */
    @Autowired
    public UserController(IUserService userService) {
        this.userService = userService;
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<IUserService.LoginResult> login(@RequestBody LoginRequest request) {
        IUserService.LoginResult result = userService.login(request);
        return Result.success("登录成功", result);
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<IUserService.RegisterResult> register(@RequestBody RegisterRequest request) {
        IUserService.RegisterResult result = userService.register(request);
        return Result.success("注册成功", result);
    }

    /**
     * 用户注销
     */
    @PostMapping("/deregister")
    public Result<Void> deregister(@AuthenticationPrincipal JwtPrincipal principal,
                                   @RequestBody DeregisterRequest request) {
        if (principal == null) {
            return Result.error(ResponseCode.UNAUTHORIZED);
        }

        userService.deregister(principal.getUserId(), request.getPassword());
        return Result.success("注销成功");
    }

    /**
     * 通过手机号重置密码（忘记密码简化方案）
     *
     * 说明：
     * - 必须登录（从JWT拿到当前用户ID）
     * - 仅允许修改“当前登录用户”自己的密码（手机号用于校验）
     */
    @PostMapping("/resetPasswordByPhone")
    public Result<Void> resetPasswordByPhone(@AuthenticationPrincipal JwtPrincipal principal,
                                             @RequestBody ResetPasswordByPhoneRequest request) {
        if (request == null) {
            return Result.error(ResponseCode.BAD_REQUEST);
        }
        if (principal == null) {
            return Result.error(ResponseCode.UNAUTHORIZED);
        }

        userService.resetPasswordByPhone(principal.getUserId(), request.getPhoneNumber(), request.getNewPassword());
        return Result.success("修改成功");
    }

    /**
     * 未登录找回密码（忘记密码）
     *
     * 说明：无需登录，通过登录名+手机号校验身份后修改密码。不依赖短信验证码（短信需申请签名/模板等）。
     * 路径已配置为 permitAll，无需 JWT。
     */
    @PostMapping("/forgotPassword")
    public Result<Void> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        if (request == null) {
            return Result.error(ResponseCode.BAD_REQUEST);
        }
        userService.forgotPassword(request.getLoginName(), request.getPhoneNumber(), request.getNewPassword());
        return Result.success("密码已重置，请使用新密码登录");
    }

    /**
     * 修改用户等级（仅作者/管理员可操作）
     *
     * @param principal 当前登录用户（从JWT获取）
     * @param targetUserId 目标用户ID
     * @param request 请求体（包含新等级）
     */
    @PutMapping("/{targetUserId}/level")
    public Result<Void> updateUserLevel(@AuthenticationPrincipal JwtPrincipal principal,
                                        @PathVariable("targetUserId") Long targetUserId,
                                        @RequestBody UpdateUserLevelRequest request) {
        if (principal == null) {
            return Result.error(ResponseCode.UNAUTHORIZED);
        }
        if (request == null || request.getLevel() == null) {
            return Result.error(ResponseCode.BAD_REQUEST);
        }

        userService.updateUserLevel(principal.getUserId(), targetUserId, request.getLevel());
        return Result.success("用户等级修改成功");
    }

    /**
     * 修改用户状态（仅作者/管理员可操作）
     *
     * @param principal 当前登录用户（从JWT获取）
     * @param targetUserId 目标用户ID
     * @param request 请求体（包含新状态）
     */
    @PutMapping("/{targetUserId}/state")
    public Result<Void> updateUserState(@AuthenticationPrincipal JwtPrincipal principal,
                                       @PathVariable("targetUserId") Long targetUserId,
                                       @RequestBody UpdateUserStateRequest request) {
        if (principal == null) {
            return Result.error(ResponseCode.UNAUTHORIZED);
        }
        if (request == null || request.getState() == null) {
            return Result.error(ResponseCode.BAD_REQUEST);
        }

        userService.updateUserState(principal.getUserId(), targetUserId, request.getState());
        return Result.success("用户状态修改成功");
    }

    /**
     * 获取用户列表（分页，仅作者可操作）
     *
     * @param principal 当前登录用户（从JWT获取）
     * @param page 页码（默认1）
     * @param size 每页数量（默认20，最大100）
     */
    @GetMapping("/list")
    public Result<IUserService.UserListResult> getUserList(@AuthenticationPrincipal JwtPrincipal principal,
                                                           @RequestParam(value = "page", defaultValue = "1") Integer page,
                                                           @RequestParam(value = "size", defaultValue = "20") Integer size) {
        if (principal == null) {
            return Result.error(ResponseCode.UNAUTHORIZED);
        }

        IUserService.UserListResult result = userService.getUserList(principal.getUserId(), page, size);
        return Result.success("查询成功", result);
    }

    /**
     * 根据用户ID获取昵称
     * GET /api/user/{userId}/nickname
     *
     * @param userId 用户ID
     * @return 用户昵称
     */
    @GetMapping("/{userId}/nickname")
    public Result<String> getNickNameById(@PathVariable("userId") Long userId) {
        if (userId == null) {
            return Result.error(ResponseCode.BAD_REQUEST);
        }
        String nickName = userService.getNickNameById(userId);
        if (nickName == null) {
            return Result.error(ResponseCode.NOT_FOUND);
        }
        return Result.success("查询成功", nickName);
    }
}
