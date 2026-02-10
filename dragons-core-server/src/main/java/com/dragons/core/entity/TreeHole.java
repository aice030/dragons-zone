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
 * 树洞表
 * </p>
 *
 * @author aice
 * @since 2026-01-17
 */
@Getter
@Setter
@TableName("tree_hole")
public class TreeHole implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("owner_id")
    private Long ownerId;

    /**
     * 0=正常；1=有未读消息；2=禁止他人向树洞投入新消息
     */
    @TableField("state")
    private Byte state;
}
