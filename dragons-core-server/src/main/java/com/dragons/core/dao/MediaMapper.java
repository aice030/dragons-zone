package com.dragons.core.dao;

import com.dragons.core.entity.Media;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 媒体资源表 Mapper 接口
 * </p>
 *
 * @author aice
 * @since 2026-01-17
 */
public interface MediaMapper extends BaseMapper<Media> {

    /**
     * 专区列表总数：INNER JOIN media_visible，按 user_id 与 state、可选 category 过滤
     */
    long countMediaInZone(@Param("zoneUserId") long zoneUserId, @Param("category") Byte category);

    /**
     * 专区列表分页：INNER JOIN media_visible，按 update_time DESC, id DESC 排序
     */
    List<Media> listMediaInZone(@Param("zoneUserId") long zoneUserId, @Param("category") Byte category,
                                @Param("offset") long offset, @Param("limit") int limit);
}
