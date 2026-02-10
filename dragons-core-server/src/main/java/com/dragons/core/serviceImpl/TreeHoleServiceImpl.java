package com.dragons.core.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dragons.core.dto.ResponseCode;
import com.dragons.core.entity.TreeHole;
import com.dragons.core.dao.TreeHoleMapper;
import com.dragons.core.exception.BusinessException;
import com.dragons.core.service.ITreeHoleService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 树洞表 服务实现类
 * </p>
 *
 * @author aice
 * @since 2026-01-17
 */
@Service
public class TreeHoleServiceImpl extends ServiceImpl<TreeHoleMapper, TreeHole> implements ITreeHoleService {

    private static final int WRITE_MAX_RETRIES = 3;

    @Override
    public void updateTreeHoleState(Long ownerId, Long currentUserId, Byte state) {
        // 1) 参数与登录态校验
        if (currentUserId == null) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }
        if (ownerId == null || state == null) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        // 2) 仅允许树洞主人本人修改自己的树洞状态
        if (!ownerId.equals(currentUserId)) {
            throw new BusinessException(ResponseCode.FORBIDDEN);
        }

        // 3) 仅允许设置 0=正常 或 2=禁止投递
        if (state != 0 && state != 2) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        // 4) 按 owner_id 查询树洞（树洞数据是预置的）
        TreeHole treeHole = this.getOne(
                new LambdaQueryWrapper<TreeHole>().eq(TreeHole::getOwnerId, ownerId)
        );
        if (treeHole == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // 5) 更新状态（重试 3 次）
        treeHole.setState(state);
        boolean updated = updateByIdWithRetry(treeHole);
        if (!updated) {
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public TreeHole getByOwnerIdForUpdate(Long ownerId) {
        if (ownerId == null) {
            return null;
        }
        return baseMapper.selectByOwnerIdForUpdate(ownerId);
    }

    /** 写操作重试：最多 3 次，防止临时网络/锁冲突导致失败 */
    private boolean updateByIdWithRetry(TreeHole treeHole) {
        for (int i = 0; i < WRITE_MAX_RETRIES; i++) {
            try {
                if (this.updateById(treeHole)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }
}
