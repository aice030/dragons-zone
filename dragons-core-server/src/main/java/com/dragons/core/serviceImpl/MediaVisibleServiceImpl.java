package com.dragons.core.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dragons.core.dto.MediaListCacheValue;
import com.dragons.core.entity.MediaVisible;
import com.dragons.core.dao.MediaVisibleMapper;
import com.dragons.core.entity.Media;
import com.dragons.core.dao.MediaMapper;
import com.dragons.core.dto.ResponseCode;
import com.dragons.core.exception.BusinessException;
import com.dragons.core.service.IMediaVisibleService;
import com.dragons.core.service.IMediaService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dragons.core.storage.StorageService;
import com.dragons.core.cache.MediaRedisCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 * 媒体资源可见权限表 服务实现类
 * </p>
 *
 * @author aice
 * @since 2026-01-17
 */
@Slf4j
@Service
public class MediaVisibleServiceImpl extends ServiceImpl<MediaVisibleMapper, MediaVisible> implements IMediaVisibleService {

    private final MediaMapper mediaMapper;
    private final StorageService storageService;
    private final MediaRedisCacheService mediaRedisCacheService;
    private final IMediaService mediaService;

    @Autowired
    public MediaVisibleServiceImpl(MediaMapper mediaMapper, StorageService storageService,
                                   MediaRedisCacheService mediaRedisCacheService,
                                   IMediaService mediaService) {
        this.mediaMapper = mediaMapper;
        this.storageService = storageService;
        this.mediaRedisCacheService = mediaRedisCacheService;
        this.mediaService = mediaService;
    }

    @Override
    public MediaPageResult listMedia(Integer page, Integer size, Byte category, Long zoneUserId) {
        // 若未传入分页信息，采用默认值
        int safePage = (page == null || page < 1) ? 1 : page;
        int safeSize = (size == null || size < 1) ? 10 : size;
        if (safeSize > 100) {
            safeSize = 100;
        }
        // 先确认是成员专区展示（zoneUserId不为null），还是在公共区全量展示（zoneUserId为null）
        Long zoneUserIdForCache = zoneUserId == null ? 0L : zoneUserId;

        // 参数校验：传入的 category 是否合法
        if (category != null && category != 0 && category != 1) {
            log.warn("listMedia invalid category category={} zoneUserId={}", category, zoneUserId);
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        // 先查缓存：获取媒体列表（包含total和mediaIds）
        MediaListCacheValue cachedList = mediaRedisCacheService.getMediaList(zoneUserIdForCache, category, safePage, safeSize);
        
        List<Media> records;
        long total;

        // 先处理缓存命中的列表
        if (cachedList != null && cachedList.getMediaIds() != null && !cachedList.getMediaIds().isEmpty()) {
            // 缓存命中：批量从 media:core 获取数据
            List<Long> cachedMediaIds = cachedList.getMediaIds();
            log.info("listMedia cache hit zoneUserId={} category={} page={} size={} total={} cachedIds={}", 
                    zoneUserIdForCache, category, safePage, safeSize, cachedList.getTotal(), cachedMediaIds.size());
            
            Map<Long, Media> cachedMediaMap = mediaRedisCacheService.batchGetMediaCore(cachedMediaIds);

            // 找出未命中的ID，后续从DB加载
            List<Long> missingIds = cachedMediaIds.stream()
                    .filter(id -> !cachedMediaMap.containsKey(id))
                    .collect(Collectors.toList());

            // 未命中ID列表不为空，从DB加载未命中的媒体数据
            if (!missingIds.isEmpty()) {
                List<Media> missingMedia = mediaService.listByIds(missingIds);
                if (missingMedia != null) {
                    for (Media media : missingMedia) {
                        // 仅 state=0 的媒体写入缓存
                        if (media.getState() != null && media.getState() == 0) {
                            cachedMediaMap.put(media.getId(), media);
                            // 写入 media:core 缓存
                            mediaRedisCacheService.putMediaCore(media.getId(), media);
                        }
                    }
                }
            }
            
            // 按原始ID列表顺序构建结果，保持排序
            // 确保只返回 state=0 的媒体（列表只显示已审核通过的媒体）
            records = new ArrayList<>();
            for (Long mediaId : cachedMediaIds) {
                Media media = cachedMediaMap.get(mediaId);
                if (media != null && media.getState() != null && media.getState() == 0) {
                    records.add(media);
                }
            }
            
            // 使用缓存中的 total（避免再次查询数据库）
            total = cachedList.getTotal() != null ? cachedList.getTotal() : 0L;
        } else {
            // 缓存未命中：查DB
            log.info("listMedia cache miss zoneUserId={} category={} page={} size={}", 
                    zoneUserIdForCache, category, safePage, safeSize);
            
            if (zoneUserIdForCache == 0L) {
                // 公共区：直接查media表，利用(state, category, update_time)索引获取特定类别的media(图片/视频)或(state, update_time)索引获取全部类型的media
                // 先统计记录数量，用于分页中统计记录总数
                LambdaQueryWrapper<Media> countWrapper = new LambdaQueryWrapper<>();
                countWrapper.eq(Media::getState, (byte) 0);
                if (category != null) {
                    countWrapper.eq(Media::getCategory, category);
                }
                total = mediaMapper.selectCount(countWrapper);

                // 获取media列表
                LambdaQueryWrapper<Media> listWrapper = new LambdaQueryWrapper<>();
                // 仅获取状态为正常、公开的media(state=0)
                listWrapper.eq(Media::getState, (byte) 0);
                if (category != null) {
                    listWrapper.eq(Media::getCategory, category);
                }
                // 分页
                int offset = (safePage - 1) * safeSize;
                listWrapper.orderByDesc(Media::getUpdateTime).orderByDesc(Media::getId);
                listWrapper.last("limit " + offset + "," + safeSize);
                records = mediaMapper.selectList(listWrapper);
            } else {
                // 专区：INNER JOIN media_visible，使用内连接查询，利用 (user_id, media_id) 与 media 主键，达到类似索引的效果，提高查询效率
                total = mediaMapper.countMediaInZone(zoneUserIdForCache, category);
                long offset = (long) (safePage - 1) * safeSize;
                records = mediaMapper.listMediaInZone(zoneUserIdForCache, category, offset, safeSize);
            }
            
            // 写入列表缓存（包含total和mediaIds）和 media:core 缓存
            if (records != null && !records.isEmpty()) {
                List<Long> mediaIds = records.stream()
                        .map(Media::getId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                
                // 写入列表缓存（包含total和mediaIds）
                mediaRedisCacheService.putMediaList(zoneUserIdForCache, category, safePage, safeSize, total, mediaIds);
                
                // 批量写入 media:core 缓存（仅 state=0）
                // 注意：putMediaCore 内部已有异常处理，无需外层 try-catch
                for (Media media : records) {
                    if (media.getState() != null && media.getState() == 0) {
                        mediaRedisCacheService.putMediaCore(media.getId(), media);
                    }
                }
            }
        }

        // 构建返回结果
        List<MediaListItem> list = new ArrayList<>();
        if (records != null) {
            for (Media m : records) {
                String coverPath = m.getCoverPath();
                // 优先使用缓存中的 coverUrl，如果不存在则重新生成
                String coverUrl = m.getCoverUrl();
                if (coverUrl == null || coverUrl.trim().isEmpty()) {
                    coverUrl = buildCoverPresignedUrl(coverPath);
                }
                list.add(new MediaListItem(
                        m.getId(),
                        m.getCategory(),
                        m.getTitle(),
                        coverPath,
                        m.getUpdateTime(),
                        coverUrl
                ));
            }
        }
        log.info("listMedia zoneUserId={} page={} size={} category={} total={}", zoneUserIdForCache, safePage, safeSize, category, total);
        return new MediaPageResult(total, list);
    }

    @Override
    public MyUploadPageResult listMyUpload(Integer page, Integer size, Byte category, Long uploaderUserId) {
        if (uploaderUserId == null) {
            log.warn("listMyUpload denied reason=uploaderUserId_null");
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }

        int safePage = (page == null || page < 1) ? 1 : page;
        int safeSize = (size == null || size < 1) ? 10 : size;
        if (safeSize > 100) {
            safeSize = 100;
        }

        if (category != null && category != 0 && category != 1) {
            log.warn("listMyUpload invalid category category={} uploaderUserId={}", category, uploaderUserId);
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        // 先查缓存：获取媒体列表（包含total和mediaIds）
        MediaListCacheValue cachedList = mediaRedisCacheService.getMyUploadList(uploaderUserId, category, safePage, safeSize);
        
        List<Media> records;
        long total;

        if (cachedList != null && cachedList.getMediaIds() != null && !cachedList.getMediaIds().isEmpty()) {
            // 缓存命中：批量从 media:core 获取数据
            List<Long> cachedMediaIds = cachedList.getMediaIds();
            log.info("listMyUpload cache hit uploaderUserId={} category={} page={} size={} total={} cachedIds={}", 
                    uploaderUserId, category, safePage, safeSize, cachedList.getTotal(), cachedMediaIds.size());
            
            Map<Long, Media> cachedMediaMap = mediaRedisCacheService.batchGetMediaCore(cachedMediaIds);
            
            // 找出未命中的ID，从DB加载
            // 注意：listMyUpload 允许显示 state=6/7，但 media:core 只缓存 state=0
            // 所以未命中的ID可能是 state=6/7 的媒体，需要从DB加载
            List<Long> missingIds = cachedMediaIds.stream()
                    .filter(id -> !cachedMediaMap.containsKey(id))
                    .collect(Collectors.toList());
            
            if (!missingIds.isEmpty()) {
                // 从DB加载未命中的媒体数据
                List<Media> missingMedia = mediaService.listByIds(missingIds);
                if (missingMedia != null) {
                    for (Media media : missingMedia) {
                        // 排除已删除 state=5
                        if (media.getState() != null && media.getState() != 5) {
                            cachedMediaMap.put(media.getId(), media);
                            // 仅 state=0 的媒体写入 media:core 缓存
                            if (media.getState() == 0) {
                                mediaRedisCacheService.putMediaCore(media.getId(), media);
                            }
                        }
                    }
                }
            }
            
            // 按原始ID列表顺序构建结果，保持排序
            records = new ArrayList<>();
            for (Long mediaId : cachedMediaIds) {
                Media media = cachedMediaMap.get(mediaId);
                if (media != null && media.getState() != null && media.getState() != 5) {
                    records.add(media);
                }
            }
            
            // 使用缓存中的 total（避免再次查询数据库）
            total = cachedList.getTotal() != null ? cachedList.getTotal() : 0L;
        } else {
            // 缓存未命中：查DB
            log.info("listMyUpload cache miss uploaderUserId={} category={} page={} size={}", 
                    uploaderUserId, category, safePage, safeSize);
            
            // total（允许显示 state=6 待审核和 state=7 审核未通过，让上传者看到审核状态）
        LambdaQueryWrapper<Media> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(Media::getUploaderId, uploaderUserId);
        // 排除已删除 state=5
        countWrapper.ne(Media::getState, (byte) 5);
        if (category != null) {
            countWrapper.eq(Media::getCategory, category);
        }
            total = mediaMapper.selectCount(countWrapper);

            // list（允许显示 state=6 待审核和 state=7 审核未通过）
            LambdaQueryWrapper<Media> listWrapper = new LambdaQueryWrapper<>();
            listWrapper.eq(Media::getUploaderId, uploaderUserId);
            // 排除已删除 state=5
            listWrapper.ne(Media::getState, (byte) 5);
            if (category != null) {
                listWrapper.eq(Media::getCategory, category);
            }
            int offset = (safePage - 1) * safeSize;
            listWrapper.orderByDesc(Media::getUpdateTime).orderByDesc(Media::getId);
            listWrapper.last("limit " + offset + "," + safeSize);

            records = mediaMapper.selectList(listWrapper);
            
            // 写入列表缓存（包含total和mediaIds）和 media:core 缓存
            if (records != null && !records.isEmpty()) {
                List<Long> mediaIds = records.stream()
                        .map(Media::getId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                
                // 写入列表缓存（包含total和mediaIds，排除 state=5 的所有媒体ID）
                mediaRedisCacheService.putMyUploadList(uploaderUserId, category, safePage, safeSize, total, mediaIds);
                
                // 批量写入 media:core 缓存（仅 state=0）
                for (Media media : records) {
                    if (media.getState() != null && media.getState() == 0) {
                        mediaRedisCacheService.putMediaCore(media.getId(), media);
                    }
                }
            }
        }

        // 构建返回结果
        List<MyUploadListItem> list = new ArrayList<>();
        if (records != null) {
            for (Media m : records) {
                MyUploadListItem item = new MyUploadListItem(
                        m.getId(),
                        m.getCategory(),
                        m.getState(),
                        m.getTitle(),
                        m.getCoverPath(),
                        m.getUpdateTime()
                );
                // 生成封面预签名URL：用于“我的上传”列表缩略图展示（允许 state=6/7）
                item.coverUrl = buildCoverPresignedUrl(m.getCoverPath());
                list.add(item);
            }
        }

        log.info("listMyUpload uploaderUserId={} page={} size={} category={} total={}", uploaderUserId, safePage, safeSize, category, total);
        return new MyUploadPageResult(total, list);
    }

    /**
     * 为 coverPath 生成预签名URL（不存在/空则返回 null）
     */
    private String buildCoverPresignedUrl(String coverPath) {
        if (coverPath == null || coverPath.trim().isEmpty()) {
            return null;
        }
        try {
            if (!storageService.exists(coverPath)) {
                return null;
            }
            // 2 小时有效期，前端列表用足够
            return storageService.getPresignedUrl(coverPath, 7200);
        } catch (Exception e) {
            log.warn("buildCoverPresignedUrl error coverPath={}", coverPath, e);
            return null;
        }
    }


    /**
     * 获取当前media的标签（除公共区外，在哪些成员专区可访问到）
     */
    @Override
    public List<Long> getVisibleUserIdsByMediaId(Long mediaId) {
        if (mediaId == null) {
            log.info("getVisibleUserIdsByMediaId mediaId=null returning empty");
            return new ArrayList<>();
        }
        LambdaQueryWrapper<MediaVisible> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MediaVisible::getMediaId, mediaId);
        List<MediaVisible> visibleList = this.list(wrapper);
        List<Long> userIds = new ArrayList<>();
        if (visibleList != null) {
            for (MediaVisible mv : visibleList) {
                if (mv.getUserId() != null) {
                    userIds.add(mv.getUserId());
                }
            }
        }
        log.info("getVisibleUserIdsByMediaId mediaId={} zoneCount={}", mediaId, userIds.size());
        return userIds;
    }
}
