package com.dragons.core.dao;

import com.dragons.core.entity.TreeHole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * <p>
 * 树洞表 Mapper 接口
 * </p>
 *
 * @author aice
 * @since 2026-01-17
 */
public interface TreeHoleMapper extends BaseMapper<TreeHole> {

    /**
     * 按 owner_id 查询树洞并加行锁（FOR UPDATE），需在调用方事务内执行，用于投递防刷并发控制。
     */
    @Select("SELECT * FROM tree_hole WHERE owner_id = #{ownerId} LIMIT 1 FOR UPDATE")
    TreeHole selectByOwnerIdForUpdate(@Param("ownerId") Long ownerId);
}
