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
 * 媒体资源可见权限表
 * </p>
 *
 * @author aice
 * @since 2026-01-17
 */
@Getter
@Setter
@TableName("media_visible")
public class MediaVisible implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("media_id")
    private Long mediaId;

    /**
     * media_id对应的媒体资源在哪个成员的专区可见
     */
    @TableField("user_id")
    private Long userId;
}
