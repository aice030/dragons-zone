package com.dragons.core.mq;

import com.dragons.core.dto.MediaLikeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 点赞事件发送 NoOp 实现：当 media.like.use-mq=false 时使用，不连 RocketMQ，不发送消息。
 * 当前 like/unlike 走「先 DB 再 Redis」时本实现被注入，send 调用被忽略。
 *
 * @author aice
 * @since 2026-02-24
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "media.like.use-mq", havingValue = "false", matchIfMissing = true)
public class MediaLikeEventSenderNoOp implements MediaLikeEventSender {

    @Override
    public void send(MediaLikeEvent event) {
        // 未启用 MQ，不发送；当前流程为 likeSyncDbFirst/unlikeSyncDbFirst，不依赖 MQ
        if (log.isDebugEnabled()) {
            log.debug("media like event send skipped (use-mq=false) operation={} mediaId={} userId={}",
                    event != null ? event.getOperation() : null,
                    event != null ? event.getMediaId() : null,
                    event != null ? event.getUserId() : null);
        }
    }
}
