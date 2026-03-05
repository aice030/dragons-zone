package com.dragons.core.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dragons.core.dao.TreeHoleBlacklistMapper;
import com.dragons.core.dto.ResponseCode;
import com.dragons.core.entity.TreeHoleBlacklist;
import com.dragons.core.exception.BusinessException;
import com.dragons.core.service.ITreeHoleBlacklistService;
import com.dragons.core.service.IUserService;
import com.dragons.core.service.dbwrite.TreeHoleBlacklistDbWriteService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
public class TreeHoleBlacklistServiceImpl extends ServiceImpl<TreeHoleBlacklistMapper, TreeHoleBlacklist> implements ITreeHoleBlacklistService {

    /** 拉黑生效 */
    private static final byte STATE_ACTIVE = 0;
    /** 拉黑解除/失效 */
    private static final byte STATE_INACTIVE = 1;

    @Autowired
    private IUserService userService;
    @Autowired
    private TreeHoleBlacklistDbWriteService treeHoleBlacklistDbWriteService;

    @Override
    public void addBlock(Long ownerId, Long blockedUserId, String reason) {
        if (ownerId == null || ownerId <= 0 || blockedUserId == null || blockedUserId <= 0) {
            log.warn("addBlock invalid params ownerId={} blockedUserId={}", ownerId, blockedUserId);
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        if (ownerId.equals(blockedUserId)) {
            log.warn("addBlock denied ownerId={} blockedUserId={} reason=cannot_block_self", ownerId, blockedUserId);
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        if (userService.getById(blockedUserId) == null) {
            log.warn("addBlock denied ownerId={} blockedUserId={} reason=blocked_user_not_found", ownerId, blockedUserId);
            throw new BusinessException(ResponseCode.BLOCK_USER_NOT_FOUND);
        }

        TreeHoleBlacklist existing = this.getOne(
                new LambdaQueryWrapper<TreeHoleBlacklist>()
                        .eq(TreeHoleBlacklist::getOwnerId, ownerId)
                        .eq(TreeHoleBlacklist::getBlockedUserId, blockedUserId));

        if (existing != null) {
            if (existing.getState() != null && existing.getState() == STATE_ACTIVE) {
                log.info("treehole block already active ownerId={} blockedUserId={}", ownerId, blockedUserId);
                return;
            }
            existing.setState(STATE_ACTIVE);
            existing.setUpdateTime(LocalDateTime.now());
            if (reason != null) {
                existing.setReason(reason.trim().isEmpty() ? null : reason.trim());
            }
            try {
                // 交由独立写库服务执行；重试由 @DbWriteRetry 切面自动完成
                treeHoleBlacklistDbWriteService.updateById(existing);
            } catch (Exception e) {
                log.error("addBlock failed ownerId={} blockedUserId={} reason=db_update_reactivate_failed", ownerId, blockedUserId);
                throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
            }
            log.info("treehole block success ownerId={} blockedUserId={} action=reactivate", ownerId, blockedUserId);
            return;
        }

        TreeHoleBlacklist one = new TreeHoleBlacklist();
        one.setOwnerId(ownerId);
        one.setBlockedUserId(blockedUserId);
        one.setState(STATE_ACTIVE);
        one.setReason(reason != null && !reason.trim().isEmpty() ? reason.trim() : null);
        one.setUpdateTime(LocalDateTime.now());
        try {
            treeHoleBlacklistDbWriteService.insert(one);
        } catch (Exception e) {
            log.error("addBlock failed ownerId={} blockedUserId={} reason=db_insert_failed", ownerId, blockedUserId);
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
        log.info("treehole block success ownerId={} blockedUserId={} action=insert", ownerId, blockedUserId);
    }

    @Override
    public void removeBlock(Long ownerId, Long blockedUserId) {
        if (ownerId == null || ownerId <= 0 || blockedUserId == null || blockedUserId <= 0) {
            log.warn("removeBlock invalid params ownerId={} blockedUserId={}", ownerId, blockedUserId);
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        TreeHoleBlacklist existing = this.getOne(
                new LambdaQueryWrapper<TreeHoleBlacklist>()
                        .eq(TreeHoleBlacklist::getOwnerId, ownerId)
                        .eq(TreeHoleBlacklist::getBlockedUserId, blockedUserId));
        if (existing == null) {
            log.info("removeBlock idempotent ownerId={} blockedUserId={} reason=record_not_found", ownerId, blockedUserId);
            return;
        }
        if (existing.getState() != null && existing.getState() == STATE_INACTIVE) {
            log.info("removeBlock idempotent ownerId={} blockedUserId={} reason=already_inactive", ownerId, blockedUserId);
            return;
        }
        existing.setState(STATE_INACTIVE);
        existing.setUpdateTime(LocalDateTime.now());
        try {
            treeHoleBlacklistDbWriteService.updateById(existing);
        } catch (Exception e) {
            log.error("removeBlock failed ownerId={} blockedUserId={} reason=db_update_failed", ownerId, blockedUserId);
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
        log.info("treehole unblock success ownerId={} blockedUserId={}", ownerId, blockedUserId);
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
