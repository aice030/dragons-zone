package com.dragons.core.service;

import com.dragons.core.dto.LoginRequest;
import com.dragons.core.dto.RegisterRequest;
import com.dragons.core.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 用户表 服务类
 * </p>
 *
 * @author aice
 * @since 2026-01-17
 */
public interface IUserService extends IService<User> {

    /**
     * 用户登录
     * 
     * @param request 登录请求
     * @return 用户信息和Token
     */
    LoginResult login(LoginRequest request);

    /**
     * 用户注册
     * 
     * @param request 注册请求
     * @return 注册结果（userId和loginName）
     */
    RegisterResult register(RegisterRequest request);

    /**
     * 用户注销（逻辑删除）
     * 
     * @param userId 用户ID
     * @param password 密码（二次确认）
     */
    void deregister(Long userId, String password);

    /**
     * 通过手机号修改密码（仅允许修改自己的密码）
     *
     * 说明：
     * - 必须登录（从JWT拿到currentUserId）
     * - phoneNumber 用于校验“你确实知道自己绑定的手机号”，防止误操作
     *
     * @param currentUserId 当前登录用户ID（从JWT获取）
     * @param phoneNumber 手机号（可为测试数据，如 1/2/3）
     * @param newPassword 新密码（明文）
     */
    void resetPasswordByPhone(Long currentUserId, String phoneNumber, String newPassword);

    /**
     * 未登录找回密码（忘记密码）
     *
     * 说明：不依赖验证码，仅通过登录名+手机号校验身份后修改密码。登录名或手机号不匹配时统一返回相同错误，避免泄露用户是否存在。
     *
     * @param loginName 登录名
     * @param phoneNumber 注册时绑定的手机号
     * @param newPassword 新密码（明文）
     */
    void forgotPassword(String loginName, String phoneNumber, String newPassword);

    /**
     * 登录结果内部类
     */
    class LoginResult {
        public String token;
        public UserInfo userInfo;

        public LoginResult(String token, UserInfo userInfo) {
            this.token = token;
            this.userInfo = userInfo;
        }
    }

    /**
     * 注册结果内部类
     */
    class RegisterResult {
        public Long userId;
        public String loginName;

        public RegisterResult(Long userId, String loginName) {
            this.userId = userId;
            this.loginName = loginName;
        }
    }

    /**
     * 用户信息内部类
     */
    class UserInfo {
        public Long id;
        public String loginName;
        public String nickName;
        public Byte level;

        public UserInfo(Long id, String loginName, String nickName, Byte level) {
            this.id = id;
            this.loginName = loginName;
            this.nickName = nickName;
            this.level = level;
        }
    }
}
