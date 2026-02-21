package com.dragons.core.serviceImpl;

import com.dragons.core.dao.MediaMapper;
import com.dragons.core.entity.Media;
import com.dragons.core.entity.UserLikeRecord;
import com.dragons.core.dao.UserLikeRecordMapper;
import com.dragons.core.exception.BusinessException;
import com.dragons.core.dto.ResponseCode;
import com.dragons.core.cache.RedisCacheMediaLikeService;
import com.dragons.core.service.IUserLikeRecordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * <p>
 * 用户点赞记录表 服务实现类
 * </p>
 *
 * @author aice
 * @since 2026-02-20
 */
@Slf4j
@Service
public class UserLikeRecordServiceImpl extends ServiceImpl<UserLikeRecordMapper, UserLikeRecord> implements IUserLikeRecordService {

    private final MediaMapper mediaMapper;
    private final RedisCacheMediaLikeService redisCacheMediaLikeService;

    @Autowired
    public UserLikeRecordServiceImpl(MediaMapper mediaMapper, RedisCacheMediaLikeService redisCacheMediaLikeService) {
        this.mediaMapper = mediaMapper;
        this.redisCacheMediaLikeService = redisCacheMediaLikeService;
    }

    @Override
    public boolean existsByUserIdAndMediaId(Long userId, Long mediaId) {
        if (userId == null || mediaId == null) {
            return false;
        }
        return count(new LambdaQueryWrapper<UserLikeRecord>()
                .eq(UserLikeRecord::getUserId, userId)
                .eq(UserLikeRecord::getMediaId, mediaId)) > 0;
    }

    @Override
    public boolean removeByUserIdAndMediaId(Long userId, Long mediaId) {
        if (userId == null || mediaId == null) {
            return false;
        }
        return remove(new LambdaQueryWrapper<UserLikeRecord>()
                .eq(UserLikeRecord::getUserId, userId)
                .eq(UserLikeRecord::getMediaId, mediaId));
    }

    /**
     * 查询当前用户是否已赞指定媒体。仅 state=0 的媒体可查。
     * <p>
     * 只读 Redis bitmap（{@code media:liked:{mediaId}}，offset=userId）。点赞/取消点赞已先写 Redis 再 MQ 同步 DB，
     * 以 Redis 为准；缓存未命中（key 不存在或该位为 0）时视为未赞，不查 DB。
     */
    @Override
    public boolean getLikeStatus(Long mediaId, Long currentUserId) {
        if (mediaId == null) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        if (currentUserId == null) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }
        // 校验媒体存在且 state=0（仅已审核通过的媒体可查已赞状态）
        Media media = mediaMapper.selectById(mediaId);
        if (media == null || media.getState() == null || media.getState() != 0) {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        // 只查 Redis bitmap（点赞/取消点赞已先写 Redis 再 MQ 同步 DB，以 Redis 为准）
        Optional<Boolean> fromCache = redisCacheMediaLikeService.getLikedFromCache(mediaId, currentUserId);
        if (fromCache.isPresent()) {
            return fromCache.get();
        }
        // 缓存未命中（key 不存在或该位为 0）：视为未赞
        return false;
    }
}
