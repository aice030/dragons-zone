package com.dragons.core.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dragons.core.dto.MediaListCacheValue;
import com.dragons.core.entity.MediaVisible;
import com.dragons.core.dao.MediaVisibleMapper;
import com.dragons.core.entity.Media;
import com.dragons.core.dao.MediaMapper;
import com.dragons.core.dto.ResponseCode;
import com.dragons.core.exception.BusinessException;
import com.dragons.core.lock.RedisLockFacade;
import com.dragons.core.service.IMediaVisibleService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dragons.core.storage.StorageService;
import com.dragons.core.cache.RedisCacheMediaCoreService;
import com.dragons.core.cache.RedisCacheMediaLikeService;
import com.dragons.core.cache.RedisCacheMediaListService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
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
    private final RedisCacheMediaCoreService redisCacheMediaCoreService;
    private final RedisCacheMediaListService redisCacheMediaListService;
    private final RedisCacheMediaLikeService redisCacheMediaLikeService;
    private final RedisLockFacade redisLockFacade;

    @Autowired
    public MediaVisibleServiceImpl(MediaMapper mediaMapper, StorageService storageService,
                                   RedisCacheMediaCoreService redisCacheMediaCoreService,
                                   RedisCacheMediaListService redisCacheMediaListService,
                                   RedisCacheMediaLikeService redisCacheMediaLikeService,
                                   RedisLockFacade redisLockFacade) {
        this.mediaMapper = mediaMapper;
        this.storageService = storageService;
        this.redisCacheMediaCoreService = redisCacheMediaCoreService;
        this.redisCacheMediaListService = redisCacheMediaListService;
        this.redisCacheMediaLikeService = redisCacheMediaLikeService;
        this.redisLockFacade = redisLockFacade;
    }

    @Override
    public MediaPageResult listMedia(Integer page, Integer size, Byte category, Long zoneUserId) {
        // 若未传入分页信息，采用默认值
        int safePage = (page == null || page < 1) ? 1 : page;
        int tempSafeSize = (size == null || size < 1) ? 10 : size;
        final int safeSize = Math.min(tempSafeSize, 100);
        // 先确认是成员专区展示（zoneUserId不为null），还是在公共区全量展示（zoneUserId为null）
        Long zoneUserIdForCache = zoneUserId == null ? 0L : zoneUserId;

        // 参数校验：传入的 category 是否合法
        if (category != null && category != 0 && category != 1) {
            log.warn("listMedia invalid category category={} zoneUserId={}", category, zoneUserId);
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        // 先查缓存：获取媒体列表（包含total和mediaIds）
        MediaListCacheValue cachedList = redisCacheMediaListService.getMediaList(zoneUserIdForCache, category, safePage, safeSize);
        
        List<Media> records;
        long total;

        // 先处理缓存命中的列表
        if (cachedList != null && cachedList.getMediaIds() != null && !cachedList.getMediaIds().isEmpty()) {
            // 缓存命中：批量从 media:core 获取数据
            List<Long> cachedMediaIds = cachedList.getMediaIds();
            log.info("listMedia cache hit zoneUserId={} category={} page={} size={} total={} cachedIds={}", 
                    zoneUserIdForCache, category, safePage, safeSize, cachedList.getTotal(), cachedMediaIds.size());
            
            Map<Long, Media> cachedMediaMap = redisCacheMediaCoreService.batchGetMediaCore(cachedMediaIds);

            // 找出未命中的ID，后续从DB加载（需要加分布式锁保护）
            List<Long> missingIds = cachedMediaIds.stream()
                    .filter(id -> !cachedMediaMap.containsKey(id))
                    .collect(Collectors.toList());

            // 未命中ID列表不为空，从DB加载未命中的媒体数据（加分布式锁防止缓存击穿）
            loadMissingMediaWithLock(missingIds, cachedMediaMap);
            
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
            // media:list 缓存未命中：尝试获取分布式锁，防止缓存击穿
            log.info("listMedia cache miss zoneUserId={} category={} page={} size={}", 
                    zoneUserIdForCache, category, safePage, safeSize);

            // 锁生命周期由 AOP 托管；业务代码只关注“拿到锁/没拿到锁”后的分支策略
            MediaPageResult missResult = redisLockFacade.withMediaListLock(
                    zoneUserIdForCache,
                    category,
                    safePage,
                    safeSize,
                    lockCtx -> {
                        if (lockCtx.isLockAcquired()) {
                            // 1) 获取到锁后做双重检测，避免重复查库
                            MediaListCacheValue doubleCheckCachedList = redisCacheMediaListService.getMediaList(zoneUserIdForCache, category, safePage, safeSize);
                            MediaPageResult doubleCheckResult = buildResultFromCache(doubleCheckCachedList, zoneUserIdForCache, safePage, safeSize, category);
                            if (doubleCheckResult != null) {
                                return doubleCheckResult;
                            }

                            // 2) 缓存仍未命中：查库并回填缓存
                            MediaListQueryResult queryResult = queryMediaListFromDB(zoneUserIdForCache, category, safePage, safeSize);
                            writeMediaListCache(zoneUserIdForCache, category, safePage, safeSize, queryResult.total, queryResult.records);
                            return buildMediaPageResult(queryResult.records, queryResult.total, zoneUserIdForCache, safePage, safeSize, category);
                        }

                        // 3) 获取锁失败：最终缓存检查 -> 降级查库（不回填）
                        MediaListCacheValue finalCheckCachedList = redisCacheMediaListService.getMediaList(zoneUserIdForCache, category, safePage, safeSize);
                        MediaPageResult finalCheckResult = buildResultFromCache(finalCheckCachedList, zoneUserIdForCache, safePage, safeSize, category);
                        if (finalCheckResult != null) {
                            return finalCheckResult;
                        }

                        log.warn("listMedia failed to acquire lock, falling back to direct DB query zoneUserId={} category={} page={} size={}",
                                zoneUserIdForCache, category, safePage, safeSize);
                        MediaListQueryResult queryResult = queryMediaListFromDB(zoneUserIdForCache, category, safePage, safeSize);
                        return buildMediaPageResult(queryResult.records, queryResult.total, zoneUserIdForCache, safePage, safeSize, category);
                    }
            );
            return missResult;
        }

        // 构建返回结果
        return buildMediaPageResult(records, total, zoneUserIdForCache, safePage, safeSize, category);
    }

    /**
     * 从缓存构建结果（如果缓存命中）
     *
     * @param cachedList 缓存的列表数据
     * @param zoneUserIdForCache 专区ID（用于日志）
     * @param safePage 页码（用于日志）
     * @param safeSize 每页数量（用于日志）
     * @param category 分类（用于日志）
     * @return 如果缓存命中返回结果，否则返回 null
     */
    private MediaPageResult buildResultFromCache(MediaListCacheValue cachedList, Long zoneUserIdForCache, 
                                                 int safePage, int safeSize, Byte category) {
        if (cachedList == null || cachedList.getMediaIds() == null || cachedList.getMediaIds().isEmpty()) {
            return null;
        }
        
        List<Long> cachedMediaIds = cachedList.getMediaIds();
        Map<Long, Media> cachedMediaMap = redisCacheMediaCoreService.batchGetMediaCore(cachedMediaIds);
        
        // 找出未命中的ID，从DB加载（需要加分布式锁保护）
        List<Long> missingIds = cachedMediaIds.stream()
                .filter(id -> !cachedMediaMap.containsKey(id))
                .collect(Collectors.toList());
        
        // 处理未命中的 media:core，加分布式锁保护
        loadMissingMediaWithLock(missingIds, cachedMediaMap);
        
        // 构建结果
        List<Media> records = new ArrayList<>();
        for (Long mediaId : cachedMediaIds) {
            Media media = cachedMediaMap.get(mediaId);
            if (media != null && media.getState() != null && media.getState() == 0) {
                records.add(media);
            }
        }
        long total = cachedList.getTotal() != null ? cachedList.getTotal() : 0L;
        
        return buildMediaPageResult(records, total, zoneUserIdForCache, safePage, safeSize, category);
    }

    private static class MediaListQueryResult {
        final long total;
        final List<Media> records;

        MediaListQueryResult(long total, List<Media> records) {
            this.total = total;
            this.records = records;
        }
    }

    /**
     * 从数据库查询媒体列表数据
     *
     * @param zoneUserIdForCache 专区ID：0=公共区，其他=成员专区ID
     * @param category 分类：null=all，0=图片，1=视频
     * @param safePage 页码（从1开始）
     * @param safeSize 每页数量
     * @return 查询结果，包含 total 和 records
     */
    private MediaListQueryResult queryMediaListFromDB(Long zoneUserIdForCache, Byte category, int safePage, int safeSize) {
        long total;
        List<Media> records;
        
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
        
        return new MediaListQueryResult(total, records);
    }

    /**
     * 写入媒体列表缓存（包含列表缓存和 media:core 缓存）
     *
     * @param zoneUserIdForCache 专区ID：0=公共区，其他=成员专区ID
     * @param category 分类：null=all，0=图片，1=视频
     * @param safePage 页码（从1开始）
     * @param safeSize 每页数量
     * @param total 列表总数
     * @param records 媒体记录列表
     */
    private void writeMediaListCache(Long zoneUserIdForCache, Byte category, int safePage, int safeSize, 
                                     long total, List<Media> records) {
        if (records != null && !records.isEmpty()) {
            List<Long> mediaIds = records.stream()
                    .map(Media::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            
            // 写入列表缓存（包含total和mediaIds）
            redisCacheMediaListService.putMediaList(zoneUserIdForCache, category, safePage, safeSize, total, mediaIds);
            
            // 批量写入 media:core 缓存（仅 state=0）
            // 注意：putMediaCore 内部已有异常处理，无需外层 try-catch
            for (Media media : records) {
                if (media.getState() != null && media.getState() == 0) {
                    redisCacheMediaCoreService.putMediaCore(media.getId(), media);
                }
            }
        } else {
            // 查询结果为空，写入空列表缓存（防止缓存穿透）
            redisCacheMediaListService.putMediaList(zoneUserIdForCache, category, safePage, safeSize, 0L, Collections.emptyList());
        }
    }

    /**
     * 查询我的上传列表数据（从数据库）
     *
     * @param uploaderUserId 上传者用户ID
     * @param category 分类：null=all，0=图片，1=视频
     * @param safePage 页码（从1开始）
     * @param safeSize 每页数量
     * @return 查询结果，包含 total 和 records
     */
    private MediaListQueryResult queryMyUploadListFromDB(Long uploaderUserId, Byte category, int safePage, int safeSize) {
        // total（允许显示 state=6 待审核和 state=7 审核未通过，让上传者看到审核状态）
        LambdaQueryWrapper<Media> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(Media::getUploaderId, uploaderUserId);
        // 排除已删除 state=5
        countWrapper.ne(Media::getState, (byte) 5);
        if (category != null) {
            countWrapper.eq(Media::getCategory, category);
        }
        long total = mediaMapper.selectCount(countWrapper);

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

        List<Media> records = mediaMapper.selectList(listWrapper);
        
        return new MediaListQueryResult(total, records);
    }

    /**
     * 查询 media:core 数据并写入缓存
     *
     * @param mediaId 媒体ID
     * @param cachedMediaMap 已缓存的媒体数据映射（用于存储加载到的数据）
     */
    private void queryAndWriteMediaCore(Long mediaId, Map<Long, Media> cachedMediaMap) {
        Media media = mediaMapper.selectById(mediaId);
        if (media == null || media.getState() == null) {
            // 防止缓存穿透：写入空值缓存
            redisCacheMediaCoreService.putNullValue(mediaId);
        } else if (media.getState() != null && media.getState() == 0) {
            // 仅 state=0 的媒体写入缓存
            cachedMediaMap.put(mediaId, media);
            redisCacheMediaCoreService.putMediaCore(mediaId, media);
        }
    }

    /**
     * 加载未命中的 media:core 数据，使用分布式锁保护（防止缓存击穿）
     *
     * @param missingIds 未命中的媒体ID列表
     * @param cachedMediaMap 已缓存的媒体数据映射（用于存储加载到的数据）
     */
    private void loadMissingMediaWithLock(List<Long> missingIds, Map<Long, Media> cachedMediaMap) {
        if (missingIds == null || missingIds.isEmpty()) {
            return;
        }
        
        for (Long mediaId : missingIds) {
            // 每个 mediaId 的锁托管都交给 AOP，业务只保留命中/降级分支
            redisLockFacade.withMediaCoreLock(mediaId, lockCtx -> {
                if (lockCtx.isLockAcquired()) {
                    // 1) 持锁后做双重检测，优先使用缓存
                    Media cachedMedia = redisCacheMediaCoreService.getMediaCore(mediaId);
                    if (cachedMedia != null) {
                        cachedMediaMap.put(mediaId, cachedMedia);
                    } else {
                        // 2) 缓存仍未命中：查库并写回 media:core
                        queryAndWriteMediaCore(mediaId, cachedMediaMap);
                    }
                    return null;
                }

                // 3) 未拿到锁：最终缓存检查 -> 降级查库（不回填）
                Media cachedMedia = redisCacheMediaCoreService.getMediaCore(mediaId);
                if (cachedMedia != null) {
                    cachedMediaMap.put(mediaId, cachedMedia);
                    return null;
                }

                log.warn("listMedia failed to acquire lock for media core, falling back to direct DB query mediaId={}", mediaId);
                Media media = mediaMapper.selectById(mediaId);
                if (media != null && media.getState() != null && media.getState() == 0) {
                    cachedMediaMap.put(mediaId, media);
                }
                return null;
            });
        }
    }

    /** 仅当封面已就绪（coverStatus=2 或未设置）时才生成封面 URL，与 MediaServiceImpl 一致。 */
    private static boolean isCoverReadyForUrl(Media m) {
        Byte cs = m != null ? m.getCoverStatus() : null;
        return cs == null || cs == 2;
    }

    /**
     * 构建 MediaPageResult 返回结果
     */
    private MediaPageResult buildMediaPageResult(List<Media> records, long total, Long zoneUserIdForCache, 
                                                  int safePage, int safeSize, Byte category) {
        List<MediaListItem> list = new ArrayList<>();
        if (records != null) {
            for (Media m : records) {
                String coverPath = m.getCoverPath();
                String coverUrl = null;
                if (isCoverReadyForUrl(m)) {
                    coverUrl = m.getCoverUrl();
                    if (coverUrl == null || coverUrl.trim().isEmpty()) {
                        coverUrl = buildCoverPresignedUrl(coverPath);
                    }
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
        int tempSafeSize = (size == null || size < 1) ? 10 : size;
        final int safeSize = tempSafeSize > 100 ? 100 : tempSafeSize;

        if (category != null && category != 0 && category != 1) {
            log.warn("listMyUpload invalid category category={} uploaderUserId={}", category, uploaderUserId);
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        // 直接查数据库获取本页 (total, records)，再根据 id 从 media:core 或 DB 结果填充
        MediaListQueryResult queryResult = queryMyUploadListFromDB(uploaderUserId, category, safePage, safeSize);
        long total = queryResult.total;
        List<Media> records = queryResult.records;

        if (records == null || records.isEmpty()) {
            log.info("listMyUpload uploaderUserId={} page={} size={} category={} total=0", uploaderUserId, safePage, safeSize, category);
            return new MyUploadPageResult(total, new ArrayList<>());
        }

        List<Long> mediaIds = records.stream()
                .map(Media::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Map<Long, Media> cachedMap = redisCacheMediaCoreService.batchGetMediaCore(mediaIds);
        Map<Long, Media> recordById = new HashMap<>();
        for (Media m : records) {
            if (m.getId() != null) {
                recordById.put(m.getId(), m);
            }
        }

        List<MyUploadListItem> list = new ArrayList<>();
        for (Long id : mediaIds) {
            Media m = cachedMap.containsKey(id) ? cachedMap.get(id) : recordById.get(id);
            if (m == null || (m.getState() != null && m.getState() == 5)) {
                continue;
            }
            MyUploadListItem item = new MyUploadListItem(
                    m.getId(),
                    m.getCategory(),
                    m.getState(),
                    m.getTitle(),
                    m.getCoverPath(),
                    m.getUpdateTime()
            );
            String coverUrl = null;
            if (isCoverReadyForUrl(m)) {
                coverUrl = (m.getCoverUrl() != null && !m.getCoverUrl().trim().isEmpty())
                        ? m.getCoverUrl() : buildCoverPresignedUrl(m.getCoverPath());
            }
            item.coverUrl = coverUrl;
            list.add(item);
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

    @Override
    public List<HotListItem> listHotMedia(Byte category, Integer size) {
        if (category != null && category != 0 && category != 1) {
            log.warn("listHotMedia invalid category category={}", category);
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        int safeSize = (size == null || size < 1) ? 20 : size;
        if (safeSize > 100) {
            safeSize = 100;
        }
        int fetchLimit = safeSize + 10;
        // 获取 Redis ZSET 中的排行榜数据，得到有序 Entry 列表
        List<RedisCacheMediaLikeService.RankEntry> rankEntries = redisCacheMediaLikeService.getRankMediaIdsWithScores(category, fetchLimit);
        if (rankEntries == null || rankEntries.isEmpty()) {
            log.info("listHotMedia empty rank category={} size={}", category, safeSize);
            return new ArrayList<>();
        }

        // 将排行榜数据转换为 mediaId 和 likeCount 的映射
        // 收集 media id 列表，用于后续查询 media:core 缓存或数据库，获取要返回的 HotListItem 的全部字段
        Map<Long, Long> mediaIdToLikeCount = new HashMap<>(rankEntries.size());
        List<Long> mediaIds = new ArrayList<>(rankEntries.size());
        for (RedisCacheMediaLikeService.RankEntry currentEntry : rankEntries) {
            mediaIdToLikeCount.put(currentEntry.mediaId(), currentEntry.likeCount());
            mediaIds.add(currentEntry.mediaId());
        }

        // 先到 media:core 缓存中查询，统计未命中的 media id 列表
        Map<Long, Media> mediaMap = redisCacheMediaCoreService.batchGetMediaCore(mediaIds);
        List<Long> missingIds = mediaIds.stream().filter(id -> !mediaMap.containsKey(id)).collect(Collectors.toList());
        // 加分布式锁保护，从数据库查询并写入缓存
        loadMissingMediaWithLock(missingIds, mediaMap);
        // 构建返回结果
        List<HotListItem> result = new ArrayList<>(safeSize);
        for (Long mediaId : mediaIds) {
            if (result.size() >= safeSize) {
                break;
            }
            Media m = mediaMap.get(mediaId);
            if (m == null || m.getState() == null || m.getState() != 0) {
                continue;
            }
            String coverUrl = null;
            if (isCoverReadyForUrl(m)) {
                coverUrl = (m.getCoverUrl() != null && !m.getCoverUrl().trim().isEmpty())
                        ? m.getCoverUrl() : buildCoverPresignedUrl(m.getCoverPath());
            }
            Long likeCount = mediaIdToLikeCount.getOrDefault(mediaId, 0L);
            result.add(new HotListItem(
                    m.getId(),
                    m.getCategory(),
                    m.getTitle(),
                    m.getDescription(),
                    coverUrl,
                    likeCount
            ));
        }
        log.info("listHotMedia category={} size={} returned={}", category, safeSize, result.size());
        return result;
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
