package com.dragons.core.mq;

import com.dragons.core.dto.MediaLikeEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 点赞/取消点赞事件 MQ 生产者（Producer）：负责把点赞/取消点赞事件发到 RocketMQ 的某个 Topic。
 * 消息队列中，生产者只负责“发消息”，不关心谁处理；消费者在另一处订阅该 Topic 并处理。
 * 发送失败时由调用方（MediaServiceImpl）回滚 Redis，保证缓存与 DB 最终一致。
 *
 * @author aice
 * @since 2026-02-21
 */
@Slf4j
@Component
public class MediaLikeMqProducer {

    private final RocketMQTemplate rocketMQTemplate;

    /** 发送到的 Topic 名称；Topic 是 MQ 里消息的分类，消费者通过订阅同一 Topic 收到消息 */
    @Value("${media-like.topic:media-like-topic}")
    private String topic;

    public MediaLikeMqProducer(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /**
     * 同步发送一条点赞事件到 MQ。
     * syncSend：发送后阻塞等待 Broker 确认，失败会抛异常，便于调用方立刻回滚 Redis。
     */
    public void send(MediaLikeEvent event) {
        if (event == null || event.getOperation() == null || event.getMediaId() == null || event.getUserId() == null) {
            throw new IllegalArgumentException("MediaLikeEvent incomplete");
        }
        rocketMQTemplate.syncSend(topic, event);
        log.info("media like event sent topic={} operation={} mediaId={} userId={}", topic, event.getOperation(), event.getMediaId(), event.getUserId());
    }
}
