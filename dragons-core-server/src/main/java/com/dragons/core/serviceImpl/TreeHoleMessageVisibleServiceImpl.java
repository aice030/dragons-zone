package com.dragons.core.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dragons.core.dao.TreeHoleMessageVisibleMapper;
import com.dragons.core.dto.ResponseCode;
import com.dragons.core.dto.TreeHoleMessagePageResult;
import com.dragons.core.entity.TreeHole;
import com.dragons.core.entity.TreeHoleMessage;
import com.dragons.core.entity.TreeHoleMessageVisible;
import com.dragons.core.entity.User;
import com.dragons.core.exception.BusinessException;
import com.dragons.core.service.ITreeHoleBlacklistService;
import com.dragons.core.service.ITreeHoleMessageService;
import com.dragons.core.service.ITreeHoleMessageVisibleService;
import com.dragons.core.service.ITreeHoleService;
import com.dragons.core.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 树洞消息可见权限表 服务实现类
 *
 * 说明：承接树洞消息分享区（收件箱）展示能力；listSharedMessages 通过联表查询返回其他树洞主人分享给当前主人的留言列表。
 *
 * @author aice
 * @since 2026-01-21
 */
@Slf4j
@Service
public class TreeHoleMessageVisibleServiceImpl
        extends ServiceImpl<TreeHoleMessageVisibleMapper, TreeHoleMessageVisible>
        implements ITreeHoleMessageVisibleService {

    private static final int WRITE_MAX_RETRIES = 3;

    private final ITreeHoleService treeHoleService;
    private final ITreeHoleMessageService treeHoleMessageService;
    private final IUserService userService;
    private final ITreeHoleBlacklistService treeHoleBlacklistService;

    @Autowired
    public TreeHoleMessageVisibleServiceImpl(ITreeHoleService treeHoleService,
                                             ITreeHoleMessageService treeHoleMessageService,
                                             IUserService userService,
                                             ITreeHoleBlacklistService treeHoleBlacklistService) {
        this.treeHoleService = treeHoleService;
        this.treeHoleMessageService = treeHoleMessageService;
        this.userService = userService;
        this.treeHoleBlacklistService = treeHoleBlacklistService;
    }

    @Override
    public TreeHoleMessagePageResult listSharedMessages(Long ownerId, Integer page, Integer size) {
        // 参数校验
        if (ownerId == null || ownerId <= 0) {
            log.warn("listSharedMessages invalid params ownerId={}", ownerId);
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        int pageNum = (page == null || page <= 0) ? 1 : page;
        int pageSize = (size == null || size <= 0) ? 10 : Math.min(size, 100);

        long offset = (long) (pageNum - 1) * pageSize;
        long total = baseMapper.countSharedMessages(ownerId);
        List<TreeHoleMessagePageResult.TreeHoleMessageItem> list =
                baseMapper.selectSharedMessageItems(ownerId, offset, pageSize);

        log.info("listSharedMessages ownerId={} page={} size={} total={}", ownerId, pageNum, pageSize, total);
        return new TreeHoleMessagePageResult(total, list);
    }

    @Override
    public void shareMessage(Long ownerId, Long messageId, List<Long> targetOwnerIds, Long currentUserId) {
        // 参数校验
        if (ownerId == null || ownerId <= 0 || messageId == null
                || targetOwnerIds == null || currentUserId == null) {
            log.warn("shareMessage invalid params ownerId={} messageId={} targetOwnerIds={} currentUserId={}", ownerId, messageId, targetOwnerIds, currentUserId);
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        // 必须是树洞拥有者才能使用分享功能（只能分享自己树洞下的留言）
        if (!ownerId.equals(currentUserId)) {
            log.warn("shareMessage denied ownerId={} currentUserId={} reason=not_owner", ownerId, currentUserId);
            throw new BusinessException(ResponseCode.FORBIDDEN);
        }

        // 确认分享者的树洞中有该条消息，且未删除（state != 2），并取出留言实体以获取 senderId
        LambdaQueryWrapper<TreeHoleMessage> treeHoleMessageWrapper = new LambdaQueryWrapper<>();
        treeHoleMessageWrapper.eq(TreeHoleMessage::getId, messageId)
                .eq(TreeHoleMessage::getTreeHoleOwnerId, ownerId)
                .and(w -> w.isNull(TreeHoleMessage::getState).or().ne(TreeHoleMessage::getState, (byte) 2));
        TreeHoleMessage message = treeHoleMessageService.getOne(treeHoleMessageWrapper);
        if (message == null) {
            log.warn("shareMessage denied ownerId={} messageId={} reason=message_not_found_or_deleted", ownerId, messageId);
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        // 若树洞主人已拉黑该留言的投递用户，则不允许分享
        Long senderId = message.getSenderId();
        if (senderId != null && treeHoleBlacklistService.isBlocked(ownerId, senderId)) {
            log.warn("shareMessage denied ownerId={} messageId={} senderId={} reason=sender_blocked", ownerId, messageId, senderId);
            throw new BusinessException(ResponseCode.TREE_HOLE_SHARE_SENDER_BLOCKED);
        }

        // 对接收方 ID 去重，避免同一用户重复处理与重复写入
        List<Long> distinctTargets = targetOwnerIds.stream().distinct().collect(Collectors.toList());

        // 记录分享失败的目标（无树洞或写库失败），全部处理完后若有失败则抛异常
        List<Long> failedOwnerIds = new ArrayList<>();
        for (Long targetOwnerId : distinctTargets) {
            if (targetOwnerId == null || targetOwnerId.equals(currentUserId)) {
                continue; // 不分享给自己，跳过
            }
            // 判断被分享者是否是树洞拥有者（数据库存在其树洞）
            LambdaQueryWrapper<TreeHole> treeHoleWrapper = new LambdaQueryWrapper<>();
            treeHoleWrapper.eq(TreeHole::getOwnerId, targetOwnerId);
            if (treeHoleService.getOne(treeHoleWrapper) == null) {
                failedOwnerIds.add(targetOwnerId);
                continue;
            }
            // 已分享过则静默跳过（幂等）
            LambdaQueryWrapper<TreeHoleMessageVisible> messageWrapper = new LambdaQueryWrapper<>();
            messageWrapper.eq(TreeHoleMessageVisible::getOwnerId, targetOwnerId)
                    .eq(TreeHoleMessageVisible::getMessageId, messageId);
            if (this.getOne(messageWrapper) != null) {
                continue;
            }
            // 写入新记录
            TreeHoleMessageVisible treeHoleMessageVisible = new TreeHoleMessageVisible();
            treeHoleMessageVisible.setMessageId(messageId);
            treeHoleMessageVisible.setOwnerId(targetOwnerId);
            treeHoleMessageVisible.setSharedByUserId(currentUserId);
            if (!saveWithRetry(treeHoleMessageVisible)) {
                log.error("shareMessage db insert failed messageId={} targetOwnerId={}", messageId, targetOwnerId);
                failedOwnerIds.add(targetOwnerId);
            }
        }

        if (!failedOwnerIds.isEmpty()) {
            log.warn("treehole share partial fail messageId={} targetCount={} failedCount={}", messageId, distinctTargets.size(), failedOwnerIds.size());
            List<User> failedUsers = userService.listByIds(failedOwnerIds);
            Map<Long, User> userMap = failedUsers.stream().collect(Collectors.toMap(User::getId, u -> u));
            String nickNames = failedOwnerIds.stream()
                    .map(id -> {
                        User u = userMap.get(id);
                        return (u != null && StringUtils.hasText(u.getNickName())) ? u.getNickName() : "用户" + id;
                    })
                    .collect(Collectors.joining("、"));
            throw new BusinessException(ResponseCode.TREE_HOLE_SHARE_PARTIAL_FAIL, "失败，分享给" + nickNames + "失败");
        }
        log.info("treehole share success messageId={} targetCount={}", messageId, distinctTargets.size());
    }

    /** 写操作重试：最多 3 次，防止临时网络/锁冲突导致失败 */
    private boolean saveWithRetry(TreeHoleMessageVisible visible) {
        for (int i = 0; i < WRITE_MAX_RETRIES; i++) {
            try {
                if (this.save(visible)) {
                    return true;
                }
            } catch (Exception e) {
                if (i == WRITE_MAX_RETRIES - 1) {
                    log.error("saveWithRetry failed after {} retries messageId={} ownerId={}", WRITE_MAX_RETRIES, visible.getMessageId(), visible.getOwnerId(), e);
                }
            }
        }
        return false;
    }
}

