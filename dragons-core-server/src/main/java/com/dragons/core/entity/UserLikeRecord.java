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
 * 用户点赞记录表
 * </p>
 *
 * @author aice
 * @since 2026-02-20
 */
@Getter
@Setter
@ToString
@TableName("user_like_record")
public class UserLikeRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("media_id")
    private Long mediaId;

    @TableField("create_time")
    private LocalDateTime createTime;
}
