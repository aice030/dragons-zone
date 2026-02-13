package com.dragons.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 树洞信息内容表
 * </p>
 *
 * @author aice
 * @since 2026-01-17
 */
@Getter
@Setter
@TableName("tree_hole_message")
public class TreeHoleMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tree_hole_id")
    private Long treeHoleId;

    /**
     * 冗余字段，为了提高判断树洞主人是否存在未读消息的查询效率
     */
    @TableField("tree_hole_owner_id")
    private Long treeHoleOwnerId;

    @TableField("sender_id")
    private Long senderId;

    /**
     * 树洞消息对发送者是否可见：0=可见；1=不可见
     */
    @TableField("sender_deleted")
    private Byte senderDeleted;

    /**
     * 树洞消息的根消息（当前消息是回复哪条消息的）；为空表示用户投递，非空表示主人回复
     */
    @TableField("root_message_id")
    private Long rootMessageId;

    /**
     * 树洞消息的回复id（回复当前这条消息的消息id）；仅支持一条回复，非空表示已被回复
     */
    @TableField("reply_message_id")
    private Long replyMessageId;

    @TableField("content")
    private String content;

    /**
     * 0=未读；1=已读；2=逻辑删除；3=已回复
     */
    @TableField("state")
    private Byte state;

    /**
     * 最近更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
