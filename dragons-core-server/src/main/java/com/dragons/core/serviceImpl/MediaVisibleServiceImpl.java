package com.dragons.core.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dragons.core.entity.MediaVisible;
import com.dragons.core.dao.MediaVisibleMapper;
import com.dragons.core.entity.Media;
import com.dragons.core.dao.MediaMapper;
import com.dragons.core.dto.ResponseCode;
import com.dragons.core.exception.BusinessException;
import com.dragons.core.service.IMediaVisibleService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 媒体资源可见权限表 服务实现类
 * </p>
 *
 * @author aice
 * @since 2026-01-17
 */
@Service
public class MediaVisibleServiceImpl extends ServiceImpl<MediaVisibleMapper, MediaVisible> implements IMediaVisibleService {

    private final MediaMapper mediaMapper;

    @Autowired
    public MediaVisibleServiceImpl(MediaMapper mediaMapper) {
        this.mediaMapper = mediaMapper;
    }

    @Override
    public MediaPageResult listMedia(Integer page, Integer size, Byte category, Long zoneUserId) {
        // 若未传入分页信息，采用默认值
        int safePage = (page == null || page < 1) ? 1 : page;
        int safeSize = (size == null || size < 1) ? 10 : size;
        if (safeSize > 100) {
            safeSize = 100;
        }
        long safeZoneUserId = (zoneUserId == null) ? 0L : zoneUserId;

        if (category != null && category != 0 && category != 1) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        long total;
        List<Media> records;

        if (safeZoneUserId == 0L) {
            // 公共区：直接查 media，利用 (state, category, update_time, id) 索引
            LambdaQueryWrapper<Media> countWrapper = new LambdaQueryWrapper<>();
            countWrapper.eq(Media::getState, (byte) 0);
            if (category != null) {
                countWrapper.eq(Media::getCategory, category);
            }
            total = mediaMapper.selectCount(countWrapper);

            LambdaQueryWrapper<Media> listWrapper = new LambdaQueryWrapper<>();
            listWrapper.eq(Media::getState, (byte) 0);
            if (category != null) {
                listWrapper.eq(Media::getCategory, category);
            }
            int offset = (safePage - 1) * safeSize;
            listWrapper.orderByDesc(Media::getUpdateTime).orderByDesc(Media::getId);
            listWrapper.last("limit " + offset + "," + safeSize);
            records = mediaMapper.selectList(listWrapper);
        } else {
            // 专区：INNER JOIN media_visible，利用 (user_id, media_id) 与 media 主键
            total = mediaMapper.countMediaInZone(safeZoneUserId, category);
            long offset = (long) (safePage - 1) * safeSize;
            records = mediaMapper.listMediaInZone(safeZoneUserId, category, offset, safeSize);
        }

        List<MediaListItem> list = new ArrayList<>();
        if (records != null) {
            for (Media m : records) {
                list.add(new MediaListItem(m.getId(), m.getCategory(), m.getTitle(), m.getCoverPath()));
            }
        }
        return new MediaPageResult(total, list);
    }

    @Override
    public MyUploadPageResult listMyUpload(Integer page, Integer size, Byte category, Long uploaderUserId) {
        if (uploaderUserId == null) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }

        int safePage = (page == null || page < 1) ? 1 : page;
        int safeSize = (size == null || size < 1) ? 10 : size;
        if (safeSize > 100) {
            safeSize = 100;
        }

        if (category != null && category != 0 && category != 1) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        // total（允许显示 state=6 待审核和 state=7 审核未通过，让上传者看到审核状态）
        LambdaQueryWrapper<Media> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(Media::getUploaderId, uploaderUserId);
        countWrapper.ne(Media::getState, (byte) 5); // 排除已删除 state=5
        if (category != null) {
            countWrapper.eq(Media::getCategory, category);
        }
        long total = mediaMapper.selectCount(countWrapper);

        // list（允许显示 state=6 待审核和 state=7 审核未通过）
        LambdaQueryWrapper<Media> listWrapper = new LambdaQueryWrapper<>();
        listWrapper.eq(Media::getUploaderId, uploaderUserId);
        listWrapper.ne(Media::getState, (byte) 5); // 排除已删除 state=5
        if (category != null) {
            listWrapper.eq(Media::getCategory, category);
        }
        int offset = (safePage - 1) * safeSize;
        listWrapper.orderByDesc(Media::getUpdateTime).orderByDesc(Media::getId);
        listWrapper.last("limit " + offset + "," + safeSize);

        List<Media> records = mediaMapper.selectList(listWrapper);
        List<MyUploadListItem> list = new ArrayList<>();
        if (records != null) {
            for (Media m : records) {
                list.add(new MyUploadListItem(m.getId(), m.getCategory(), m.getState(), m.getTitle(), m.getCoverPath()));
            }
        }

        return new MyUploadPageResult(total, list);
    }
}
