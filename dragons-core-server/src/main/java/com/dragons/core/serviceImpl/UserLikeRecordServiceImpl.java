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
     * <b>查询与写回流程</b>：
     * <ol>
     *   <li>先查 Redis：key 为 {@code media:liked:{mediaId}}，offset=userId。若该位为 1，直接返回 true，不查 DB。</li>
     *   <li>若该 key 在 Redis 中不存在（该媒体从未有过任何点赞缓存），或 key 存在但该位为 0：Redis 的 GETBIT 在 key 不存在时也返回 0，
     *       无法区分这两种情况，统一查 DB（user_like_record 表）得到真实结果。</li>
     *   <li>写回缓存：调用 {@link RedisCacheMediaLikeService#setLiked}。若 key 不存在，SETBIT 会自动创建 {@code media:liked:{mediaId}}，
     *       只写入当前用户这一位（0 或 1），下次再查可直接命中缓存。</li>
     * </ol>
     */
    @Override
    public boolean getLikeStatus(Long mediaId, Long currentUserId) {
        if (mediaId == null) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        if (currentUserId == null) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }
        // 步骤1：校验媒体存在且 state=0（仅已审核通过的媒体可查已赞状态）
        Media media = mediaMapper.selectById(mediaId);
        if (media == null || media.getState() == null || media.getState() != 0) {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        // 步骤2：先查 Redis bitmap，命中（位为 1）则直接返回已赞
        Optional<Boolean> fromCache = redisCacheMediaLikeService.getLikedFromCache(mediaId, currentUserId);
        if (fromCache.isPresent()) {
            log.info("getLikeStatus cache hit mediaId={} userId={} liked=true", mediaId, currentUserId);
            return fromCache.get();
        }
        // 步骤3：缓存未命中，查 DB（user_like_record 表）
        boolean dbLiked = existsByUserIdAndMediaId(currentUserId, mediaId);
        log.info("getLikeStatus db query mediaId={} userId={} liked={}", mediaId, currentUserId, dbLiked);
        // 步骤4：写回 Redis，下次可命中；key 不存在时 SETBIT 会自动创建
        redisCacheMediaLikeService.setLiked(mediaId, currentUserId, dbLiked);
        return dbLiked;
    }
}
