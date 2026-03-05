package com.dragons.core.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dragons.core.dto.ResponseCode;
import com.dragons.core.entity.TreeHole;
import com.dragons.core.dao.TreeHoleMapper;
import com.dragons.core.exception.BusinessException;
import com.dragons.core.service.ITreeHoleService;
import com.dragons.core.service.dbwrite.TreeHoleDbWriteService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 树洞表 服务实现类
 * </p>
 *
 * @author aice
 * @since 2026-01-17
 */
@Slf4j
@Service
public class TreeHoleServiceImpl extends ServiceImpl<TreeHoleMapper, TreeHole> implements ITreeHoleService {

    private final TreeHoleDbWriteService treeHoleDbWriteService;

    @Autowired
    public TreeHoleServiceImpl(TreeHoleDbWriteService treeHoleDbWriteService) {
        this.treeHoleDbWriteService = treeHoleDbWriteService;
    }

    @Override
    public void updateTreeHoleState(Long ownerId, Long currentUserId, Byte state) {
        // 1) 参数与登录态校验
        if (currentUserId == null) {
            log.warn("updateTreeHoleState denied ownerId={} reason=currentUserId_null", ownerId);
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }
        if (ownerId == null || state == null) {
            log.warn("updateTreeHoleState invalid params ownerId={} currentUserId={} state={}", ownerId, currentUserId, state);
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        // 2) 仅允许树洞主人本人修改自己的树洞状态
        if (!ownerId.equals(currentUserId)) {
            log.warn("updateTreeHoleState denied ownerId={} currentUserId={} reason=not_owner", ownerId, currentUserId);
            throw new BusinessException(ResponseCode.FORBIDDEN);
        }

        // 3) 仅允许设置 0=正常 或 2=禁止投递
        if (state != 0 && state != 2) {
            log.warn("updateTreeHoleState invalid state ownerId={} state={}", ownerId, state);
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        // 4) 按 owner_id 查询树洞（树洞数据是预置的）
        TreeHole treeHole = this.getOne(
                new LambdaQueryWrapper<TreeHole>().eq(TreeHole::getOwnerId, ownerId)
        );
        if (treeHole == null) {
            log.warn("updateTreeHoleState denied ownerId={} reason=treehole_not_found", ownerId);
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // 5) 更新状态（重试 3 次）
        treeHole.setState(state);
        try {
            // 只调用写库服务，重试由 @DbWriteRetry 切面负责
            treeHoleDbWriteService.updateById(treeHole);
        } catch (Exception e) {
            log.error("updateTreeHoleState failed ownerId={} state={} reason=db_update_failed", ownerId, state);
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
        log.info("treehole state updated ownerId={} state={}", ownerId, state);
    }

    @Override
    public TreeHole getByOwnerIdForUpdate(Long ownerId) {
        if (ownerId == null) {
            log.warn("getByOwnerIdForUpdate invalid params ownerId=null");
            return null;
        }
        TreeHole result = baseMapper.selectByOwnerIdForUpdate(ownerId);
        if (result == null) {
            log.warn("getByOwnerIdForUpdate treehole not found ownerId={}", ownerId);
        }
        return result;
    }

    @Override
    public TreeHole getByOwnerId(Long ownerId) {
        if (ownerId == null) {
            return null;
        }
        TreeHole result = this.getOne(
                new LambdaQueryWrapper<TreeHole>().eq(TreeHole::getOwnerId, ownerId)
        );
        if (result == null) {
            log.info("getByOwnerId treehole not found ownerId={}", ownerId);
        }
        return result;
    }
}
