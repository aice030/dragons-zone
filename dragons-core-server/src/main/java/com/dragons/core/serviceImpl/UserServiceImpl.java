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
import com.dragons.core.util.JwtUtil;
import com.dragons.core.util.PasswordUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private static final int WRITE_MAX_RETRIES = 3;

    private final PasswordUtil passwordUtil;
    private final JwtUtil jwtUtil;

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
    public UserServiceImpl(PasswordUtil passwordUtil, JwtUtil jwtUtil) {
        this.passwordUtil = passwordUtil;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public LoginResult login(LoginRequest request) {
        // 1. 根据登录名查询用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getLoginName, request.getLoginName());
        User user = this.getOne(wrapper);

        // 2. 验证用户是否存在
        if (user == null) {
            throw new BusinessException(ResponseCode.LOGIN_FAILED);
        }

        // 3. 验证用户状态
        if (user.getState() == 1) {
            throw new BusinessException(ResponseCode.USER_DELETED);
        }
        if (user.getState() == 2) {
            throw new BusinessException(ResponseCode.USER_BLACKLISTED);
        }

        // 4. 验证密码
        if (!passwordUtil.matches(request.getPassword(), user.getPassword())) {
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

        return new LoginResult(token, userInfo);
    }

    @Override
    public RegisterResult register(RegisterRequest request) {
        // 1. 验证手机号格式
        if (!PHONE_PATTERN.matcher(request.getPhoneNumber()).matches()) {
            throw new BusinessException(ResponseCode.PHONE_FORMAT_INVALID);
        }

        // 2. 检查用户名是否已存在
        LambdaQueryWrapper<User> loginNameWrapper = new LambdaQueryWrapper<>();
        loginNameWrapper.eq(User::getLoginName, request.getLoginName());
        if (this.count(loginNameWrapper) > 0) {
            throw new BusinessException(ResponseCode.USERNAME_EXISTS);
        }

        // 3. 检查手机号是否已存在
        LambdaQueryWrapper<User> phoneWrapper = new LambdaQueryWrapper<>();
        phoneWrapper.eq(User::getPhoneNumber, request.getPhoneNumber());
        if (this.count(phoneWrapper) > 0) {
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
        if (!saveWithRetry(user)) {
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }

        return new RegisterResult(user.getId(), user.getLoginName());
    }

    @Override
    public void deregister(Long userId, String password) {
        // 1. 查询用户
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // 2. 检查用户状态（如果已被注销，不允许重复注销）
        if (user.getState() == 1) {
            throw new BusinessException(ResponseCode.USER_DELETED);
        }

        // 3. 验证密码（二次确认）
        if (!passwordUtil.matches(password, user.getPassword())) {
            throw new BusinessException(ResponseCode.LOGIN_FAILED);
        }

        // 4. 逻辑删除：设置 state=1，清除 nickName，手机号改为占位符以释放唯一约束（占位符按 id 唯一，避免多行 null 在某些库下冲突）
        user.setState((byte) 1);
        user.setNickName("用户" + user.getId() + "已注销");
        user.setPhoneNumber("deleted_" + user.getId());
        user.setUpdateTime(LocalDateTime.now());
        if (!updateByIdWithRetry(user)) {
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void resetPasswordByPhone(Long currentUserId, String phoneNumber, String newPassword) {
        if (currentUserId == null) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }

        // 1) 只要注册成功，默认手机号合规，此处不再校验，以兼容手动入库的测试用户
        // if (phoneNumber == null || !PHONE_PATTERN.matcher(phoneNumber).matches()) {
        //     throw new BusinessException(ResponseCode.PHONE_FORMAT_INVALID);
        // }
        // 2) 校验新密码（MVP：基础校验即可）
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        if (newPassword.length() < 6 || newPassword.length() > 64) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        // 3) 查询当前登录用户
        User user = this.getById(currentUserId);
        if (user == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // 4) 校验用户状态
        if (user.getState() == 1) {
            throw new BusinessException(ResponseCode.USER_DELETED);
        }
        if (user.getState() == 2) {
            throw new BusinessException(ResponseCode.USER_BLACKLISTED);
        }

        // 5) 校验手机号必须匹配当前用户（防止用别人的token改别人的密码）
        String inputPhone = (phoneNumber == null) ? null : phoneNumber.trim();
        String dbPhone = user.getPhoneNumber();
        if (inputPhone == null || inputPhone.isEmpty() || dbPhone == null || !dbPhone.equals(inputPhone)) {
            throw new BusinessException(ResponseCode.FORBIDDEN);
        }

        // 6) 加密新密码并保存（重试 3 次）
        String encodedPassword = passwordUtil.encode(newPassword);
        user.setPassword(encodedPassword);
        user.setUpdateTime(LocalDateTime.now());
        if (!updateByIdWithRetry(user)) {
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void forgotPassword(String loginName, String phoneNumber, String newPassword) {
        // 1) 参数校验
        if (!StringUtils.hasText(loginName) || !StringUtils.hasText(phoneNumber)) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        // 只要注册成功，默认手机号合规，此处不再校验，以兼容手动入库的测试用户
        // if (phoneNumber != null && !PHONE_PATTERN.matcher(phoneNumber.trim()).matches()) {
        //     throw new BusinessException(ResponseCode.PHONE_FORMAT_INVALID);
        // }
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        if (newPassword.length() < 6 || newPassword.length() > 64) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        // 2) 按登录名查用户（不存在或手机号不匹配时统一返回，避免泄露用户是否存在）
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getLoginName, loginName.trim());
        User user = this.getOne(wrapper);
        if (user == null) {
            throw new BusinessException(ResponseCode.LOGIN_NAME_PHONE_MISMATCH);
        }
        String inputPhone = phoneNumber.trim();
        String dbPhone = user.getPhoneNumber();
        if (dbPhone == null || !dbPhone.equals(inputPhone)) {
            throw new BusinessException(ResponseCode.LOGIN_NAME_PHONE_MISMATCH);
        }

        // 3) 校验用户状态
        if (user.getState() != null && user.getState() == 1) {
            throw new BusinessException(ResponseCode.USER_DELETED);
        }
        if (user.getState() != null && user.getState() == 2) {
            throw new BusinessException(ResponseCode.USER_BLACKLISTED);
        }

        // 4) 加密新密码并保存（重试 3 次）
        String encodedPassword = passwordUtil.encode(newPassword);
        user.setPassword(encodedPassword);
        user.setUpdateTime(LocalDateTime.now());
        if (!updateByIdWithRetry(user)) {
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /** 写操作重试：最多 3 次，防止临时网络/锁冲突导致失败 */
    private boolean saveWithRetry(User user) {
        for (int i = 0; i < WRITE_MAX_RETRIES; i++) {
            try {
                if (this.save(user)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private boolean updateByIdWithRetry(User user) {
        for (int i = 0; i < WRITE_MAX_RETRIES; i++) {
            try {
                if (this.updateById(user)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    /**
     * 验证当前用户是否为作者或管理员
     */
    private void validateAuthorOrAdmin(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }
        User user = this.getById(userId);
        if (user == null || user.getState() == null || user.getState() != 0) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }
        Byte level = user.getLevel();
        if (level == null || (level != 0 && level != 1)) {
            throw new BusinessException(ResponseCode.FORBIDDEN);
        }
    }

    @Override
    public void updateUserLevel(Long currentUserId, Long targetUserId, Byte newLevel) {
        if (currentUserId == null || targetUserId == null) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        if (newLevel == null || (newLevel != 0 && newLevel != 1 && newLevel != 2 && newLevel != 3)) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        // 验证当前用户权限（必须是作者或管理员）
        validateAuthorOrAdmin(currentUserId);

        // 查询目标用户
        User targetUser = this.getById(targetUserId);
        if (targetUser == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // 更新用户等级
        targetUser.setLevel(newLevel);
        targetUser.setUpdateTime(LocalDateTime.now());
        if (!updateByIdWithRetry(targetUser)) {
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void updateUserState(Long currentUserId, Long targetUserId, Byte newState) {
        if (currentUserId == null || targetUserId == null) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        if (newState == null || (newState != 0 && newState != 1 && newState != 2)) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        // 验证当前用户权限（必须是作者或管理员）
        validateAuthorOrAdmin(currentUserId);

        // 查询目标用户
        User targetUser = this.getById(targetUserId);
        if (targetUser == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // 更新用户状态
        targetUser.setState(newState);
        targetUser.setUpdateTime(LocalDateTime.now());
        if (!updateByIdWithRetry(targetUser)) {
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 验证当前用户是否为作者（仅作者可操作）
     */
    private void validateAuthor(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }
        User user = this.getById(userId);
        if (user == null || user.getState() == null || user.getState() != 0) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }
        Byte level = user.getLevel();
        if (level == null || level != 0) {
            throw new BusinessException(ResponseCode.FORBIDDEN);
        }
    }

    @Override
    public IUserService.UserListResult getUserList(Long currentUserId, Integer page, Integer size) {
        if (currentUserId == null) {
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

        return new IUserService.UserListResult(pageResult.getTotal(), list);
    }

}
