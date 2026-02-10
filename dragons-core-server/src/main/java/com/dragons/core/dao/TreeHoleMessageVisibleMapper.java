package com.dragons.core.dao;

import com.dragons.core.dto.TreeHoleMessagePageResult;
import com.dragons.core.entity.TreeHoleMessageVisible;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 树洞消息可见权限表 Mapper 接口
 * </p>
 *
 * @author aice
 * @since 2026-01-21
 */
public interface TreeHoleMessageVisibleMapper extends BaseMapper<TreeHoleMessageVisible> {

    /**
     * 联表查询：分享给当前主人的留言列表（仅未删除的留言），按可见记录 id 倒序分页
     */
    List<TreeHoleMessagePageResult.TreeHoleMessageItem> selectSharedMessageItems(
            @Param("ownerId") Long ownerId,
            @Param("offset") long offset,
            @Param("limit") int limit);

    /**
     * 联表统计：分享给当前主人的未删除留言总数
     */
    long countSharedMessages(@Param("ownerId") Long ownerId);
}

