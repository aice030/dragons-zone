package com.dragons.core.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.dragons.core.dto.MediaAuditResult;
import com.dragons.core.dto.OssStsCredentials;
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
     * 准备上传媒体资源（两阶段上传的第 1 步）
     *
     * 说明：
     * - 不接收主文件与封面，仅接收前端计算的 fileHash、基础信息等；
     * - 负责参数校验、基于 fileHash 的幂等校验、落库 Media（state=1）并生成主文件存储路径（不落库 coverPath，不写 media_visible）；
     * - 返回 mediaId、storagePath 以及用于前端直传对象存储的上传 URL（例如预签名 PUT URL），供前端直传对象存储使用。
     *
     * @param fileHash 前端计算的文件 hash（用于幂等校验）
     * @param category 0=图片；1=视频
     * @param uploaderUserId 上传者用户ID（从JWT获取）
     * @param title 标题（可选，最多32个字符）
     * @param description 描述（可选，最多128个字符）
     * @param filename 可选的原始文件名（用于推断扩展名；为空时按 category 使用默认扩展名）
     * @return 上传结果（至少包含 mediaId、storagePath、uploadUrl 等）
     */
    UploadResult prepareUpload(String fileHash,
                               Byte category,
                               Long uploaderUserId,
                               String title,
                               String description,
                               String filename);

    /**
     * 通知上传结果（两阶段上传的第 2 步）
     *
     * 说明：
     * - 前端完成直传 OSS 后调用；
     * - success=true 时先校验对象是否存在，再写 state、media_visible；校验 cover 文件类型后计算 coverPath、落库并上传封面到 OSS；
     * - success=false 时将 state 置为 3，必要时清理对象存储；cover 可不传。
     *
     * @param mediaId 媒体主键ID（准备上传阶段返回）
     * @param success true=上传成功，false=上传失败
     * @param visibleUserIds 成员专区ID列表（必填，但可以为空数组[]）
     * @param currentUserId 当前登录用户ID（从JWT获取）
     * @param cover 封面图片文件；success=true 时必传，后端校验类型后计算 coverPath、落库并上传到 OSS；success=false 时可 null
     */
    void uploadComplete(Long mediaId,
                        boolean success,
                        List<Long> visibleUserIds,
                        Long currentUserId,
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
     * 点赞：仅 state=0 的媒体可被点赞，每人每条仅可赞一次（重复请求幂等返回成功）。仅更新 Redis ZSET，定时回写 DB。
     *
     * @param mediaId       媒体ID
     * @param currentUserId 当前用户ID（从JWT获取，必填）
     */
    void like(Long mediaId, Long currentUserId);

    /**
     * 取消点赞：未赞过则幂等成功。仅更新 Redis ZSET，定时回写 DB。
     *
     * @param mediaId       媒体ID
     * @param currentUserId 当前用户ID（从JWT获取，必填）
     */
    void unlike(Long mediaId, Long currentUserId);

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
        /**
         * 前端直传对象存储使用的上传 URL（例如预签名 PUT URL）
         */
        public String uploadUrl;
        /**
         * 上传 URL 的有效期（秒），便于前端在过期后做重试或重新准备上传
         */
        public Integer uploadUrlExpireSeconds;
        /**
         * STS 临时凭证（准备上传时若配置了 STS 则返回，供前端 OSS SDK 分片上传等使用）
         */
        public OssStsCredentials stsCredentials;

        public UploadResult(Long mediaId,
                            String storagePath,
                            Byte category,
                            List<Long> visibleUserIds,
                            String uploadUrl,
                            Integer uploadUrlExpireSeconds,
                            OssStsCredentials stsCredentials) {
            this.mediaId = mediaId;
            this.storagePath = storagePath;
            this.category = category;
            this.visibleUserIds = visibleUserIds;
            this.uploadUrl = uploadUrl;
            this.uploadUrlExpireSeconds = uploadUrlExpireSeconds;
            this.stsCredentials = stsCredentials;
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
        public Long likeCount;

        public MediaDetailResult(){}

        @JsonCreator
        public MediaDetailResult(@JsonProperty("id") Long id,
                                 @JsonProperty("category") Byte category,
                                 @JsonProperty("title") String title,
                                 @JsonProperty("description") String description,
                                 @JsonProperty("storagePath") String storagePath,
                                 @JsonProperty("coverPath") String coverPath,
                                 @JsonProperty("coverUrl") String coverUrl,
                                 @JsonProperty("uploaderId") Long uploaderId,
                                 @JsonProperty("updateTime") LocalDateTime updateTime,
                                 @JsonProperty("likeCount") Long likeCount) {
            this.id = id;
            this.category = category;
            this.title = title;
            this.description = description;
            this.storagePath = storagePath;
            this.coverPath = coverPath;
            this.coverUrl = coverUrl;
            this.uploaderId = uploaderId;
            this.updateTime = updateTime;
            this.likeCount = likeCount;
        }
    }
}
