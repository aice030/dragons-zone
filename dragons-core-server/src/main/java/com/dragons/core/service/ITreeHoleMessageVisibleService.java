package com.dragons.core.service;

import com.dragons.core.dto.TreeHoleMessagePageResult;
import com.dragons.core.entity.TreeHoleMessageVisible;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 树洞消息可见权限表 服务类
 *
 * 说明：承接树洞留言“列表展示”类能力，保持与 Media / MediaVisible 的职责划分一致
 * （展示型列表统一归到 Visible 系列）。
 *
 * @author aice
 * @since 2026-01-21
 */
public interface ITreeHoleMessageVisibleService extends IService<TreeHoleMessageVisible> {

    /**
     * 获取“分享收件箱”列表：其他树洞主人分享给当前树洞主人的留言
     *
     * @param ownerId 当前树洞主人用户ID（接收方）
     * @param page 页码（从1开始）
     * @param size 每页数量
     * @return 分页列表
     */
    TreeHoleMessagePageResult listSharedMessages(Long ownerId, Integer page, Integer size);

    /**
     * 树洞主人将一条留言分享给多个树洞主人（写入 tree_hole_message_visible）
     *
     * @param ownerId 分享者树洞主人 ID（留言所属树洞的 owner，须等于 currentUserId）
     * @param messageId 被分享的留言 ID
     * @param targetOwnerIds 接收方树洞主人用户 ID 列表
     * @param currentUserId 当前登录用户 ID
     * 无返回值，失败时抛 BusinessException（如发件人被拉黑、部分用户无法接收）
     */
    void shareMessage(Long ownerId, Long messageId, List<Long> targetOwnerIds, Long currentUserId);
}

