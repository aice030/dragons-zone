package com.dragons.core.mq;

import com.dragons.core.dto.MediaLikeEvent;

/**
 * 点赞/取消点赞事件发送抽象：MQ 方案下发送到 RocketMQ，非 MQ 方案下为 NoOp。
 * 便于在未启用 RocketMQ 时应用仍可启动，且不删除 MQ 相关代码。
 *
 * @author aice
 * @since 2026-02-24
 */
public interface MediaLikeEventSender {

    /**
     * 发送一条点赞/取消点赞事件（MQ 方案下发给 Broker，NoOp 方案下忽略）。
     */
    void send(MediaLikeEvent event);
}
