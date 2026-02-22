package com.dragons.core.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * 点赞/取消点赞 MQ 消息体：生产者序列化后发往 Topic，消费者反序列化后根据 operation/mediaId/userId/category 落库或回滚 Redis。
 * 实现 Serializable 以便 RocketMQ 在网络上传输对象。
 *
 * @author aice
 * @since 2026-02-21
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MediaLikeEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 操作类型：LIKE=点赞，UNLIKE=取消点赞 */
    public enum Operation {
        LIKE,
        UNLIKE
    }

    /** 操作：LIKE / UNLIKE */
    private Operation operation;
    /** 媒体 ID */
    private Long mediaId;
    /** 用户 ID（点赞者） */
    private Long userId;
    /** 媒体分类 0=图片 1=视频，用于回滚时定位 ZSET key */
    private Byte category;
}
