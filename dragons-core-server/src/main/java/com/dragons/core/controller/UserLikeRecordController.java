package com.dragons.core.controller;

import com.dragons.core.dto.ResponseCode;
import com.dragons.core.dto.Result;
import com.dragons.core.security.JwtPrincipal;
import com.dragons.core.service.IUserLikeRecordService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 用户点赞记录 前端控制器
 * </p>
 *
 * @author aice
 * @since 2026-02-20
 */
@RestController
@RequestMapping("/api/userLikeRecord")
public class UserLikeRecordController {

    private final IUserLikeRecordService userLikeRecordService;

    public UserLikeRecordController(IUserLikeRecordService userLikeRecordService) {
        this.userLikeRecordService = userLikeRecordService;
    }

    /**
     * 查询当前用户是否已赞该媒体（需登录；仅 state=0 可查）
     * 先查 Redis bitmap，bit=1 直接返回已赞；否则查 DB，写回缓存后返回。
     *
     * GET /api/userLikeRecord/media/{mediaId}/status
     * Header: Authorization: Bearer <JWT>
     */
    @GetMapping("/media/{mediaId}/status")
    public Result<Boolean> getLikeStatus(
            @PathVariable("mediaId") Long mediaId,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        if (principal == null) {
            return Result.error(ResponseCode.UNAUTHORIZED);
        }
        boolean liked = userLikeRecordService.getLikeStatus(mediaId, principal.getUserId());
        return Result.success("查询成功", liked);
    }
}
