package com.dragons.core.service;

import com.dragons.core.entity.UserLikeRecord;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 用户点赞记录表 服务类
 * </p>
 *
 * @author aice
 * @since 2026-02-20
 */
public interface IUserLikeRecordService extends IService<UserLikeRecord> {

    /**
     * 查询该用户是否已赞该媒体（用于「查询是否已赞」接口在 Redis 未命中时查 DB）
     *
     * @param userId  用户ID
     * @param mediaId 媒体ID
     * @return true 已点赞，false 未点赞
     */
    boolean existsByUserIdAndMediaId(Long userId, Long mediaId);

    /**
     * 按用户与媒体删除点赞记录（取消点赞时落库删除）
     *
     * @param userId  用户ID
     * @param mediaId 媒体ID
     * @return 是否删除了记录
     */
    boolean removeByUserIdAndMediaId(Long userId, Long mediaId);

    /**
     * 查询当前用户是否已赞指定媒体（需登录）。
     * 仅 state=0 的媒体可查；先查 Redis bitmap，未命中则查 DB 并写回缓存。
     *
     * @param mediaId       媒体ID
     * @param currentUserId 当前用户ID（从 JWT 获取，必填）
     * @return true 已点赞，false 未点赞
     */
    boolean getLikeStatus(Long mediaId, Long currentUserId);
}
