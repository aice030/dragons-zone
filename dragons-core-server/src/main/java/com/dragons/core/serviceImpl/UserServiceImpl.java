package com.dragons.core.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dragons.core.dao.UserMapper;
import com.dragons.core.dto.LoginRequest;
import com.dragons.core.dto.RegisterRequest;
import com.dragons.core.dto.ResponseCode;
import com.dragons.core.exception.BusinessException;
import com.dragons.core.entity.User;
import com.dragons.core.service.IUserService;
import com.dragons.core.service.dbwrite.UserDbWriteService;
import com.dragons.core.util.JwtUtil;
import com.dragons.core.util.PasswordUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * <p>
 * 用户表 服务实现类
 * </p>
 *
 * @author aice
 * @since 2026-01-17
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final PasswordUtil passwordUtil;
    private final JwtUtil jwtUtil;
    private final UserDbWriteService userDbWriteService;

    /**
     * 手机号正则表达式（11位数字）
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /**
     * 构造器注入
     * 使用 @Autowired 注解明确标识依赖注入
     * 注意：Spring 4.3+ 如果类只有一个构造函数，可以省略 @Autowired 注解
     */
    @Autowired
    public UserServiceImpl(PasswordUtil passwordUtil, JwtUtil jwtUtil, UserDbWriteService userDbWriteService) {
        this.passwordUtil = passwordUtil;
        this.jwtUtil = jwtUtil;
        this.userDbWriteService = userDbWriteService;
    }

    @Override
    public LoginResult login(LoginRequest request) {
        // 1. 根据登录名查询用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getLoginName, request.getLoginName());
        User user = this.getOne(wrapper);

        // 2. 验证用户是否存在
        if (user == null) {
            log.warn("login failed loginName={} reason=user_not_found", request.getLoginName());
            throw new BusinessException(ResponseCode.LOGIN_FAILED);
        }

        // 3. 验证用户状态
        if (user.getState() == 1) {
            log.warn("login denied userId={} loginName={} reason=user_deleted", user.getId(), request.getLoginName());
            throw new BusinessException(ResponseCode.USER_DELETED);
        }
        if (user.getState() == 2) {
            log.warn("login denied userId={} loginName={} reason=user_blacklisted", user.getId(), request.getLoginName());
            throw new BusinessException(ResponseCode.USER_BLACKLISTED);
        }

        // 4. 验证密码
        if (!passwordUtil.matches(request.getPassword(), user.getPassword())) {
            log.warn("login failed userId={} loginName={} reason=password_mismatch", user.getId(), request.getLoginName());
            throw new BusinessException(ResponseCode.LOGIN_FAILED);
        }

        // 5. 生成JWT Token
        String token = jwtUtil.generateToken(user.getId(), user.getLoginName());

        // 6. 构建用户信息
        IUserService.UserInfo userInfo = new IUserService.UserInfo(
                user.getId(),
                user.getLoginName(),
                user.getNickName(),
                user.getLevel()
        );
        log.info("login success userId={} loginName={}", user.getId(), user.getLoginName());
        return new LoginResult(token, userInfo);
    }

    @Override
    public RegisterResult register(RegisterRequest request) {
        // 业务路径（注册）：
        // 1) 参数与唯一性校验 -> 2) 组装用户默认值 -> 3) 写库 -> 4) 返回最小必要信息
        // 1. 验证手机号格式
        if (!PHONE_PATTERN.matcher(request.getPhoneNumber()).matches()) {
            log.warn("register failed loginName={} reason=phone_format_invalid", request.getLoginName());
            throw new BusinessException(ResponseCode.PHONE_FORMAT_INVALID);
        }

        // 2. 检查用户名是否已存在
        LambdaQueryWrapper<User> loginNameWrapper = new LambdaQueryWrapper<>();
        loginNameWrapper.eq(User::getLoginName, request.getLoginName());
        if (this.count(loginNameWrapper) > 0) {
            log.warn("register failed loginName={} reason=username_exists", request.getLoginName());
            throw new BusinessException(ResponseCode.USERNAME_EXISTS);
        }

        // 3. 检查手机号是否已存在
        LambdaQueryWrapper<User> phoneWrapper = new LambdaQueryWrapper<>();
        phoneWrapper.eq(User::getPhoneNumber, request.getPhoneNumber());
        if (this.count(phoneWrapper) > 0) {
            log.warn("register failed loginName={} reason=phone_exists", request.getLoginName());
            throw new BusinessException(ResponseCode.PHONE_EXISTS);
        }

        // 4. 加密密码
        String encodedPassword = passwordUtil.encode(request.getPassword());

        // 5. 创建用户对象
        User user = new User();
        user.setLoginName(request.getLoginName());
        user.setPassword(encodedPassword);
        user.setNickName(request.getNickName());
        user.setPhoneNumber(request.getPhoneNumber());
        // 默认普通用户
        user.setLevel((byte) 2);
        // 默认正常状态
        user.setState((byte) 0);
        user.setUpdateTime(LocalDateTime.now());

        // 6. 保存用户（重试 3 次）
        try {
            // 调用独立写库服务；重试由 @DbWriteRetry 切面自动处理
            userDbWriteService.insert(user);
        } catch (Exception e) {
            log.error("register failed loginName={} reason=db_insert_failed", request.getLoginName());
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
        log.info("register success userId={} loginName={}", user.getId(), user.getLoginName());
        return new RegisterResult(user.getId(), user.getLoginName());
    }

    @Override
    public void deregister(Long userId, String password) {
        // 业务路径（注销）：
        // 1) 校验账号与密码 -> 2) 逻辑删除字段更新 -> 3) 写库持久化
        // 1. 查询用户
        User user = this.getById(userId);
        if (user == null) {
            log.warn("deregister denied userId={} reason=user_not_found", userId);
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // 2. 检查用户状态（如果已被注销，不允许重复注销）
        if (user.getState() == 1) {
            log.warn("deregister denied userId={} reason=already_deleted", userId);
            throw new BusinessException(ResponseCode.USER_DELETED);
        }

        // 3. 验证密码（二次确认）
        if (!passwordUtil.matches(password, user.getPassword())) {
            log.warn("deregister denied userId={} reason=password_mismatch", userId);
            throw new BusinessException(ResponseCode.LOGIN_FAILED);
        }

        // 4. 逻辑删除：设置 state=1，清除 nickName，手机号改为占位符以释放唯一约束（占位符按 id 唯一，避免多行 null 在某些库下冲突）
        user.setState((byte) 1);
        user.setNickName("用户" + user.getId() + "已注销");
        user.setPhoneNumber("deleted_" + user.getId());
        user.setUpdateTime(LocalDateTime.now());
        try {
            // 逻辑注销只做 DB 更新；失败由切面重试后再进入异常分支
            userDbWriteService.updateById(user);
        } catch (Exception e) {
            log.error("deregister failed userId={} reason=db_update_failed", userId);
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
        log.info("deregister success userId={}", userId);
    }

    @Override
    public void resetPasswordByPhone(Long currentUserId, String phoneNumber, String newPassword) {
        // 业务路径（登录态改密）：
        // 1) 校验登录态和新密码 -> 2) 校验手机号归属 -> 3) 更新密码
        if (currentUserId == null) {
            log.warn("resetPasswordByPhone denied reason=currentUserId_null");
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }

        // 1) 只要注册成功，默认手机号合规，此处不再校验，以兼容手动入库的测试用户
        // if (phoneNumber == null || !PHONE_PATTERN.matcher(phoneNumber).matches()) {
        //     throw new BusinessException(ResponseCode.PHONE_FORMAT_INVALID);
        // }
        // 2) 校验新密码（MVP：基础校验即可）
        if (newPassword == null || newPassword.trim().isEmpty()) {
            log.warn("resetPasswordByPhone invalid params userId={} reason=password_empty", currentUserId);
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        if (newPassword.length() < 6 || newPassword.length() > 64) {
            log.warn("resetPasswordByPhone invalid params userId={} reason=password_length_invalid", currentUserId);
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        // 3) 查询当前登录用户
        User user = this.getById(currentUserId);
        if (user == null) {
            log.warn("resetPasswordByPhone denied userId={} reason=user_not_found", currentUserId);
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // 4) 校验用户状态
        if (user.getState() == 1) {
            log.warn("resetPasswordByPhone denied userId={} reason=user_deleted", currentUserId);
            throw new BusinessException(ResponseCode.USER_DELETED);
        }
        if (user.getState() == 2) {
            log.warn("resetPasswordByPhone denied userId={} reason=user_blacklisted", currentUserId);
            throw new BusinessException(ResponseCode.USER_BLACKLISTED);
        }

        // 5) 校验手机号必须匹配当前用户（防止用别人的token改别人的密码）
        String inputPhone = (phoneNumber == null) ? null : phoneNumber.trim();
        String dbPhone = user.getPhoneNumber();
        if (inputPhone == null || inputPhone.isEmpty() || dbPhone == null || !dbPhone.equals(inputPhone)) {
            log.warn("resetPasswordByPhone denied userId={} reason=phone_mismatch", currentUserId);
            throw new BusinessException(ResponseCode.FORBIDDEN);
        }

        // 6) 加密新密码并保存（重试 3 次）
        String encodedPassword = passwordUtil.encode(newPassword);
        user.setPassword(encodedPassword);
        user.setUpdateTime(LocalDateTime.now());
        try {
            userDbWriteService.updateById(user);
        } catch (Exception e) {
            log.error("resetPasswordByPhone failed userId={} reason=db_update_failed", currentUserId);
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
        log.info("resetPasswordByPhone success userId={}", currentUserId);
    }

    @Override
    public void forgotPassword(String loginName, String phoneNumber, String newPassword) {
        // 业务路径（未登录找回密码）：
        // 1) 参数校验 -> 2) 登录名+手机号联合校验身份 -> 3) 更新密码
        // 1) 参数校验
        if (!StringUtils.hasText(loginName) || !StringUtils.hasText(phoneNumber)) {
            log.warn("forgotPassword invalid params loginName={} reason=empty_params", loginName);
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        // 只要注册成功，默认手机号合规，此处不再校验，以兼容手动入库的测试用户
        // if (phoneNumber != null && !PHONE_PATTERN.matcher(phoneNumber.trim()).matches()) {
        //     throw new BusinessException(ResponseCode.PHONE_FORMAT_INVALID);
        // }
        if (newPassword == null || newPassword.trim().isEmpty()) {
            log.warn("forgotPassword invalid params loginName={} reason=password_empty", loginName);
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        if (newPassword.length() < 6 || newPassword.length() > 64) {
            log.warn("forgotPassword invalid params loginName={} reason=password_length_invalid", loginName);
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        // 2) 按登录名查用户（不存在或手机号不匹配时统一返回，避免泄露用户是否存在）
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getLoginName, loginName.trim());
        User user = this.getOne(wrapper);
        if (user == null) {
            log.warn("forgotPassword denied loginName={} reason=login_name_phone_mismatch", loginName);
            throw new BusinessException(ResponseCode.LOGIN_NAME_PHONE_MISMATCH);
        }
        String inputPhone = phoneNumber.trim();
        String dbPhone = user.getPhoneNumber();
        if (dbPhone == null || !dbPhone.equals(inputPhone)) {
            log.warn("forgotPassword denied loginName={} userId={} reason=login_name_phone_mismatch", loginName, user.getId());
            throw new BusinessException(ResponseCode.LOGIN_NAME_PHONE_MISMATCH);
        }

        // 3) 校验用户状态
        if (user.getState() != null && user.getState() == 1) {
            log.warn("forgotPassword denied loginName={} userId={} reason=user_deleted", loginName, user.getId());
            throw new BusinessException(ResponseCode.USER_DELETED);
        }
        if (user.getState() != null && user.getState() == 2) {
            log.warn("forgotPassword denied loginName={} userId={} reason=user_blacklisted", loginName, user.getId());
            throw new BusinessException(ResponseCode.USER_BLACKLISTED);
        }

        // 4) 加密新密码并保存（重试 3 次）
        String encodedPassword = passwordUtil.encode(newPassword);
        user.setPassword(encodedPassword);
        user.setUpdateTime(LocalDateTime.now());
        try {
            userDbWriteService.updateById(user);
        } catch (Exception e) {
            log.error("forgotPassword failed loginName={} userId={} reason=db_update_failed", loginName, user.getId());
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
        log.info("forgotPassword success loginName={}", loginName.trim());
    }

    /**
     * 验证当前用户是否为作者或管理员
     */
    private void validateAuthorOrAdmin(Long userId) {
        if (userId == null) {
            log.warn("validateAuthorOrAdmin denied userId=null");
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }
        User user = this.getById(userId);
        if (user == null || user.getState() == null || user.getState() != 0) {
            log.warn("validateAuthorOrAdmin denied userId={} reason=user_not_found_or_invalid_state", userId);
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }
        Byte level = user.getLevel();
        if (level == null || (level != 0 && level != 1)) {
            log.warn("validateAuthorOrAdmin denied userId={} level={} reason=not_author_nor_admin", userId, level);
            throw new BusinessException(ResponseCode.FORBIDDEN);
        }
    }

    @Override
    public void updateUserLevel(Long currentUserId, Long targetUserId, Byte newLevel) {
        if (currentUserId == null || targetUserId == null) {
            log.warn("updateUserLevel invalid params currentUserId={} targetUserId={}", currentUserId, targetUserId);
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        if (newLevel == null || (newLevel != 0 && newLevel != 1 && newLevel != 2 && newLevel != 3)) {
            log.warn("updateUserLevel invalid level currentUserId={} targetUserId={} newLevel={}", currentUserId, targetUserId, newLevel);
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        // 验证当前用户权限（必须是作者或管理员）
        validateAuthorOrAdmin(currentUserId);

        // 查询目标用户
        User targetUser = this.getById(targetUserId);
        if (targetUser == null) {
            log.warn("updateUserLevel denied currentUserId={} targetUserId={} reason=target_user_not_found", currentUserId, targetUserId);
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // 更新用户等级
        targetUser.setLevel(newLevel);
        targetUser.setUpdateTime(LocalDateTime.now());
        try {
            userDbWriteService.updateById(targetUser);
        } catch (Exception e) {
            log.error("updateUserLevel failed currentUserId={} targetUserId={} newLevel={} reason=db_update_failed", currentUserId, targetUserId, newLevel);
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
        log.info("updateUserLevel success currentUserId={} targetUserId={} newLevel={}", currentUserId, targetUserId, newLevel);
    }

    @Override
    public void updateUserState(Long currentUserId, Long targetUserId, Byte newState) {
        if (currentUserId == null || targetUserId == null) {
            log.warn("updateUserState invalid params currentUserId={} targetUserId={}", currentUserId, targetUserId);
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        if (newState == null || (newState != 0 && newState != 1 && newState != 2)) {
            log.warn("updateUserState invalid state currentUserId={} targetUserId={} newState={}", currentUserId, targetUserId, newState);
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        // 验证当前用户权限（必须是作者或管理员）
        validateAuthorOrAdmin(currentUserId);

        // 查询目标用户
        User targetUser = this.getById(targetUserId);
        if (targetUser == null) {
            log.warn("updateUserState denied currentUserId={} targetUserId={} reason=target_user_not_found", currentUserId, targetUserId);
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // 更新用户状态
        targetUser.setState(newState);
        targetUser.setUpdateTime(LocalDateTime.now());
        try {
            userDbWriteService.updateById(targetUser);
        } catch (Exception e) {
            log.error("updateUserState failed currentUserId={} targetUserId={} newState={} reason=db_update_failed", currentUserId, targetUserId, newState);
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
        log.info("updateUserState success currentUserId={} targetUserId={} newState={}", currentUserId, targetUserId, newState);
    }

    /**
     * 验证当前用户是否为作者（仅作者可操作）
     */
    private void validateAuthor(Long userId) {
        if (userId == null) {
            log.warn("validateAuthor denied userId=null");
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }
        User user = this.getById(userId);
        if (user == null || user.getState() == null || user.getState() != 0) {
            log.warn("validateAuthor denied userId={} reason=user_not_found_or_invalid_state", userId);
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }
        Byte level = user.getLevel();
        if (level == null || level != 0) {
            log.warn("validateAuthor denied userId={} level={} reason=not_author", userId, level);
            throw new BusinessException(ResponseCode.FORBIDDEN);
        }
    }

    @Override
    public IUserService.UserListResult getUserList(Long currentUserId, Integer page, Integer size) {
        if (currentUserId == null) {
            log.warn("getUserList denied reason=currentUserId_null");
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 20;
        }
        if (size > 100) {
            size = 100;
        }

        // 验证当前用户权限（必须是作者）
        validateAuthor(currentUserId);

        // 分页查询用户列表（排除 level=0 的作者）
        Page<User> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(User::getId, User::getNickName, User::getLevel, User::getState);
        wrapper.ne(User::getLevel, 0); // 排除 level=0 的作者
        wrapper.orderByAsc(User::getId);
        IPage<User> pageResult = this.page(pageParam, wrapper);

        // 转换为结果列表
        List<IUserService.UserListItem> list = new ArrayList<>();
        for (User user : pageResult.getRecords()) {
            list.add(new IUserService.UserListItem(
                    user.getId(),
                    user.getNickName(),
                    user.getLevel(),
                    user.getState()
            ));
        }

        log.info("getUserList currentUserId={} page={} size={} total={}", currentUserId, page, size, pageResult.getTotal());
        return new IUserService.UserListResult(pageResult.getTotal(), list);
    }

    @Override
    public String getNickNameById(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = this.getById(userId);
        return user != null ? user.getNickName() : null;
    }
}
