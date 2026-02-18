package com.dragons.core.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.dragons.core.dto.MediaAuditResult;
import com.dragons.core.entity.Media;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 媒体资源表 服务类
 * </p>
 *
 * @author aice
 * @since 2026-01-17
 */
public interface IMediaService extends IService<Media> {

    /**
     * 上传媒体资源（图片/视频）
     *
     * @param file 文件
     * @param category 0=图片；1=视频
     * @param visibleUserIds 成员专区ID列表（必填，但可以为空数组[]）
     * @param uploaderUserId 上传者用户ID（从JWT获取）
     * @param title 标题（可选，最多32个字符）
     * @param description 描述（可选，最多128个字符）
     * @param cover 封面图片（可选，如果提供则使用用户上传的封面，否则按默认规则处理）
     * @return 上传结果
     */
    UploadResult upload(MultipartFile file,
                        Byte category,
                        List<Long> visibleUserIds,
                        Long uploaderUserId,
                        String title,
                        String description,
                        MultipartFile cover);

    /**
     * 获取媒体下载URL（预签名URL）
     *
     * @param mediaId 媒体ID
     * @return 预签名URL
     */
    String getDownloadUrl(Long mediaId);

    /**
     * 获取媒体下载URL（预签名URL）
     *
     * 说明：兼容游客模式（currentUserId 可为空），但当携带 JWT 且为审核者（作者/管理员）时，
     * 允许预览/下载待审核（state=6）和审核未通过（state=7）的媒体，用于审核流程。
     *
     * @param mediaId 媒体ID
     * @param currentUserId 当前登录用户ID（从JWT获取；可为空表示游客）
     * @return 预签名URL
     */
    String getDownloadUrl(Long mediaId, Long currentUserId);

    /**
     * 删除媒体资源
     *
     * 约束：仅允许上传者本人删除（通过JWT中的userId校验）
     *
     * @param mediaId 媒体ID
     * @param currentUserId 当前登录用户ID（从JWT获取）
     */
    void delete(Long mediaId, Long currentUserId);

    /**
     * 获取媒体详情
     *
     * 说明：产品定义是“永远全部公开”，专区只是筛选展示，不作为权限系统，因此详情无需专区参数。
     *
     * @param mediaId 媒体ID
     * @param currentUserId 当前登录用户ID（从JWT获取；可为空表示游客）
     * @return 媒体详情
     */
    MediaDetailResult getMediaDetail(Long mediaId, Long currentUserId);

    /**
     * 更新媒体基础信息（仅允许 state=0/6/7 且上传者本人）
     *
     * @param mediaId 媒体主键ID
     * @param currentUserId 当前登录用户ID（从JWT获取）
     * @param title 标题（可选，最多32字符）
     * @param description 描述（可选，最多128字符）
     * @return 与上传一致的 UploadResult（visibleUserIds 字段可能为 null）
     * 注意：如果原状态是 state=7（审核未通过），修改后自动变为 state=6（待审核），需要重新审核
     */
    UploadResult update(Long mediaId,
                       Long currentUserId,
                       String title,
                       String description);

    /**
     * 更新视频封面（仅允许 state=0 且上传者本人，且 media.category=1）
     *
     * 说明：先上传 MinIO 成功，再更新 DB；DB 更新失败会删除刚上传的封面做补偿。
     *
     * @param mediaId 媒体主键ID
     * @param cover 封面图片（必传）
     * @param currentUserId 当前登录用户ID（从JWT获取）
     * @return 与上传一致的 UploadResult（便于前端复用）
     */
    CoverUpdateResult updateCover(Long mediaId, MultipartFile cover, Long currentUserId);

    /**
     * 修复/重建某条媒体的可见范围（仅允许上传者本人；仅修改 DB，不触碰 MinIO）
     *
     * 典型场景：upload 主文件已成功，但写入 media_visible 失败导致 state=2（上传成功但不可见）。
     * 调用该接口后会覆盖写 media_visible，并将 state 修正为 0（正常可查看）。
     *
     * @param mediaId 媒体主键ID
     * @param visibleUserIds 成员专区ID列表（必填，可为空数组）
     * @param currentUserId 当前登录用户ID（从JWT获取）
     * @return 与上传一致的 UploadResult
     */
    UploadResult rebuildVisible(Long mediaId, List<Long> visibleUserIds, Long currentUserId);

    /**
     * 批量审核通过媒体（仅管理员或作者可操作）
     *
     * @param mediaIds 要审核通过的媒体ID列表
     * @param auditorUserId 审核者用户ID（从JWT获取，必须是管理员或作者）
     * @return 审核结果，包含失败的媒体列表（mediaId 和 title）
     */
    MediaAuditResult approveMedia(List<Long> mediaIds, Long auditorUserId);

    /**
     * 批量审核驳回媒体（仅管理员或作者可操作）
     *
     * @param mediaIds 要审核驳回的媒体ID列表
     * @param auditorUserId 审核者用户ID（从JWT获取，必须是管理员或作者）
     * @return 审核结果，包含失败的媒体列表（mediaId 和 title）
     */
    MediaAuditResult rejectMedia(List<Long> mediaIds, Long auditorUserId);

    /**
     * 查询待审核媒体列表（仅管理员或作者可访问）
     *
     * @param page 页码（从1开始）
     * @param size 每页数量
     * @param category 类型筛选：null=全部，0=图片，1=视频
     * @param auditorUserId 审核者用户ID（从JWT获取，必须是管理员或作者）
     * @return 待审核媒体列表（state=6）
     */
    IMediaVisibleService.MediaPageResult listPendingMedia(Integer page, Integer size, Byte category, Long auditorUserId);

    /**
     * 上传结果（简单内部类，用于 Result<UploadResult> 返回）
     */
    class UploadResult {
        public Long mediaId;
        public String storagePath;
        public Byte category;
        public List<Long> visibleUserIds;

        public UploadResult(Long mediaId, String storagePath, Byte category, List<Long> visibleUserIds) {
            this.mediaId = mediaId;
            this.storagePath = storagePath;
            this.category = category;
            this.visibleUserIds = visibleUserIds;
        }
    }

    /**
     * 封面更新结果（用于封面独立更新接口返回）
     */
    class CoverUpdateResult {
        public Long mediaId;
        public String coverPath;
        public String coverUrl;

        public CoverUpdateResult(Long mediaId, String coverPath, String coverUrl) {
            this.mediaId = mediaId;
            this.coverPath = coverPath;
            this.coverUrl = coverUrl;
        }
    }

    /**
     * 下载URL结果（简单内部类）
     */
    class DownloadUrlResult {
        public String downloadUrl;

        public DownloadUrlResult(String downloadUrl) {
            this.downloadUrl = downloadUrl;
        }
    }


    /**
     * 媒体详情返回结构
     */
    class MediaDetailResult {
        public Long id;
        public Byte category;
        public String title;
        public String description;
        public String storagePath;
        public String coverPath;
        public String coverUrl;
        public Long uploaderId;
        public LocalDateTime updateTime;

        @JsonCreator
        public MediaDetailResult(@JsonProperty("id") Long id,
                                 @JsonProperty("category") Byte category,
                                 @JsonProperty("title") String title,
                                 @JsonProperty("description") String description,
                                 @JsonProperty("storagePath") String storagePath,
                                 @JsonProperty("coverPath") String coverPath,
                                 @JsonProperty("coverUrl") String coverUrl,
                                 @JsonProperty("uploaderId") Long uploaderId,
                                 @JsonProperty("updateTime") LocalDateTime updateTime) {
            this.id = id;
            this.category = category;
            this.title = title;
            this.description = description;
            this.storagePath = storagePath;
            this.coverPath = coverPath;
            this.coverUrl = coverUrl;
            this.uploaderId = uploaderId;
            this.updateTime = updateTime;
        }
    }
}
