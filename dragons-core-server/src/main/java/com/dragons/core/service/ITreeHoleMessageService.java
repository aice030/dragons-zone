package com.dragons.core.service;

import com.dragons.core.dto.TreeHoleMessagePageResult;
import com.dragons.core.entity.TreeHoleMessage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 树洞信息内容表 服务类
 * </p>
 *
 * @author aice
 * @since 2026-01-17
 */
public interface ITreeHoleMessageService extends IService<TreeHoleMessage> {

    /**
     * 向树洞投递留言或回复（同一接口）
     *
     * @param ownerId 树洞主人用户ID
     * @param senderUserId 投递者用户ID（从JWT获取）
     * @param content 留言内容
     * @param rootMessageId 为空=用户投递新消息；非空=主人回复该条消息（仅支持一次回复）
     * @return 新留言ID
     */
    Long sendMessage(Long ownerId, Long senderUserId, String content, Long rootMessageId);

    /**
     * 获取树洞留言列表（树洞正常消息展示）
     *
     * @param ownerId 树洞主人用户ID
     * @param viewerUserId 当前查看者用户ID（从JWT获取）
     * @param page 页码（从1开始）
     * @param size 每页数量
     */
    TreeHoleMessagePageResult listMessages(Long ownerId, Long viewerUserId, Integer page, Integer size);

    /**
     * 树洞主人将留言标记为已读
     *
     * @param messageId 留言ID
     * @param ownerUserId 当前登录用户ID（必须是树洞主人）
     */
    void markMessageRead(Long messageId, Long ownerUserId);

    /**
     * 树洞主人删除留言（全局删除）
     *
     * @param messageId 留言ID
     * @param ownerUserId 当前登录用户ID（必须是树洞主人）
     */
    void deleteMessageByOwner(Long messageId, Long ownerUserId);

    /**
     * 发送者删除留言（仅对发送者不可见）
     *
     * @param messageId 留言ID
     * @param senderUserId 当前登录用户ID（必须是发送者）
     */
    void deleteMessageBySender(Long messageId, Long senderUserId);
}
