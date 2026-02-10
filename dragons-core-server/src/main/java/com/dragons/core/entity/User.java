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
 * 用户表
 * </p>
 *
 * @author aice
 * @since 2026-01-17
 */
@Getter
@Setter
@TableName("user")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("login_name")
    private String loginName;

    /**
     * 加密处理
     */
    @TableField("password")
    private String password;

    @TableField("nick_name")
    private String nickName;

    @TableField("phone_number")
    private String phoneNumber;

    /**
     * 0=作者；1=管理员；2=普通用户；3=游客；
     */
    @TableField("level")
    private Byte level;

    /**
     * 0=正常；1=逻辑删除；2=黑名单
     */
    @TableField("state")
    private Byte state;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
