package com.dragons.core.mq;

import com.dragons.core.cache.RedisCacheMediaLikeService;
import com.dragons.core.dto.MediaLikeEvent;
import com.dragons.core.service.MediaLikePersistService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 点赞/取消点赞 MQ 消费者：事务内落库 user_like_record 与 media.like_count，失败则回滚 Redis。
 *
 * @author aice
 * @since 2026-02-21
 */
@Slf4j
@Component
@RocketMQMessageListener(topic = "media-like-topic", consumerGroup = "media-like-consumer")
public class MediaLikeMqConsumer implements RocketMQListener<MediaLikeEvent> {

    private final MediaLikePersistService mediaLikePersistService;
    private final RedisCacheMediaLikeService redisCacheMediaLikeService;

    public MediaLikeMqConsumer(MediaLikePersistService mediaLikePersistService,
                              RedisCacheMediaLikeService redisCacheMediaLikeService) {
        this.mediaLikePersistService = mediaLikePersistService;
        this.redisCacheMediaLikeService = redisCacheMediaLikeService;
    }

    /**
     * 消费点赞事件：事务落库；失败则回滚 Redis 后 rethrow，由 RocketMQ 重试或进死信。
     */
    @Override
    public void onMessage(MediaLikeEvent event) {
        if (event == null || event.getOperation() == null || event.getMediaId() == null || event.getUserId() == null || event.getCategory() == null) {
            log.warn("MediaLikeMqConsumer invalid event ignored: {}", event);
            return;
        }
        try {
            mediaLikePersistService.persist(event);
        } catch (Exception e) {
            log.error("MediaLikeMqConsumer persist failed, rolling back Redis event={} error={}", event, e.getMessage());
            // 落库失败：撤销 Redis 中本次操作，保证与 DB 一致
            try {
                if (event.getOperation() == MediaLikeEvent.Operation.LIKE) {
                    redisCacheMediaLikeService.rollbackLike(event.getMediaId(), event.getUserId(), event.getCategory());
                } else {
                    redisCacheMediaLikeService.rollbackUnlike(event.getMediaId(), event.getUserId(), event.getCategory());
                }
            } catch (Exception rollbackEx) {
                log.error("MediaLikeMqConsumer Redis rollback failed event={} error={}", event, rollbackEx.getMessage());
            }
            throw new RuntimeException(e);
        }
    }
}
