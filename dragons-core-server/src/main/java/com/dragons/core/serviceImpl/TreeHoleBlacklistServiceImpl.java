package com.dragons.core.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dragons.core.dao.TreeHoleBlacklistMapper;
import com.dragons.core.dto.ResponseCode;
import com.dragons.core.entity.TreeHoleBlacklist;
import com.dragons.core.exception.BusinessException;
import com.dragons.core.service.ITreeHoleBlacklistService;
import com.dragons.core.service.IUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * <p>
 * 树洞黑名单表 服务实现类
 * </p>
 *
 * @author aice
 * @since 2026-02-03
 */
@Service
public class TreeHoleBlacklistServiceImpl extends ServiceImpl<TreeHoleBlacklistMapper, TreeHoleBlacklist> implements ITreeHoleBlacklistService {

    private static final int WRITE_MAX_RETRIES = 3;

    /** 拉黑生效 */
    private static final byte STATE_ACTIVE = 0;
    /** 拉黑解除/失效 */
    private static final byte STATE_INACTIVE = 1;

    @Autowired
    private IUserService userService;

    @Override
    public void addBlock(Long ownerId, Long blockedUserId, String reason) {
        if (ownerId == null || ownerId <= 0 || blockedUserId == null || blockedUserId <= 0) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        if (ownerId.equals(blockedUserId)) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        if (userService.getById(blockedUserId) == null) {
            throw new BusinessException(ResponseCode.BLOCK_USER_NOT_FOUND);
        }

        TreeHoleBlacklist existing = this.getOne(
                new LambdaQueryWrapper<TreeHoleBlacklist>()
                        .eq(TreeHoleBlacklist::getOwnerId, ownerId)
                        .eq(TreeHoleBlacklist::getBlockedUserId, blockedUserId));

        if (existing != null) {
            if (existing.getState() != null && existing.getState() == STATE_ACTIVE) {
                return;
            }
            existing.setState(STATE_ACTIVE);
            existing.setUpdateTime(LocalDateTime.now());
            if (reason != null) {
                existing.setReason(reason.trim().isEmpty() ? null : reason.trim());
            }
            if (!updateByIdWithRetry(existing)) {
                throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
            }
            return;
        }

        TreeHoleBlacklist one = new TreeHoleBlacklist();
        one.setOwnerId(ownerId);
        one.setBlockedUserId(blockedUserId);
        one.setState(STATE_ACTIVE);
        one.setReason(reason != null && !reason.trim().isEmpty() ? reason.trim() : null);
        one.setUpdateTime(LocalDateTime.now());
        if (!saveWithRetry(one)) {
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    private boolean saveWithRetry(TreeHoleBlacklist entity) {
        for (int i = 0; i < WRITE_MAX_RETRIES; i++) {
            try {
                if (this.save(entity)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private boolean updateByIdWithRetry(TreeHoleBlacklist entity) {
        for (int i = 0; i < WRITE_MAX_RETRIES; i++) {
            try {
                if (this.updateById(entity)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @Override
    public void removeBlock(Long ownerId, Long blockedUserId) {
        if (ownerId == null || ownerId <= 0 || blockedUserId == null || blockedUserId <= 0) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        TreeHoleBlacklist existing = this.getOne(
                new LambdaQueryWrapper<TreeHoleBlacklist>()
                        .eq(TreeHoleBlacklist::getOwnerId, ownerId)
                        .eq(TreeHoleBlacklist::getBlockedUserId, blockedUserId));
        if (existing == null) {
            return;
        }
        if (existing.getState() != null && existing.getState() == STATE_INACTIVE) {
            return;
        }
        existing.setState(STATE_INACTIVE);
        existing.setUpdateTime(LocalDateTime.now());
        if (!updateByIdWithRetry(existing)) {
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public boolean isBlocked(Long ownerId, Long blockedUserId) {
        if (ownerId == null || blockedUserId == null) {
            return false;
        }
        TreeHoleBlacklist one = this.getOne(
                new LambdaQueryWrapper<TreeHoleBlacklist>()
                        .eq(TreeHoleBlacklist::getOwnerId, ownerId)
                        .eq(TreeHoleBlacklist::getBlockedUserId, blockedUserId)
                        .eq(TreeHoleBlacklist::getState, STATE_ACTIVE));
        return one != null;
    }
}
