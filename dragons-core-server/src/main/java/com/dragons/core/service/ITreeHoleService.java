package com.dragons.core.service;

import com.dragons.core.entity.TreeHole;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 树洞表 服务类
 * </p>
 *
 * @author aice
 * @since 2026-01-17
 */
public interface ITreeHoleService extends IService<TreeHole> {

    /**
     * 按 owner_id 查询树洞并加行锁（FOR UPDATE）。必须在调用方已开启的事务内调用，用于投递防刷时串行化 count+insert。
     */
    TreeHole getByOwnerIdForUpdate(Long ownerId);

    /**
     * 按 owner_id 查询树洞（不加锁，用于普通查询）
     *
     * @param ownerId 树洞主人用户ID
     * @return 树洞实体，如果不存在返回 null
     */
    TreeHole getByOwnerId(Long ownerId);

    /**
     * 树洞主人设置树洞状态（允许/禁止投递）
     *
     * @param ownerId 树洞主人用户ID
     * @param currentUserId 当前登录用户ID（必须与 ownerId 相同）
     * @param state 0=正常；2=禁止投递
     */
    void updateTreeHoleState(Long ownerId, Long currentUserId, Byte state);
}
