package com.dragons.core.controller;

import com.dragons.core.dto.DeregisterRequest;
import com.dragons.core.dto.ForgotPasswordRequest;
import com.dragons.core.dto.LoginRequest;
import com.dragons.core.dto.RegisterRequest;
import com.dragons.core.dto.ResetPasswordByPhoneRequest;
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
}
