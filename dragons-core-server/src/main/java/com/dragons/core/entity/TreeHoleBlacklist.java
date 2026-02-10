package com.dragons.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 树洞黑名单表
 * </p>
 *
 * @author aice
 * @since 2026-02-03
 */
@Getter
@Setter
@ToString
@TableName("tree_hole_blacklist")
public class TreeHoleBlacklist implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 树洞主人ID（谁拉黑的）
     */
    @TableField("owner_id")
    private Long ownerId;

    /**
     * 被拉黑的用户ID（谁被拉黑）
     */
    @TableField("blocked_user_id")
    private Long blockedUserId;

    /**
     * 状态：0=生效；1=解除/失效
     */
    @TableField("state")
    private Byte state;

    /**
     * 拉黑原因（可选）
     */
    @TableField("reason")
    private String reason;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
