package com.dragons.core.service;

import com.dragons.core.entity.TreeHoleBlacklist;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 树洞黑名单表 服务类
 * </p>
 *
 * @author aice
 * @since 2026-02-03
 */
public interface ITreeHoleBlacklistService extends IService<TreeHoleBlacklist> {

    /**
     * 拉黑用户：保证 (owner_id, blocked_user_id) 唯一；已存在且 state=生效则静默成功，已存在且 state=解除则改回生效，不存在则新增。
     * 写操作重试 3 次。无返回值，失败时抛 BusinessException。
     *
     * @param ownerId      树洞主人 ID（当前登录用户）
     * @param blockedUserId 被拉黑的用户 ID
     * @param reason       拉黑原因，可选
     */
    void addBlock(Long ownerId, Long blockedUserId, String reason);

    /**
     * 解除拉黑：将 (owner_id, blocked_user_id) 对应记录的 state 置为解除/失效。
     * 记录不存在或已是 state=解除 则静默成功（幂等）。写操作重试 3 次，失败时抛 BusinessException。
     *
     * @param ownerId      树洞主人 ID（当前登录用户）
     * @param blockedUserId 被解除拉黑的用户 ID
     */
    void removeBlock(Long ownerId, Long blockedUserId);

    /**
     * 判断树洞主人是否已拉黑某用户（仅 state=生效 的记录算拉黑中）
     *
     * @param ownerId      树洞主人用户 ID
     * @param blockedUserId 被拉黑用户 ID
     * @return true 表示已拉黑
     */
    boolean isBlocked(Long ownerId, Long blockedUserId);
}
