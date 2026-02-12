package com.dragons.core.controller;

import com.dragons.core.dto.ResponseCode;
import com.dragons.core.dto.Result;
import com.dragons.core.security.JwtPrincipal;
import com.dragons.core.service.IMediaVisibleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * <p>
 * 媒体资源可见权限表 前端控制器
 * </p>
 *
 * @author aice
 * @since 2026-01-17
 */
@RestController
@RequestMapping("/api/mediaVisible")
public class MediaVisibleController {

    private final IMediaVisibleService mediaVisibleService;

    @Autowired
    public MediaVisibleController(IMediaVisibleService mediaVisibleService) {
        this.mediaVisibleService = mediaVisibleService;
    }

    /**
     * 获取媒体列表（按专区筛选）
     * 支持游客模式：未登录也可访问，无需请求头。currentUserId=0 为公共区，传成员ID 为专区。
     *
     * Query:
     * - page: 页码（默认1）
     * - size: 每页数量（默认10）
     * - category: 可选，0=图片；1=视频
     * - currentUserId: 专区ID（0=公共区；其他=成员专区ID）
     */
    @GetMapping("/list")
    public Result<IMediaVisibleService.MediaPageResult> list(
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "10") Integer size,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "currentUserId", required = false, defaultValue = "0") Long zoneUserId
    ) {
        Byte categoryValue = parseCategory(category);
        if (categoryValue == null && category != null && !category.trim().isEmpty()) {
            return Result.error(ResponseCode.BAD_REQUEST);
        }
        IMediaVisibleService.MediaPageResult result = mediaVisibleService.listMedia(page, size, categoryValue, zoneUserId);
        return Result.success("查询成功", result);
    }

    /**
     * 获取“我的上传”列表（管理用）
     *
     * Query:
     * - page: 页码（默认1）
     * - size: 每页数量（默认10，最大100）
     * - category: 可选，0=图片；1=视频；不传=全展示
     */
    @GetMapping("/my/list")
    public Result<IMediaVisibleService.MyUploadPageResult> myList(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "10") Integer size,
            @RequestParam(value = "category", required = false) String category
    ) {
        if (principal == null) {
            return Result.error(ResponseCode.UNAUTHORIZED);
        }
        Byte categoryValue = parseCategory(category);
        if (categoryValue == null && category != null && !category.trim().isEmpty()) {
            return Result.error(ResponseCode.BAD_REQUEST);
        }
        IMediaVisibleService.MyUploadPageResult result = mediaVisibleService.listMyUpload(page, size, categoryValue, principal.getUserId());
        return Result.success("查询成功", result);
    }

    /**
     * 根据媒体ID查询该媒体属于哪些成员专区
     *
     * GET /api/mediaVisible/{mediaId}/zones
     * 支持游客模式：未登录也可访问，无需请求头
     */
    @GetMapping("/{mediaId}/zones")
    public Result<List<Long>> getVisibleZonesByMediaId(@PathVariable("mediaId") Long mediaId) {
        if (mediaId == null) {
            return Result.error(ResponseCode.BAD_REQUEST);
        }
        List<Long> visibleUserIds = mediaVisibleService.getVisibleUserIdsByMediaId(mediaId);
        return Result.success("查询成功", visibleUserIds);
    }

    /**
     * category 参数解析：
     * - null/空字符串：表示“默认全展示”
     * - "0"/"1"：分别表示图片/视频
     */
    private Byte parseCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            return null;
        }
        try {
            byte v = Byte.parseByte(category.trim());
            if (v == 0 || v == 1) {
                return v;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    // 登录用户从 @AuthenticationPrincipal 注入
}
