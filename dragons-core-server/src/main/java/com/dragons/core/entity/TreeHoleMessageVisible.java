package com.dragons.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 树洞消息可见权限表
 * </p>
 *
 * 对应数据表：tree_hole_message_visible
 *
 * @author aice
 * @since 2026-01-21
 */
@Getter
@Setter
@TableName("tree_hole_message_visible")
public class TreeHoleMessageVisible implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("message_id")
    private Long messageId;

    /**
     * message_id 对应的消息共享给哪个树洞主人可见（接收方）
     */
    @TableField("owner_id")
    private Long ownerId;

    /**
     * 分享者用户ID（发起共享的一方）
     */
    @TableField("shared_by_user_id")
    private Long sharedByUserId;
}

