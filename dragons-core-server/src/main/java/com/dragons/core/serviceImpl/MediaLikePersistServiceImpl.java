package com.dragons.core.serviceImpl;

import com.dragons.core.dao.MediaMapper;
import com.dragons.core.dto.MediaLikeEvent;
import com.dragons.core.entity.UserLikeRecord;
import com.dragons.core.service.IUserLikeRecordService;
import com.dragons.core.service.MediaLikePersistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 点赞/取消点赞事务落库：更新 user_like_record 与 media.like_count。
 *
 * @author aice
 * @since 2026-02-21
 */
@Slf4j
@Service
public class MediaLikePersistServiceImpl implements MediaLikePersistService {

    private final IUserLikeRecordService userLikeRecordService;
    private final MediaMapper mediaMapper;

    public MediaLikePersistServiceImpl(IUserLikeRecordService userLikeRecordService, MediaMapper mediaMapper) {
        this.userLikeRecordService = userLikeRecordService;
        this.mediaMapper = mediaMapper;
    }

    /**
     * 事务内落库：LIKE 写 user_like_record + media.like_count+1；UNLIKE 删记录 + like_count-1。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void persist(MediaLikeEvent event) {
        if (event == null || event.getOperation() == null || event.getMediaId() == null || event.getUserId() == null) {
            throw new IllegalArgumentException("MediaLikeEvent incomplete");
        }
        Long mediaId = event.getMediaId();
        Long userId = event.getUserId();

        if (event.getOperation() == MediaLikeEvent.Operation.LIKE) {
            // 插入点赞关系（已存在则跳过，兼容重试）
            if (!userLikeRecordService.existsByUserIdAndMediaId(userId, mediaId)) {
                UserLikeRecord record = new UserLikeRecord();
                record.setUserId(userId);
                record.setMediaId(mediaId);
                record.setCreateTime(LocalDateTime.now());
                userLikeRecordService.save(record);
            }
            mediaMapper.incrementLikeCount(mediaId);
            log.info("persist LIKE success mediaId={} userId={}", mediaId, userId);
        } else {
            userLikeRecordService.removeByUserIdAndMediaId(userId, mediaId);
            mediaMapper.decrementLikeCount(mediaId); // SQL 内 GREATEST(0, like_count-1)
            log.info("persist UNLIKE success mediaId={} userId={}", mediaId, userId);
        }
    }
}
