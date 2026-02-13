package com.dragons.core.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dragons.core.dto.ResponseCode;
import com.dragons.core.dto.TreeHoleMessagePageResult;
import com.dragons.core.entity.TreeHole;
import com.dragons.core.entity.TreeHoleBlacklist;
import com.dragons.core.entity.TreeHoleMessage;
import com.dragons.core.dao.TreeHoleMessageMapper;
import com.dragons.core.exception.BusinessException;
import com.dragons.core.service.ITreeHoleBlacklistService;
import com.dragons.core.service.ITreeHoleMessageService;
import com.dragons.core.service.ITreeHoleService;
import com.dragons.core.service.IUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 树洞信息内容表 服务实现类
 * </p>
 *
 * @author aice
 * @since 2026-01-17
 */
@Service
public class TreeHoleMessageServiceImpl extends ServiceImpl<TreeHoleMessageMapper, TreeHoleMessage> implements ITreeHoleMessageService {

    private static final byte STATE_UNREAD = 0;
    private static final byte STATE_READ = 1;
    private static final byte STATE_DELETED = 2;
    /** 已回复：主人回复后，被回复的根消息置为此状态 */
    private static final byte STATE_REPLIED = 3;
    private static final byte TREE_HOLE_STATE_FORBIDDEN = 2;
    private static final byte BLACKLIST_STATE_ACTIVE = 0;
    private static final int WRITE_MAX_RETRIES = 3;

    @Autowired
    private ITreeHoleService treeHoleService;
    @Autowired
    private ITreeHoleBlacklistService treeHoleBlacklistService;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private IUserService userService;

    @Override
    public Long sendMessage(Long ownerId, Long senderUserId, String content, Long rootMessageId) {
        // 参数校验
        if (ownerId == null || ownerId <= 0 || senderUserId == null || senderUserId <= 0) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        // 确认树洞存在
        TreeHole treeHole = treeHoleService.getOne(
                new LambdaQueryWrapper<TreeHole>().eq(TreeHole::getOwnerId, ownerId));
        if (treeHole == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // rootMessageId为空，说明是用户投递新的树洞留言
        if (rootMessageId == null) {
            return doDeliverNewMessage(treeHole, senderUserId, content);
        }

        // rootMessageId不为空，则是树洞主人回复（仅支持一次回复）
        // 先确定回复者是树洞主人
        if (!senderUserId.equals(ownerId)) {
            throw new BusinessException(ResponseCode.FORBIDDEN);
        }
        // 根据rootMessageId获取被回复的树洞留言
        TreeHoleMessage rootMessage = this.getOne(
                new LambdaQueryWrapper<TreeHoleMessage>()
                        .eq(TreeHoleMessage::getId, rootMessageId)
                        .eq(TreeHoleMessage::getTreeHoleOwnerId, ownerId));
        // 确认被回复留言存在，且没有被删除
        if (rootMessage == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        // 确认被回复留言的状态，若已被树洞所有者删除，则无法回复
        if (rootMessage.getState() != null && rootMessage.getState() == STATE_DELETED) {
            throw new BusinessException(ResponseCode.TREE_HOLE_MESSAGE_DELETED);
        }
        // 若该留言已有其他回复，也无法回复（树洞不是讨论区，仅支持一次回复）
        if (rootMessage.getReplyMessageId() != null) {
            throw new BusinessException(ResponseCode.TREE_HOLE_REPLY_ALREADY_EXISTS);
        }
        return doReplyMessage(treeHole, rootMessage, ownerId, content);
    }

    /**
     * 用户投递新消息：在事务内对树洞行加锁（FOR UPDATE），再做了树洞状态/黑名单/防刷校验并插入，避免并发下防刷失效。
     */
    private Long doDeliverNewMessage(TreeHole treeHole, Long senderUserId, String content) {
        Long ownerId = treeHole.getOwnerId();
        return transactionTemplate.execute(status -> {
            // 1) 对树洞行加锁，同一树洞的投递需要排队执行，保证 count+insert 原子性
            TreeHole locked = treeHoleService.getByOwnerIdForUpdate(ownerId);
            if (locked == null) {
                throw new BusinessException(ResponseCode.NOT_FOUND);
            }
            // 2) 确认树洞状态
            if (locked.getState() != null && locked.getState() == TREE_HOLE_STATE_FORBIDDEN) {
                throw new BusinessException(ResponseCode.TREE_HOLE_CLOSED);
            }
            // 3) 黑名单校验
            long blackCount = treeHoleBlacklistService.count(
                    new LambdaQueryWrapper<TreeHoleBlacklist>()
                            .eq(TreeHoleBlacklist::getOwnerId, ownerId)
                            .eq(TreeHoleBlacklist::getBlockedUserId, senderUserId)
                            .eq(TreeHoleBlacklist::getState, BLACKLIST_STATE_ACTIVE));
            if (blackCount > 0) {
                throw new BusinessException(ResponseCode.TREE_HOLE_SENDER_BLOCKED);
            }
            // 4) 防刷：同一发送者在该树洞下最多一条未读
            long unreadFromSender = this.count(
                    new LambdaQueryWrapper<TreeHoleMessage>()
                            .eq(TreeHoleMessage::getTreeHoleOwnerId, ownerId)
                            .eq(TreeHoleMessage::getSenderId, senderUserId)
                            .eq(TreeHoleMessage::getState, STATE_UNREAD));
            if (unreadFromSender > 0) {
                throw new BusinessException(ResponseCode.TREE_HOLE_UNREAD_EXISTS);
            }
            // 5) 插入新留言
            TreeHoleMessage message = new TreeHoleMessage();
            message.setTreeHoleId(locked.getId());
            message.setTreeHoleOwnerId(ownerId);
            message.setSenderId(senderUserId);
            message.setSenderDeleted((byte) 0);
            message.setContent(content.trim());
            message.setState(STATE_UNREAD);
            message.setUpdateTime(LocalDateTime.now());
            boolean saved = this.save(message);
            if (!saved) {
                throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
            }
            return message.getId();
        });
    }

    /**
     * 树洞主人回复：在事务内插入回复并更新根消息（reply_message_id、已回复、update_time），保证原子性。
     * 回复消息与被回复的根消息均置为 state=3（已回复）。
     * 如果根消息的 sender_deleted=1（被sender删除），则重置为0，使消息重新对sender可见。
     */
    private Long doReplyMessage(TreeHole treeHole, TreeHoleMessage rootMessage, Long ownerId, String content) {
        Long rootMessageId = rootMessage.getId();
        // 开启编程式事务
        return transactionTemplate.execute(status -> {
            TreeHoleMessage reply = new TreeHoleMessage();
            reply.setTreeHoleId(treeHole.getId());
            reply.setTreeHoleOwnerId(ownerId);
            reply.setSenderId(ownerId);
            reply.setSenderDeleted((byte) 0);
            reply.setRootMessageId(rootMessageId);
            reply.setContent(content.trim());
            reply.setState(STATE_REPLIED);
            reply.setUpdateTime(LocalDateTime.now());
            boolean replySaved = this.save(reply);
            if (!replySaved) {
                throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
            }
            rootMessage.setReplyMessageId(reply.getId());
            rootMessage.setState(STATE_REPLIED);
            rootMessage.setUpdateTime(LocalDateTime.now());
            // 如果根消息被sender删除（sender_deleted=1），则重置为0，使消息重新对sender可见
            Byte senderDeleted = rootMessage.getSenderDeleted();
            if (senderDeleted != null && senderDeleted == 1) {
                rootMessage.setSenderDeleted((byte) 0);
            }
            boolean rootUpdated = this.updateById(rootMessage);
            if (!rootUpdated) {
                throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
            }
            return reply.getId();
        });
    }

    @Override
    public TreeHoleMessagePageResult listMessages(Long ownerId, Long viewerUserId, Integer page, Integer size) {
        // 参数校验
        if (ownerId == null || ownerId <= 0 || viewerUserId == null) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        // 若未传入分页信息，采用默认值
        int pageNum = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 10 : Math.min(size, 100);
        LambdaQueryWrapper<TreeHoleMessage> treeholeWrapper = new LambdaQueryWrapper<>();
        // 基础筛选：所属树洞、未被主人删除
        treeholeWrapper.eq(TreeHoleMessage::getTreeHoleOwnerId, ownerId).ne(TreeHoleMessage::getState, STATE_DELETED);
        if (viewerUserId.equals(ownerId)) {
            // 树洞主人：仅显示根留言（不包含自己的回复）
            treeholeWrapper.isNull(TreeHoleMessage::getRootMessageId);
        } else {
            // 非主人：① 自己投递且未被自己删除的根留言；② 主人回复自己的且未被自己删除的留言（root_message_id 对应消息的 sender_id 为当前用户）
            treeholeWrapper.and(w -> w
                    .and(w1 -> w1.isNull(TreeHoleMessage::getRootMessageId)
                            .eq(TreeHoleMessage::getSenderId, viewerUserId)
                            .and(w2 -> w2.isNull(TreeHoleMessage::getSenderDeleted).or().ne(TreeHoleMessage::getSenderDeleted, 1)))
                    .or()
                    .apply("root_message_id IS NOT NULL AND (sender_deleted IS NULL OR sender_deleted != 1) AND root_message_id IN (SELECT id FROM tree_hole_message WHERE tree_hole_owner_id = {0} AND sender_id = {1} AND root_message_id IS NULL AND (sender_deleted IS NULL OR sender_deleted != 1))", ownerId, viewerUserId));
        }
        // 未读在前、已读在后；同状态下按最近更新时间升序（越早越靠前）
        treeholeWrapper.orderByAsc(TreeHoleMessage::getState).orderByAsc(TreeHoleMessage::getUpdateTime);
        Page<TreeHoleMessage> pageParam = new Page<>(pageNum, pageSize);
        IPage<TreeHoleMessage> result = this.page(pageParam, treeholeWrapper);
        List<TreeHoleMessagePageResult.TreeHoleMessageItem> list = result.getRecords().stream()
                .map(m -> {
                    // 查询发送者的昵称
                    String senderNickName = userService.getNickNameById(m.getSenderId());
                    return new TreeHoleMessagePageResult.TreeHoleMessageItem(
                            m.getId(), m.getSenderId(), senderNickName, m.getContent(), m.getState(), m.getRootMessageId());
                })
                .collect(Collectors.toList());
        return new TreeHoleMessagePageResult(result.getTotal(), list);
    }

    @Override
    public void markMessageRead(Long messageId, Long ownerUserId) {
        // 标记已读就是将tree_hole_message的状态改为1
        updateOwnerMessageState(messageId, ownerUserId, STATE_READ);
    }

    @Override
    public void deleteMessageByOwner(Long messageId, Long ownerUserId) {
        // 将该留言及以其为根消息的回复一并置为 state=2（已删除）
        updateOwnerMessageState(messageId, ownerUserId, STATE_DELETED);
    }

    @Override
    public void deleteMessageBySender(Long messageId, Long senderUserId) {
        // 参数校验
        if (messageId == null || messageId <= 0 || senderUserId == null) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        // 仅允许消息发送者删除自己创建的留言
        TreeHoleMessage treeHoleMessage = this.getOne(
                new LambdaQueryWrapper<TreeHoleMessage>()
                        .eq(TreeHoleMessage::getId, messageId)
                        .eq(TreeHoleMessage::getSenderId, senderUserId)
        );

        if (treeHoleMessage == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        // 全局已删除：视为幂等成功
        Byte state = treeHoleMessage.getState();
        if (state != null && state == 2) {
            return;
        }
        // 重复删除不报错
        Byte senderDeleted = treeHoleMessage.getSenderDeleted();
        if (senderDeleted != null && senderDeleted == 1) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        long replyCount = this.count(new LambdaQueryWrapper<TreeHoleMessage>()
                .eq(TreeHoleMessage::getRootMessageId, messageId));
        if (replyCount > 0) {
            // 有回复：同一事务内先将回复的 sender_deleted 置为 1，再置根留言的 sender_deleted 为 1
            transactionTemplate.execute(status -> {
                LambdaUpdateWrapper<TreeHoleMessage> replyUpdateWrapper = new LambdaUpdateWrapper<>();
                replyUpdateWrapper.eq(TreeHoleMessage::getRootMessageId, messageId)
                        .set(TreeHoleMessage::getSenderDeleted, (byte) 1)
                        .set(TreeHoleMessage::getUpdateTime, now);
                this.update(replyUpdateWrapper);
                treeHoleMessage.setSenderDeleted((byte) 1);
                treeHoleMessage.setUpdateTime(now);
                boolean rootUpdated = this.updateById(treeHoleMessage);
                if (!rootUpdated) {
                    throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
                }
                return null;
            });
        } else {
            // 无回复：仅更新根留言
            treeHoleMessage.setSenderDeleted((byte) 1);
            treeHoleMessage.setUpdateTime(now);
            boolean updated = updateByIdWithRetry(treeHoleMessage);
            if (!updated) {
                throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
            }
        }
    }

    /**
     * 树洞主人更新留言 state（仅允许更新自己树洞下的留言）
     * 约定：
     * - targetState=1：标记已读（重复标记幂等；已删除不允许再标记已读）
     * - targetState=2：全局删除（重复删除幂等）；若有 root_message_id=messageId 的回复，一并置为已删除
     */
    private void updateOwnerMessageState(Long messageId, Long ownerUserId, byte targetState) {
        if (messageId == null || messageId <= 0 || ownerUserId == null) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        TreeHoleMessage treeHoleMessage = this.getOne(
                new LambdaQueryWrapper<TreeHoleMessage>()
                        .eq(TreeHoleMessage::getId, messageId)
                        .eq(TreeHoleMessage::getTreeHoleOwnerId, ownerUserId)
        );
        if (treeHoleMessage == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        Byte currentState = treeHoleMessage.getState();

        if (targetState == STATE_READ) {
            // 重复标记已读不报错
            if (currentState != null && currentState == STATE_READ) {
                return;
            }
            // 已删除的留言不允许再标记已读
            if (currentState != null && currentState == STATE_DELETED) {
                throw new BusinessException(ResponseCode.BAD_REQUEST);
            }
        } else if (targetState == STATE_DELETED) {
            // 重复删除不报错
            if (currentState != null && currentState == STATE_DELETED) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            long replyCount = this.count(new LambdaQueryWrapper<TreeHoleMessage>()
                    .eq(TreeHoleMessage::getRootMessageId, messageId));
            if (replyCount > 0) {
                // 有回复：同一事务内先删回复再删根，保证原子性
                transactionTemplate.execute(status -> {
                    LambdaUpdateWrapper<TreeHoleMessage> replyDeleteWrapper = new LambdaUpdateWrapper<>();
                    replyDeleteWrapper.eq(TreeHoleMessage::getRootMessageId, messageId)
                            .set(TreeHoleMessage::getState, STATE_DELETED)
                            .set(TreeHoleMessage::getUpdateTime, now);
                    this.update(replyDeleteWrapper);
                    treeHoleMessage.setState(STATE_DELETED);
                    treeHoleMessage.setUpdateTime(now);
                    boolean rootUpdated = this.updateById(treeHoleMessage);
                    if (!rootUpdated) {
                        throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
                    }
                    return null;
                });
            } else {
                // 无回复：仅更新根留言，无需事务
                treeHoleMessage.setState(STATE_DELETED);
                treeHoleMessage.setUpdateTime(now);
                boolean updated = updateByIdWithRetry(treeHoleMessage);
                if (!updated) {
                    throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
                }
            }
            return;
        } else {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        // 仅 STATE_READ 会执行到此：单条更新
        treeHoleMessage.setState(targetState);
        treeHoleMessage.setUpdateTime(LocalDateTime.now());
        boolean updated = updateByIdWithRetry(treeHoleMessage);
        if (!updated) {
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /** 写操作重试：最多 3 次，防止临时网络/锁冲突导致失败 */
    private boolean saveWithRetry(TreeHoleMessage message) {
        for (int i = 0; i < WRITE_MAX_RETRIES; i++) {
            try {
                if (this.save(message)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private boolean updateByIdWithRetry(TreeHoleMessage message) {
        for (int i = 0; i < WRITE_MAX_RETRIES; i++) {
            try {
                if (this.updateById(message)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }
}
