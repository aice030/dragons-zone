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
 * 媒体资源表
 * </p>
 *
 * @author aice
 * @since 2026-01-17
 */
@Getter
@Setter
@TableName("media")
public class Media implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 上传者id
     */
    @TableField("uploader_id")
    private Long uploaderId;

    /**
     * 媒体资源哈希，用于保证幂等性，防止上传重复内容
     */
    @TableField("file_hash")
    private String fileHash;

    /**
     * 0=图片；1=视频
     */
    @TableField("category")
    private Byte category;

    /**
     * 标题最多32个字符
     */
    @TableField("title")
    private String title;

    /**
     * 描述最多128个字符
     */
    @TableField("description")
    private String description;

    /**
     * 图片 / 视频资源的实际存储地址
     */
    @TableField("storage_path")
    private String storagePath;

    /**
     * 封面路径，图片直接复用图片地址，视频需要生成
     */
    @TableField("cover_path")
    private String coverPath;

    /**
     * 0=正常；1=正在上传；2=上传成功；3=上传失败；4=正在删除；5=已删除；6=待审核；7=审核未通过
     */
    @TableField("state")
    private Byte state;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 点赞数
     */
    @TableField("like_count")
    private Long likeCount;

    /**
     * 点赞数更新时间
     */
    @TableField("like_count_update_time")
    private LocalDateTime likeCountUpdateTime;
}
