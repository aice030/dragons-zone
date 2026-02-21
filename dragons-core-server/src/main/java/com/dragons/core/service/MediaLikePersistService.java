package com.dragons.core.service;

import com.dragons.core.dto.MediaLikeEvent;

/**
 * 点赞/取消点赞落库：事务内写 user_like_record + 更新 media.like_count。
 * 仅由 MQ 消费者调用；失败时消费者负责回滚 Redis。
 */
public interface MediaLikePersistService {

    /**
     * 事务内落库。LIKE：插 user_like_record + like_count+1；UNLIKE：删记录 + like_count-1（≥0）。
     * 异常时由调用方（MediaLikeMqConsumer）回滚 Redis。
     */
    void persist(MediaLikeEvent event);
}
