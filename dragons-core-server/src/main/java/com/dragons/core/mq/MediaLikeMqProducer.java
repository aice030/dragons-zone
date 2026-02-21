package com.dragons.core.mq;

import com.dragons.core.dto.MediaLikeEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 点赞/取消点赞事件 MQ 发送端。发送失败时由调用方（MediaServiceImpl）回滚 Redis。
 *
 * @author aice
 * @since 2026-02-21
 */
@Slf4j
@Component
public class MediaLikeMqProducer {

    private final RocketMQTemplate rocketMQTemplate;

    @Value("${media-like.topic:media-like-topic}")
    private String topic;

    public MediaLikeMqProducer(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /**
     * 同步发送点赞事件。失败抛异常，由 MediaServiceImpl 回滚 Redis。
     */
    public void send(MediaLikeEvent event) {
        if (event == null || event.getOperation() == null || event.getMediaId() == null || event.getUserId() == null) {
            throw new IllegalArgumentException("MediaLikeEvent incomplete");
        }
        rocketMQTemplate.syncSend(topic, event);
        log.info("media like event sent topic={} operation={} mediaId={} userId={}", topic, event.getOperation(), event.getMediaId(), event.getUserId());
    }
}
