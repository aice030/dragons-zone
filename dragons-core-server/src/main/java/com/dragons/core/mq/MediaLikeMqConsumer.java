package com.dragons.core.mq;

import com.dragons.core.cache.RedisCacheMediaLikeService;
import com.dragons.core.dto.MediaLikeEvent;
import com.dragons.core.service.MediaLikePersistService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 点赞/取消点赞 MQ 消费者（Consumer）：订阅 media-like-topic，收到消息后把点赞/取消点赞写入数据库。
 * 仅当 media.like.use-mq=true 时注册，否则不连 RocketMQ，便于本地使用「先 DB 再 Redis」方案时无需启动 MQ。
 * 流程：先改 Redis（在 Producer 侧完成）→ 发 MQ → 本类 onMessage 被调用 → 事务内写 user_like_record 与 media.like_count。
 * 落库失败时回滚 Redis 并抛出异常，RocketMQ 会重试或把消息投递到死信队列。
 * RocketMQMessageListener 指定订阅的 Topic 与 consumerGroup；同组多实例时由 Broker 做负载均衡。
 *
 * @author aice
 * @since 2026-02-21
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "media.like.use-mq", havingValue = "true")
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
     * RocketMQ 拉取到一条消息时回调此方法；消息体为 MediaLikeEvent（由 RocketMQ 反序列化）。
     * 成功：persist 内事务提交，DB 与 Redis 一致。失败：回滚 Redis 再抛异常，触发重试或死信。
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
            // 落库失败：在 Redis 中撤销本次点赞/取消点赞，避免缓存比 DB 多/少
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
