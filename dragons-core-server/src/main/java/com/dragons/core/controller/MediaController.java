package com.dragons.core.controller;

import com.dragons.core.dto.MediaAuditRequest;
import com.dragons.core.dto.MediaAuditResult;
import com.dragons.core.dto.ResponseCode;
import com.dragons.core.dto.Result;
import com.dragons.core.security.JwtPrincipal;
import com.dragons.core.service.IMediaService;
import com.dragons.core.service.IMediaVisibleService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

/**
 * <p>
 * 媒体资源表 前端控制器
 * </p>
 *
 * @author aice
 * @since 2026-01-17
 */
@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final IMediaService mediaService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造器注入（显式 @Autowired，便于学习）
     */
    @Autowired
    public MediaController(IMediaService mediaService) {
        this.mediaService = mediaService;
    }

    /**
     * 上传媒体资源（图片/视频）
     *
     * multipart/form-data:
     * - file: 文件
     * - category: 0=图片，1=视频
     * - visibleUserIds: JSON数组字符串，例如 [1,2,3] 或 []（必填，但可以为空数组）
     * - title: 标题（可选）
     * - description: 描述（可选）
     * - cover: 封面图片（可选，如果提供则使用用户上传的封面）
     */
    @PostMapping("/upload")
    public Result<IMediaService.UploadResult> upload(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestPart("file") MultipartFile file,
            @RequestParam("category") Byte category,
            @RequestParam("visibleUserIds") String visibleUserIdsJson,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestPart(value = "cover", required = false) MultipartFile cover
    ) {
        if (principal == null) {
            return Result.error(ResponseCode.UNAUTHORIZED);
        }
        
        List<Long> visibleUserIds = parseVisibleUserIds(visibleUserIdsJson);

        IMediaService.UploadResult result = mediaService.upload(
                file,
                category,
                visibleUserIds,
                principal.getUserId(),
                title,
                description,
                cover
        );
        return Result.success("上传成功", result);
    }

    /**
     * 更新媒体资源（仅上传者本人，state=0/6/7 可更新）
     *
     * PUT /api/media/{id}
     * form-data 或 x-www-form-urlencoded:
     * - title: 标题（可选）
     * - description: 描述（可选）
     * 注意：如果原状态是 state=7（审核未通过），修改后自动变为 state=6（待审核），需要重新审核
     */
    @PutMapping("/{id}")
    public Result<IMediaService.UploadResult> update(
            @PathVariable("id") Long mediaId,
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description
    ) {
        if (principal == null) {
            return Result.error(ResponseCode.UNAUTHORIZED);
        }
        IMediaService.UploadResult result = mediaService.update(
                mediaId,
                principal.getUserId(),
                title,
                description
        );
        return Result.success("更新成功", result);
    }

    /**
     * 更新视频封面（仅上传者本人，仅 state=0；仅视频允许）
     *
     * PUT /api/media/{id}/cover
     * multipart/form-data:
     * - cover: 封面图片（必填）
     */
    @PutMapping("/{id}/cover")
    public Result<IMediaService.CoverUpdateResult> updateCover(
            @PathVariable("id") Long mediaId,
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestPart("cover") MultipartFile cover
    ) {
        if (principal == null) {
            return Result.error(ResponseCode.UNAUTHORIZED);
        }
        IMediaService.CoverUpdateResult result = mediaService.updateCover(mediaId, cover, principal.getUserId());
        return Result.success("封面更新成功", result);
    }

    /**
     * 修复/重建媒体可见范围（仅上传者本人；仅修改 DB，不触碰 MinIO）
     *
     * PUT /api/media/{id}/visible
     * form-data 或 x-www-form-urlencoded:
     * - visibleUserIds: JSON数组字符串，例如 [1,2,3] 或 []（必填，但可以为空数组）
     */
    @PutMapping("/{id}/visible")
    public Result<IMediaService.UploadResult> rebuildVisible(
            @PathVariable("id") Long mediaId,
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam("visibleUserIds") String visibleUserIdsJson
    ) {
        if (principal == null) {
            return Result.error(ResponseCode.UNAUTHORIZED);
        }
        List<Long> visibleUserIds = parseVisibleUserIds(visibleUserIdsJson);
        IMediaService.UploadResult result = mediaService.rebuildVisible(mediaId, visibleUserIds, principal.getUserId());
        return Result.success("可见范围修复成功", result);
    }

    /**
     * 获取媒体下载URL（预签名URL）
     *
     * 不需要权限检查：因为展示功能已经过滤，用户能看到的都是可以下载的
     */
    @GetMapping("/{id}/download")
    public Result<IMediaService.DownloadUrlResult> download(@PathVariable("id") Long mediaId) {
        String downloadUrl = mediaService.getDownloadUrl(mediaId);
        IMediaService.DownloadUrlResult result = new IMediaService.DownloadUrlResult(downloadUrl);
        return Result.success("获取成功", result);
    }

    /**
     * 删除媒体资源（仅上传者本人可删除）
     *
     * Header: Authorization: Bearer <JWT>
     */
    @DeleteMapping("/{id}/delete")
    public Result<Void> delete(@AuthenticationPrincipal JwtPrincipal principal,
                               @PathVariable("id") Long mediaId) {
        if (principal == null) {
            return Result.error(ResponseCode.UNAUTHORIZED);
        }
        mediaService.delete(mediaId, principal.getUserId());
        return Result.success("删除成功", null);
    }

    /**
     * 获取媒体详情
     * 支持游客模式：未登录也可访问，无需请求头。专区只用于列表筛选，详情无需传专区ID。
     */
    @GetMapping("/{id}")
    public Result<IMediaService.MediaDetailResult> detail(
            @PathVariable("id") Long mediaId,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        Long currentUserId = principal == null ? null : principal.getUserId();
        IMediaService.MediaDetailResult result = mediaService.getMediaDetail(mediaId, currentUserId);
        return Result.success("查询成功", result);
    }

    /**
     * 批量审核通过媒体（仅管理员或作者可操作）
     *
     * POST /api/media/audit/approve
     * Content-Type: application/json
     * Body: { "mediaIds": [1, 2, 3] }
     */
    @PostMapping("/audit/approve")
    public Result<MediaAuditResult> approveMedia(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestBody MediaAuditRequest request
    ) {
        if (principal == null) {
            return Result.error(ResponseCode.UNAUTHORIZED);
        }
        if (request == null || request.getMediaIds() == null || request.getMediaIds().isEmpty()) {
            return Result.error(ResponseCode.BAD_REQUEST);
        }
        MediaAuditResult result = mediaService.approveMedia(request.getMediaIds(), principal.getUserId());
        // 根据失败列表决定返回消息
        if (result.getFailedItems() == null || result.getFailedItems().isEmpty()) {
            return Result.success("审核通过成功", result);
        } else {
            return Result.success("部分审核通过失败", result);
        }
    }

    /**
     * 批量审核驳回媒体（仅管理员或作者可操作）
     *
     * POST /api/media/audit/reject
     * Content-Type: application/json
     * Body: { "mediaIds": [1, 2, 3] }
     */
    @PostMapping("/audit/reject")
    public Result<MediaAuditResult> rejectMedia(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestBody MediaAuditRequest request
    ) {
        if (principal == null) {
            return Result.error(ResponseCode.UNAUTHORIZED);
        }
        if (request == null || request.getMediaIds() == null || request.getMediaIds().isEmpty()) {
            return Result.error(ResponseCode.BAD_REQUEST);
        }
        MediaAuditResult result = mediaService.rejectMedia(request.getMediaIds(), principal.getUserId());
        // 根据失败列表决定返回消息
        if (result.getFailedItems() == null || result.getFailedItems().isEmpty()) {
            return Result.success("审核驳回成功", result);
        } else {
            return Result.success("部分审核驳回失败", result);
        }
    }

    /**
     * 查询待审核媒体列表（仅管理员或作者可访问）
     *
     * GET /api/media/audit/pending?page=1&size=10
     */
    @GetMapping("/audit/pending")
    public Result<IMediaVisibleService.MediaPageResult> listPendingMedia(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "10") Integer size
    ) {
        if (principal == null) {
            return Result.error(ResponseCode.UNAUTHORIZED);
        }
        IMediaVisibleService.MediaPageResult result = mediaService.listPendingMedia(page, size, principal.getUserId());
        return Result.success("查询成功", result);
    }

    private List<Long> parseVisibleUserIds(String visibleUserIdsJson) {
        // visibleUserIds 必填，但可以为空数组 []
        if (visibleUserIdsJson == null || visibleUserIdsJson.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(visibleUserIdsJson, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            // 解析失败返回空列表（MVP阶段：前端应保证传正确格式）
            return Collections.emptyList();
        }
    }
}
