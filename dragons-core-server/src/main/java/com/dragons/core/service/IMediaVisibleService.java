package com.dragons.core.service;

import com.dragons.core.entity.MediaVisible;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 媒体资源可见权限表 服务类
 * </p>
 *
 * @author aice
 * @since 2026-01-17
 */
public interface IMediaVisibleService extends IService<MediaVisible> {

    /**
     * 获取媒体列表（按“专区”筛选）
     *
     * 说明：当前产品定义为“永远全部公开”，因此：
     * - zoneUserId=0（公共区）：直接查询 media 表
     * - zoneUserId=成员ID（成员专区）：通过 media_visible 过滤 media_id
     *
     * @param page 页码（从1开始）
     * @param size 每页数量
     * @param category 可选：0=图片；1=视频
     * @param zoneUserId 专区ID：0=公共区；成员ID=成员专区
     */
    MediaPageResult listMedia(Integer page, Integer size, Byte category, Long zoneUserId);

    /**
     * 获取“我的上传”列表（上传者本人管理后台用）
     *
     * 注意：该接口虽然放在 media_visible 系列下，但查询仍然直接来自 media 表：
     * uploader_id = 当前登录用户ID
     *
     * @param page 页码（从1开始）
     * @param size 每页数量（默认10，最大100）
     * @param category 可选：0=图片；1=视频；null=不筛选（全展示）
     * @param uploaderUserId 当前登录用户ID（从JWT获取）
     */
    MyUploadPageResult listMyUpload(Integer page, Integer size, Byte category, Long uploaderUserId);

    /**
     * 根据媒体ID查询该媒体属于哪些成员专区
     *
     * @param mediaId 媒体ID
     * @return 成员专区ID列表（user_id），如果媒体只在公共区可见则返回空列表
     */
    List<Long> getVisibleUserIdsByMediaId(Long mediaId);

    /**
     * 热门媒体列表（按点赞数降序，Top N，不做分页、不做专区）
     *
     * @param category 分类：null=全部，0=图片，1=视频（对应三个 ZSET）
     * @param size     返回条数（默认 20，最大建议 100）；内部会多取 size+10 条再过滤 state!=0，保证返回满 size 条
     * @return 热门列表项（id、category、title、description、coverUrl、likeCount），仅 state=0
     */
    List<HotListItem> listHotMedia(Byte category, Integer size);

    /**
     * 热门列表单项（点赞榜：id、分类、标题、描述、封面、点赞数）
     */
    class HotListItem {
        public Long id;
        public Byte category;
        public String title;
        public String description;
        public String coverUrl;
        public Long likeCount;

        public HotListItem(Long id, Byte category, String title, String description, String coverUrl, Long likeCount) {
            this.id = id;
            this.category = category;
            this.title = title;
            this.description = description;
            this.coverUrl = coverUrl;
            this.likeCount = likeCount;
        }
    }

    /**
     * 媒体列表单项（面向前端的最小字段集合）
     */
    class MediaListItem {
        public Long id;
        public Byte category;
        public String title;
        public String coverPath;
        public LocalDateTime updateTime;
        /**
         * 封面预签名URL（2小时有效），用于公共区/成员专区列表直接展示缩略图
         * - 支持游客模式接口
         * - 为空表示无法生成（例如对象不存在或 coverPath 为空）
         */
        public String coverUrl;

        public MediaListItem(Long id,
                             Byte category,
                             String title,
                             String coverPath,
                             LocalDateTime updateTime,
                             String coverUrl) {
            this.id = id;
            this.category = category;
            this.title = title;
            this.coverPath = coverPath;
            this.updateTime = updateTime;
            this.coverUrl = coverUrl;
        }
    }

    /**
     * 分页返回结构（total + list）
     */
    class MediaPageResult {
        public Long total;
        public List<MediaListItem> list;

        public MediaPageResult(Long total, List<MediaListItem> list) {
            this.total = total;
            this.list = list;
        }
    }

    /**
     * 我的上传列表单项（管理用：需要 state）
     */
    class MyUploadListItem {
        public Long id;
        public Byte category;
        public Byte state;
        public String title;
        public String coverPath;
        public LocalDateTime updateTime;
        /**
         * 封面预签名URL（便于前端直接展示缩略图）
         * - 仅用于“我的上传”管理列表（该接口需登录）
         * - 为空表示无法生成（例如对象不存在或 coverPath 为空）
         */
        public String coverUrl;

        public MyUploadListItem(Long id,
                                Byte category,
                                Byte state,
                                String title,
                                String coverPath,
                                LocalDateTime updateTime) {
            this.id = id;
            this.category = category;
            this.state = state;
            this.title = title;
            this.coverPath = coverPath;
            this.updateTime = updateTime;
        }
    }

    /**
     * 我的上传分页返回结构（total + list）
     */
    class MyUploadPageResult {
        public Long total;
        public List<MyUploadListItem> list;

        public MyUploadPageResult(Long total, List<MyUploadListItem> list) {
            this.total = total;
            this.list = list;
        }
    }
}
