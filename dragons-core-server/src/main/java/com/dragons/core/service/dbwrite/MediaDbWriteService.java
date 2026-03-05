package com.dragons.core.service.dbwrite;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dragons.core.dao.MediaMapper;
import com.dragons.core.dao.MediaVisibleMapper;
import com.dragons.core.entity.Media;
import com.dragons.core.entity.MediaVisible;
import com.dragons.core.retry.DbWriteRetry;
import com.dragons.core.retry.DbWriteReturnedFalseException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 媒体写库服务。
 *
 * 说明：
 * - 只处理 media / media_visible 的数据库写入；
 * - 具体业务分支（是否继续流程、是否抛业务异常）由调用方决定。
 */
@Service
public class MediaDbWriteService {

    private final MediaMapper mediaMapper;
    private final MediaVisibleMapper mediaVisibleMapper;

    public MediaDbWriteService(MediaMapper mediaMapper, MediaVisibleMapper mediaVisibleMapper) {
        this.mediaMapper = mediaMapper;
        this.mediaVisibleMapper = mediaVisibleMapper;
    }

    /** 插入 media，按 2 次重试。 */
    @DbWriteRetry(maxAttempts = 2)
    public void insertMedia(Media media) {
        int rows = mediaMapper.insert(media);
        if (rows <= 0) {
            throw new DbWriteReturnedFalseException("media.insert");
        }
    }

    /** 更新 media，按 2 次重试。 */
    @DbWriteRetry(maxAttempts = 2)
    public void updateMediaById(Media media) {
        int rows = mediaMapper.updateById(media);
        if (rows <= 0) {
            throw new DbWriteReturnedFalseException("media.updateById");
        }
    }

    /** 更新 media 状态等关键字段，按 3 次重试。 */
    @DbWriteRetry(maxAttempts = 3)
    public void updateMediaById3(Media media) {
        int rows = mediaMapper.updateById(media);
        if (rows <= 0) {
            throw new DbWriteReturnedFalseException("media.updateById.3");
        }
    }

    /**
     * 删除指定 mediaId 的全部 media_visible。
     *
     * 约定：0 行删除也视为成功（幂等）。
     */
    @DbWriteRetry(maxAttempts = 3)
    public void deleteMediaVisibleByMediaId(Long mediaId) {
        mediaVisibleMapper.delete(
                new LambdaQueryWrapper<MediaVisible>()
                        .eq(MediaVisible::getMediaId, mediaId)
        );
    }

    /**
     * 批量写入 media_visible，按 2 次重试。
     *
     * 约定：
     * - zoneId 为 null 或 0 跳过；
     * - 列表为空时直接成功。
     */
    @DbWriteRetry(maxAttempts = 2)
    public void saveMediaVisible(Long mediaId, List<Long> visibleUserIds) {
        if (visibleUserIds == null || visibleUserIds.isEmpty()) {
            return;
        }
        for (Long zoneId : visibleUserIds) {
            if (zoneId == null || zoneId == 0L) {
                continue;
            }
            MediaVisible mv = new MediaVisible();
            mv.setMediaId(mediaId);
            mv.setUserId(zoneId);
            int rows = mediaVisibleMapper.insert(mv);
            if (rows <= 0) {
                throw new DbWriteReturnedFalseException("mediaVisible.insert");
            }
        }
    }
}
