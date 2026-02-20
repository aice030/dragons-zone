package com.dragons.core.serviceImpl;

import com.dragons.core.dto.MediaAuditResult;
import com.dragons.core.dto.ResponseCode;
import com.dragons.core.entity.Media;
import com.dragons.core.dao.MediaMapper;
import com.dragons.core.entity.MediaVisible;
import com.dragons.core.entity.User;
import com.dragons.core.exception.BusinessException;
import com.dragons.core.cache.RedisCacheMediaCoreService;
import com.dragons.core.cache.RedisCacheMediaListService;
import com.dragons.core.service.IMediaService;
import com.dragons.core.service.IMediaVisibleService;
import com.dragons.core.service.IUserService;
import com.dragons.core.storage.StorageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 * 媒体资源表 服务实现类
 * </p>
 *
 * @author aice
 * @since 2026-01-17
 */
@Slf4j
@Service
public class MediaServiceImpl extends ServiceImpl<MediaMapper, Media> implements IMediaService {

    private final StorageService storageService;
    private final IMediaVisibleService mediaVisibleService;
    private final IUserService userService;
    private final RedisCacheMediaCoreService redisCacheMediaCoreService;
    private final RedisCacheMediaListService redisCacheMediaListService;

    /**
     * 封面预签名URL有效期（秒）
     * 设置为12分钟（720秒），覆盖缓存TTL（10分钟）并额外2分钟应对网络波动
     */
    private static final int COVER_URL_TTL_SECONDS = 720;

    // 延迟任务
    private final ScheduledExecutorService delayDeleteExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "media-cache-delay-delete");
                t.setDaemon(true);
                return t;
            });

    @Autowired
    public MediaServiceImpl(StorageService storageService,
                            IMediaVisibleService mediaVisibleService,
                            IUserService userService,
                            RedisCacheMediaCoreService redisCacheMediaCoreService,
                            RedisCacheMediaListService redisCacheMediaListService) {
        this.storageService = storageService;
        this.mediaVisibleService = mediaVisibleService;
        this.userService = userService;
        this.redisCacheMediaCoreService = redisCacheMediaCoreService;
        this.redisCacheMediaListService = redisCacheMediaListService;
    }

    @Override
    public UploadResult upload(MultipartFile file,
                               Byte category,
                               List<Long> visibleUserIds,
                               Long uploaderUserId,
                               String title,
                               String description,
                               MultipartFile cover) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        if (cover == null || cover.isEmpty()) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        validateUploadParams(category, title, description, uploaderUserId);
        // 1) 通过扩展名验证文件类型
        validateFileExtension(file, category);

        // 2) 读取文件内容到字节数组（用于计算哈希和上传，避免重复读取）
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (Exception e) {
            throw new BusinessException(ResponseCode.FILE_UPLOAD_FAILED);
        }
        // 3) 计算文件哈希值（用于幂等性检查）
        String fileHash = calculateFileHash(fileBytes);

        // 4) 幂等性检查：查询是否已存在相同文件（相同用户 + 相同哈希）
        LambdaQueryWrapper<Media> hashWrapper = new LambdaQueryWrapper<>();
        hashWrapper.eq(Media::getFileHash, fileHash)
                   .eq(Media::getUploaderId, uploaderUserId);
        Media existingMedia = this.getOne(hashWrapper);

        // 如果已存在相同文件，直接返回已有记录（幂等）
        // 排除正在删除（state=4）和已删除（state=5）的记录
        if (existingMedia != null && existingMedia.getState() != 4 && existingMedia.getState() != 5) {
            log.info("upload: duplicate content, returning existing media userId={} mediaId={}", uploaderUserId, existingMedia.getId());
            // 返回已有记录的ID和路径
            return new UploadResult(
                    existingMedia.getId(),
                    existingMedia.getStoragePath(),
                    existingMedia.getCategory(),
                    visibleUserIds 
            );
        }

        // 6) 生成对象存储路径
        String objectName = buildObjectName(file.getOriginalFilename(), category);
        String coverPath = resolveCoverPath(category, objectName, cover);

        // 8) 保存 media（state=1，正在上传）
        Media media = new Media();
        media.setUploaderId(uploaderUserId);
        media.setFileHash(fileHash);
        media.setCategory(category);
        media.setTitle(title);
        media.setDescription(description);
        media.setStoragePath(objectName);
        media.setCoverPath(coverPath);
        media.setState((byte) 1);
        media.setUpdateTime(LocalDateTime.now());
        media.setLikeCount(0L);
        media.setLikeCountUpdateTime(LocalDateTime.now());

        boolean saved = saveWithRetry(media);
        if (!saved) {
            log.error("upload: media insert failed");
            throw new BusinessException(ResponseCode.FILE_UPLOAD_FAILED);
        }

        // 9) 上传媒体文件到对象存储（使用字节数组上传）
        try {
            uploadMainFileToStorage(fileBytes, file.getContentType(), objectName);
        } catch (BusinessException e) {
            media.setState((byte) 3);
            media.setUpdateTime(LocalDateTime.now());
            updateWithRetry(media);
            throw e;
        }

        // 10) 上传成功，更新 state=2（上传成功）
        media.setState((byte) 2);
        media.setUpdateTime(LocalDateTime.now());
        // 重试3次
        boolean updated = updateStateWithRetry(media, 3);
        if (!updated) {
            log.error("upload state update failed, rolling back mediaId={} objectName={}", media.getId(), objectName);
            try {
                storageService.delete(objectName);
            } catch (Exception e) {
                log.warn("rollback storage delete failed mediaId={} objectName={}", media.getId(), objectName, e);
            }
            try {
                this.removeById(media.getId());
            } catch (Exception e) {
                log.warn("rollback media remove failed mediaId={}", media.getId(), e);
            }
            throw new BusinessException(ResponseCode.FILE_UPLOAD_FAILED);
        }

        // 11) 保存 media_visible
        boolean visibleSaved = saveMediaVisibleWithRetry(media.getId(), uploaderUserId, visibleUserIds);
        if (visibleSaved) {
            // 保存成功，更新 state=6（待审核）
            media.setState((byte) 6);
            media.setUpdateTime(LocalDateTime.now());
            updateWithRetry(media);
        } else {
            log.warn("media_visible save failed mediaId={}, state remains 2", media.getId());
        }

        // 12) 上传封面文件到对象存储（如果用户提供了封面）
        uploadCoverToStorageIfPresent(cover, coverPath);

        // 13) 删除"我的上传"列表缓存（新上传的媒体会出现在"我的上传"列表中）
        // 注意：新上传的媒体为 state=6（待审核），不会出现在公共区/专区列表中，所以不需要删除公共区/专区列表缓存
        if (category != null && uploaderUserId != null) {
            try {
                redisCacheMediaListService.evictMyUploadList(uploaderUserId, category);
            } catch (Exception e) {
                log.warn("evict my upload list cache failed uploaderId={} category={} error={}", 
                        uploaderUserId, category, e.getMessage());
            }
        }

        log.info("upload success mediaId={} category={} uploaderId={}", media.getId(), category, uploaderUserId);
        return new UploadResult(media.getId(), objectName, category, visibleUserIds);
    }

    @Override
    public String getDownloadUrl(Long mediaId) {
        return getDownloadUrl(mediaId, null);
    }

    @Override
    public String getDownloadUrl(Long mediaId, Long currentUserId) {
        // 1. 查询数据库中的媒体记录
        Media media = this.getById(mediaId);
        if (media == null || media.getState() == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // 下载/预览规则：
        // - 游客/普通用户：仅允许 state=0（已审核通过），不允许普通用户查看和下载 state!=0 的 media
        // - 上传者本人：允许 state=0/6/7（用于自查与修改）
        // - 审核者（作者/管理员）：允许 state=0/6/7（用于审核预览）
        byte state = media.getState();
        if (state == 4 || state == 5) {
            log.warn("getDownloadUrl denied, media was deleted, mediaId={} currentUserId={} reason=deleted_or_deleting", mediaId, currentUserId);
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        // 如果media状态不是公开状态（state=0），就要验证当前用户是否有资格查看并下载当前media
        if (state != 0) {
            // 验证是否为上传者
            boolean isOwner = currentUserId != null
                    && media.getUploaderId() != null
                    && media.getUploaderId().equals(currentUserId);
            boolean isAuditor = false;
            // 验证是否为管理员
            if (currentUserId != null) {
                try {
                    validateAuditorPermission(currentUserId);
                    isAuditor = true;
                } catch (BusinessException ignored) {
                    log.error("getDownloadUrl failed, validate auditor permission failed");
                }
            }
            // 对于 state != 0 的media，只有 media 的上传者或系统管理员可以下载
            if (!((isOwner && (state == 6 || state == 7)) || (isAuditor && (state == 6 || state == 7)))) {
                log.warn("getDownloadUrl denied, no permission to access, mediaId={} currentUserId={} reason=state_not_allowed state={}", mediaId, currentUserId, state);
                throw new BusinessException(ResponseCode.NOT_FOUND);
            }
        }

        // 2. 检查对象存储中文件是否实际存在（防止缓存不一致导致的问题）
        if (!storageService.exists(media.getStoragePath())) {
            log.warn("getDownloadUrl denied, resource doesn't exist, mediaId={} reason=storage_file_not_exists path={}", mediaId, media.getStoragePath());
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // 3. 权限验证通过且对象存储文件存在后，直接生成预签名URL并返回
        int ttlSeconds = 300; // 5分钟有效期
        try {
            String presignedUrl = storageService.getPresignedUrl(media.getStoragePath(), ttlSeconds);
            if (presignedUrl == null || presignedUrl.isEmpty()) {
                log.warn("get blank download url from oss mediaId={}", media.getId());
                throw new BusinessException(ResponseCode.NOT_FOUND);
            }
            return presignedUrl;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("get download url failed mediaId={}", media.getId(), e);
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void delete(Long mediaId, Long currentUserId) {
        if (mediaId == null) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        if (currentUserId == null) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }

        // 1) 查询 media 并校验所有权（不通过则按“资源不存在”处理）
        Media media = this.getById(mediaId);
        // 仅排除已删除（state=5）；state=4（正在删除）允许再次进入，用于收尾清理 visible/对象存储 并置为 5，解决中途失败的一致性问题
        if (media == null || media.getState() == 5) {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        
        // 权限检查：允许删除的条件：
        // 1. 当前用户是上传者（原有逻辑）
        // 2. 或者当前用户是作者（level=0）或管理员（level=1）
        boolean isOwner = media.getUploaderId() != null && media.getUploaderId().equals(currentUserId);
        boolean isAuthorOrAdmin = false;
        if (!isOwner) {
            // 如果不是上传者，检查是否是作者或管理员
            User currentUser = userService.getById(currentUserId);
            if (currentUser != null && currentUser.getLevel() != null) {
                byte level = currentUser.getLevel();
                // 0=作者，1=管理员
                isAuthorOrAdmin = level == 0 || level == 1;
            }
        }
        if (!isOwner && !isAuthorOrAdmin) {
            log.warn("delete denied mediaId={} userId={} reason=not_owner_nor_admin", mediaId, currentUserId);
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // 保存原始状态，用于回滚
        final Byte originalState = media.getState();
        final String storagePath = media.getStoragePath();
        final String mediaCoverPath = media.getCoverPath();
        // 封面路径与主文件不同说明是视频且上传了封面，需从对象存储删除
        final String coverPath = (mediaCoverPath != null && !mediaCoverPath.equals(storagePath))
                ? mediaCoverPath
                : null;
        
        // 保存媒体信息，用于删除列表缓存
        final Byte category = media.getCategory();
        final Long uploaderId = media.getUploaderId();
        
        // 在删除 media_visible 之前，先查询所有相关的 zoneUserId（用于删除列表缓存）
        List<Long> zoneUserIds = mediaVisibleService.getVisibleUserIdsByMediaId(mediaId);

        // 第一步：若是首次删除，将 media 的 state 改为 4（正在删除），重试3次
        // 若已经是正在删除状态，即上次删除执行失败，本次为重试，则跳过，直接进入第二步
        if (media.getState() != 4) {
            media.setState((byte) 4);
            media.setUpdateTime(LocalDateTime.now());
            boolean stateUpdated = updateStateWithRetry(media, 3);
            if (!stateUpdated) {
                log.error("delete failed mediaId={} reason=state_update_to_4_failed", mediaId);
                throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
            }
        }

        // 第二步：删除所有 media_visible 数据，重试3次
        boolean visibleDeleted = deleteMediaVisibleWithRetry(mediaId, 3);
        if (!visibleDeleted) {
            log.error("delete failed mediaId={} reason=media_visible_delete_failed rolling_back_state", mediaId);
            media.setState(originalState);
            updateWithRetry(media);
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }

        // 3) 删除 media_visible 成功后，再删除对象存储中的对象，保证即使出错也不会让用户感知到
        // 删除主文件
        if (storagePath != null && !storagePath.trim().isEmpty()) {
            try {
                storageService.delete(storagePath);
            } catch (Exception e) {
                log.warn("storage delete failed mediaId={} path={}", mediaId, storagePath, e);
            }
        }
        // 删除封面文件（与主文件路径不同则从对象存储删除）
        if (coverPath != null && !coverPath.trim().isEmpty()) {
            try {
                storageService.delete(coverPath);
            } catch (Exception e) {
                log.warn("storage delete cover failed mediaId={} path={}", mediaId, coverPath, e);
            }
        }

        // 第四步：删除相关列表缓存
        // 删除媒体核心数据缓存
        redisCacheMediaCoreService.evictMediaCore(mediaId);
        
        // 删除公共区列表缓存（zoneUserId=0）
        if (category != null) {
            redisCacheMediaListService.evictMediaList(0L, category);
        }
        
        // 删除所有成员专区的列表缓存
        if (zoneUserIds != null && !zoneUserIds.isEmpty()) {
            for (Long zoneUserId : zoneUserIds) {
                if (zoneUserId != null && category != null) {
                    redisCacheMediaListService.evictMediaList(zoneUserId, category);
                }
            }
        }
        
        // 删除"我的上传"列表缓存
        if (uploaderId != null && category != null) {
            redisCacheMediaListService.evictMyUploadList(uploaderId, category);
        }

        // 删除缓存后，物理删除 media 记录
        this.removeById(mediaId);
        log.info("delete success mediaId={} userId={}", mediaId, currentUserId);

        // 延迟 500ms 后再删一次缓存（延迟双删）
        final Long idToEvict = mediaId;
        final Byte finalCategory = category;
        final Long finalUploaderId = uploaderId;
        final List<Long> finalZoneUserIds = zoneUserIds;
        delayDeleteExecutor.schedule(() -> {
            try {
                redisCacheMediaCoreService.evictMediaCore(idToEvict);
                // 延迟删除列表缓存
                if (finalCategory != null) {
                    redisCacheMediaListService.evictMediaList(0L, finalCategory);
                    if (finalZoneUserIds != null && !finalZoneUserIds.isEmpty()) {
                        for (Long zoneUserId : finalZoneUserIds) {
                            if (zoneUserId != null) {
                                redisCacheMediaListService.evictMediaList(zoneUserId, finalCategory);
                            }
                        }
                    }
                    if (finalUploaderId != null) {
                        redisCacheMediaListService.evictMyUploadList(finalUploaderId, finalCategory);
                    }
                }
                log.info("delay double delete cache success mediaId={}", idToEvict);
            } catch (Exception e) {
                log.warn("delay double delete cache failed mediaId={}", idToEvict, e);
            }
        }, 500, TimeUnit.MILLISECONDS);
    }

    @Override
    public MediaDetailResult getMediaDetail(Long mediaId, Long currentUserId) {
        if (mediaId == null) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        MediaDetailResult result = new MediaDetailResult();
        // 先查缓存，命中则从 Media 实体转换为 MediaDetailResult
        Media cachedMedia = redisCacheMediaCoreService.getMediaCore(mediaId);
        if (cachedMedia != null) {
            // 缓存中仅存储 state=0（已审核通过）的媒体，游客/任何人可查看
            return convertToMediaDetailResult(cachedMedia);
        }

        // 1. 缓存未命中，尝试获取分布式锁，防止缓存击穿
        boolean lockAcquired = false;
        // 生成唯一标识，防止误释放其他线程的锁
        String requestId = UUID.randomUUID().toString();
        // 锁续期线程池 
        ScheduledExecutorService lockRenewalExecutor = null; 
        try {
            // 1.1 尝试获取分布式锁（最多重试3次）
            for (int retryCount = 0; retryCount < 3; retryCount++) {
                lockAcquired = redisCacheMediaCoreService.tryLockMediaCore(mediaId, requestId);
                if (lockAcquired) {
                    // 获取锁成功，启动后台线程自动续期（WatchDog机制）
                    // 锁TTL是5秒，每2秒续期一次，确保锁不会过期
                    lockRenewalExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                        Thread t = new Thread(r, "lock-renewal-" + mediaId);
                        t.setDaemon(true);
                        return t;
                    });
                    final Long finalMediaId = mediaId;
                    final String finalRequestId = requestId;
                    lockRenewalExecutor.scheduleAtFixedRate(() -> {
                        boolean renewed = redisCacheMediaCoreService.renewLockMediaCore(finalMediaId, finalRequestId);
                        if (!renewed) {
                            log.warn("lock renewal failed, lock may have been released mediaId={} requestId={}", finalMediaId, finalRequestId);
                        }
                        // 立即开始，每2秒执行一次
                    }, 0, 2, TimeUnit.SECONDS);
                    break; // 获取成功，跳出循环
                }
                // 1.2 获取锁失败，等待100ms后重试查询缓存
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("lock retry interrupted mediaId={}", mediaId);
                    break;
                }
                // 1.3 重试查询缓存，可能其他线程已经写入
                cachedMedia = redisCacheMediaCoreService.getMediaCore(mediaId);
                if (cachedMedia != null) {
                    // 1.4 缓存已命中，直接返回
                    return convertToMediaDetailResult(cachedMedia);
                }
            }
            
            if (lockAcquired) {
                // 2.获取到分布式锁后，再次查询缓存（双重检测）
                cachedMedia = redisCacheMediaCoreService.getMediaCore(mediaId);
                if (cachedMedia != null) {
                    // 其他线程已经写入缓存，直接返回
                    return convertToMediaDetailResult(cachedMedia);
                }
                
                // 3.缓存仍未命中，从数据库查询
                Media media = this.getById(mediaId);
                if (media == null || media.getState() == null) {
                    // 3.1 防止缓存穿透：写入空值缓存
                    redisCacheMediaCoreService.putNullValue(mediaId);
                    throw new BusinessException(ResponseCode.NOT_FOUND);
                }

                // 验证media_state，防止未公开的media被错误的访问到
                // 访问规则：
                // - 游客/非上传者：仅允许查看 state=0（已审核通过）
                // - 上传者本人：允许查看 state=0/6/7（便于查看违规原因并做修改）
                byte state = media.getState();
                if (state != 0) {
                    boolean isOwner = currentUserId != null
                            && media.getUploaderId() != null
                            && media.getUploaderId().equals(currentUserId);
                    // 审核者（作者/管理员）允许查看待审核/驳回媒体，用于审核流程中的预览
                    boolean isAuditor = false;
                    if (currentUserId != null) {
                        try {
                            validateAuditorPermission(currentUserId);
                            isAuditor = true;
                        } catch (BusinessException ignored) {
                            // 非审核者：按“资源不存在”处理，避免泄露待审核资源
                        }
                    }
                    if (!((isOwner && (state == 6 || state == 7)) || (isAuditor && (state == 6 || state == 7)))) {
                        log.warn("getMediaDetail denied because media has not got approved mediaId={} currentUserId={} reason=state_not_allowed state={}", mediaId, currentUserId, state);
                        // 防止缓存穿透：对于无权限访问的数据，也写入空值缓存
                        redisCacheMediaCoreService.putNullValue(mediaId);
                        throw new BusinessException(ResponseCode.NOT_FOUND);
                    }
                }

                // 说明：
                // “专区”只用于前端筛选展示，不用于做权限控制。
                // 因此详情不做专区校验：只要资源存在且 state=0，就允许查看详情。

                // 转换为 MediaDetailResult（会自动处理 coverUrl）
                result = convertToMediaDetailResult(media);
                // 仅 state=0（已审核通过）时写入缓存，供后续游客查询命中
                // 将 coverUrl 设置到 Media 对象中，一起缓存
                if (media.getState() != null && media.getState() == 0) {
                    media.setCoverUrl(result.coverUrl);
                    redisCacheMediaCoreService.putMediaCore(mediaId, media);
                }
            } else {
                // 获取锁失败，降级查询数据库（后续可以添加重试逻辑）
                Media media = this.getById(mediaId);
                if (media == null || media.getState() == null) {
                    throw new BusinessException(ResponseCode.NOT_FOUND);
                }
                
                // 访问规则校验
                byte state = media.getState();
                if (state != 0) {
                    boolean isOwner = currentUserId != null
                            && media.getUploaderId() != null
                            && media.getUploaderId().equals(currentUserId);
                    boolean isAuditor = false;
                    if (currentUserId != null) {
                        try {
                            validateAuditorPermission(currentUserId);
                            isAuditor = true;
                        } catch (BusinessException ignored) {
                        }
                    }
                    if (!((isOwner && (state == 6 || state == 7)) || (isAuditor && (state == 6 || state == 7)))) {
                        throw new BusinessException(ResponseCode.NOT_FOUND);
                    }
                }
                
                // 转换为 MediaDetailResult（会自动处理 coverUrl）
                result = convertToMediaDetailResult(media);
                // 注意：获取锁失败时，不写入缓存，避免并发写入问题
            }
        } catch (BusinessException e) {
            // 业务异常直接抛出
            throw e;
        } catch (Exception e) {
            log.error("get media detail failed mediaId={}", mediaId, e);
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        } finally {
            // 停止续期线程
            if (lockRenewalExecutor != null) {
                lockRenewalExecutor.shutdown();
                try {
                    // 等待续期线程停止（最多等待1秒）
                    if (!lockRenewalExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                        lockRenewalExecutor.shutdownNow(); // 强制停止
                    }
                } catch (InterruptedException e) {
                    lockRenewalExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            // 只有获取到锁才释放（使用 requestId 确保只释放自己的锁）
            if (lockAcquired) {
                redisCacheMediaCoreService.unlockMediaCore(mediaId, requestId);
            }
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadResult update(Long mediaId,
                               Long currentUserId,
                               String title,
                               String description) {
        if (mediaId == null) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        if (currentUserId == null) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }

        Media media = this.getById(mediaId);
        // 允许 state=0（正常）、state=6（待审核）、state=7（审核未通过）修改
        if (media == null || media.getState() == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        Byte currentState = media.getState();
        if (currentState != 0 && currentState != 6 && currentState != 7) {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        if (media.getUploaderId() == null || !media.getUploaderId().equals(currentUserId)) {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // 复用既有校验：使用数据库里的 category（更新基础信息不允许改主文件）
        validateUploadParams(media.getCategory(), title, description, currentUserId);

        media.setTitle(title);
        media.setDescription(description);
        media.setUpdateTime(LocalDateTime.now());
        // 修改基础信息后，一律回到 state=6（待审核），需要重新审核
        // - 原 state=0（正常）→ 6
        // - 原 state=6（待审核）→ 保持 6
        // - 原 state=7（审核未通过）→ 6
        if (currentState == null || currentState != 6) {
            media.setState((byte) 6);
        }

        if (!updateWithRetry(media)) {
            log.error("update failed mediaId={} reason=db_update_failed", mediaId);
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
        // 写时删除：媒体信息变更，删除缓存
        try {
            redisCacheMediaCoreService.evictMediaCore(mediaId);
            // 如果原 state=0，删除列表缓存（因为会从列表中消失）
            if (currentState != null && currentState == 0) {
                evictMediaListCache(mediaId, media.getCategory());
            }
        } catch (Exception e) {
            log.error("update media cache failed mediaId={} reason={}", mediaId, e.getMessage());
        }
        log.info("media update success mediaId={}", mediaId);
        return new UploadResult(media.getId(), media.getStoragePath(), media.getCategory(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CoverUpdateResult updateCover(Long mediaId, MultipartFile cover, Long currentUserId) {
        if (mediaId == null) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        if (currentUserId == null) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }
        if (cover == null || cover.isEmpty()) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        // 仅允许图片格式作为封面
        validateCoverExtension(cover);

        Media media = this.getById(mediaId);
        if (media == null || media.getState() == null || media.getState() != 0) {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        if (media.getUploaderId() == null || !media.getUploaderId().equals(currentUserId)) {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        // 仅视频允许更新封面
        if (media.getCategory() == null || media.getCategory() != 1) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        String coverPath = buildCoverObjectName(cover.getOriginalFilename());
        byte[] coverBytes;
        try {
            coverBytes = cover.getBytes();
        } catch (Exception e) {
            throw new BusinessException(ResponseCode.FILE_UPLOAD_FAILED);
        }

        // 1) 先上传对象存储，失败直接返回封面上传失败
        try {
            storageService.upload(coverBytes, cover.getContentType(), coverPath);
        } catch (Exception e) {
            throw new BusinessException(ResponseCode.FILE_UPLOAD_FAILED);
        }

        // 2) 再更新 DB；若失败则删除刚上传的封面做补偿，并返回封面上传失败
        media.setCoverPath(coverPath);
        media.setUpdateTime(LocalDateTime.now());
        // 修改封面后回到 state=6（待审核），需要重新审核
        media.setState((byte) 6);
        boolean updated = updateWithRetry(media);
        if (!updated) {
            log.error("updateCover failed mediaId={} reason=db_update_failed compensating_cover_delete", mediaId);
            try {
                storageService.delete(coverPath);
            } catch (Exception ignored) {
            }
            throw new BusinessException(ResponseCode.FILE_UPLOAD_FAILED);
        }

        // 3) 返回封面路径，并尽力返回封面预签名URL（用于前端立即刷新预览）
        String coverUrl = null;
        try {
            if (storageService.exists(coverPath)) {
                coverUrl = storageService.getPresignedUrl(coverPath, COVER_URL_TTL_SECONDS);
            }
        } catch (Exception ignored) {
            // ignore
        }
        // 写时删除：封面变更，删除缓存
        // updateCover 只允许 state=0 的媒体，更新后会变为 state=6，需要删除列表缓存
        try {
            redisCacheMediaCoreService.evictMediaCore(mediaId);
            evictMediaListCache(mediaId, media.getCategory());
        } catch (Exception e) {
            log.error("media cache update failed mediaId={}", mediaId);
        }
        log.info("media cover update success mediaId={}", mediaId);
        return new CoverUpdateResult(mediaId, coverPath, coverUrl);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadResult rebuildVisible(Long mediaId, List<Long> visibleUserIds, Long currentUserId) {
        if (mediaId == null) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        if (currentUserId == null) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }
        if (visibleUserIds == null) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        Media media = this.getById(mediaId);
        // 排除正在删除（state=4）和已删除（state=5）
        if (media == null || media.getState() == null || media.getState() == 4 || media.getState() == 5) {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        if (media.getUploaderId() == null || !media.getUploaderId().equals(currentUserId)) {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // 1) 差集同步（方案C）：只增删差集，减少写入量
        Set<Long> newSet = visibleUserIds.stream()
                .filter(id -> id != null && id != 0L).collect(Collectors.toSet());

        List<MediaVisible> existingList = mediaVisibleService.list(
                new LambdaQueryWrapper<MediaVisible>().eq(MediaVisible::getMediaId, mediaId)
        );
        Set<Long> oldSet = existingList.stream()
                .map(MediaVisible::getUserId)
                .filter(id -> id != null && id != 0L)
                .collect(Collectors.toSet());

        Set<Long> toAdd = new HashSet<>(newSet);
        toAdd.removeAll(oldSet);
        Set<Long> toRemove = new HashSet<>(oldSet);
        toRemove.removeAll(newSet);

        try {
            if (!toRemove.isEmpty()) {
                mediaVisibleService.remove(
                        new LambdaQueryWrapper<MediaVisible>()
                                .eq(MediaVisible::getMediaId, mediaId)
                                .in(MediaVisible::getUserId, toRemove)
                );
            }
        } catch (Exception e) {
            log.error("rebuildVisible failed mediaId={} reason=remove_visible_failed", mediaId, e);
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }

        for (Long zoneId : toAdd) {
            MediaVisible mv = new MediaVisible();
            mv.setMediaId(mediaId);
            mv.setUserId(zoneId);
            try {
                if (!mediaVisibleService.save(mv)) {
                    throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
                }
            } catch (BusinessException be) {
                throw be;
            } catch (Exception e) {
                log.error("rebuildVisible failed mediaId={} zoneId={} reason=add_visible_failed", mediaId, zoneId, e);
                throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
            }
        }

        // 补救逻辑：
        // 若此前为 state=2（上传成功但不可见，通常是 upload 阶段写入 media_visible 失败导致），
        // 调用本接口修复可见范围成功后，将状态修正为 state=0（正常可查看）。
        // 其余状态下，本接口仅维护 media_visible，不影响媒体审核状态（state）。
        if (media.getState() != null && media.getState() == 2) {
            log.info("rebuildVisible: correcting media state from 2 to 0 mediaId={}", mediaId);
            media.setState((byte) 0);
            media.setUpdateTime(LocalDateTime.now());
            if (!updateWithRetry(media)) {
                log.error("rebuildVisible failed mediaId={} reason=state_correct_2_to_0_failed", mediaId);
                throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
            }
        }
        log.info("media visible rebuild success mediaId={}", mediaId);
        return new UploadResult(media.getId(), media.getStoragePath(), media.getCategory(), visibleUserIds);
    }

    private boolean saveWithRetry(Media media) {
        try {
            return this.save(media);
        } catch (Exception e) {
            // 重试一次
            try {
                return this.save(media);
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    private boolean updateWithRetry(Media media) {
        try {
            return this.updateById(media);
        } catch (Exception e) {
            // 重试一次
            try {
                return this.updateById(media);
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    /**
     * 更新状态并重试指定次数
     *
     * @param media 要更新的媒体对象
     * @param maxRetries 最大重试次数
     * @return 是否更新成功
     */
    private boolean updateStateWithRetry(Media media, int maxRetries) {
        for (int i = 0; i < maxRetries; i++) {
            try {
                if (this.updateById(media)) {
                    return true;
                }
            } catch (Exception e) {
                // 继续重试
                log.warn("update media state failed, retry #{}, error={}", i, e.getMessage());
            }
        }
        return false;
    }

    /**
     * 按 mediaId 删除 media_visible，支持重试。
     * 若表中本就没有对应记录（0 行被删），视为幂等成功，返回 true，便于 state=4 时重试删除能收尾。
     *
     * @param mediaId 媒体ID
     * @param maxRetries 最大重试次数
     * @return 是否执行成功（无异常即成功，含 0 行被删）
     */
    private boolean deleteMediaVisibleWithRetry(Long mediaId, int maxRetries) {
        for (int i = 0; i < maxRetries; i++) {
            try {
                mediaVisibleService.remove(
                        new LambdaQueryWrapper<MediaVisible>()
                                .eq(MediaVisible::getMediaId, mediaId)
                );
                // 执行成功即返回 true（0 行被删也视为成功，保证 state=4 重试时可收尾）
                return true;
            } catch (Exception e) {
                // 继续重试
            }
        }
        return false;
    }

    private boolean saveMediaVisibleWithRetry(Long mediaId,
                                              Long uploaderUserId,
                                              List<Long> visibleUserIds) {
        try {
            return saveMediaVisible(mediaId, uploaderUserId, visibleUserIds);
        } catch (Exception e) {
            // 重试一次
            try {
                return saveMediaVisible(mediaId, uploaderUserId, visibleUserIds);
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    private boolean saveMediaVisible(Long mediaId,
                                     Long uploaderUserId,
                                     List<Long> visibleUserIds) {
        // 产品定义：
        // - 永远全部公开：公共区展示直接查 media 表，因此不需要写入 user_id=0
        // - media_visible 仅用于“成员专区筛选”
        //
        // 因此这里不写入“上传者本人”的 user_id 记录，也不写入公共区 user_id=0。
        // 上传者本人管理自己的内容：通过 media.uploader_id 查询。

        // 遍历专区列表（为空数组自然跳过）
        // 注意：这里的 visibleUserIds 表达“哪些成员专区要展示这条媒体”，并非权限控制
        for (Long zoneId : visibleUserIds) {
            if (zoneId == null || zoneId == 0L) {
                continue;
            }
            MediaVisible mv = new MediaVisible();
            mv.setMediaId(mediaId);
            mv.setUserId(zoneId);
            mediaVisibleService.save(mv);
        }

        return true;
    }

    private void validateFileExtension(MultipartFile file, Byte category) {
        String name = file.getOriginalFilename();
        if (name == null) {
            throw new BusinessException(ResponseCode.FILE_FORMAT_NOT_SUPPORTED);
        }
        String lower = name.toLowerCase(Locale.ROOT);

        if (category == 0) {
            // 图片常见格式
            if (!(lower.endsWith(".jpg")
                    || lower.endsWith(".jpeg")
                    || lower.endsWith(".png")
                    || lower.endsWith(".gif")
                    || lower.endsWith(".webp")
                    || lower.endsWith(".bmp"))) {
                throw new BusinessException(ResponseCode.FILE_FORMAT_NOT_SUPPORTED);
            }
            return;
        }

        // category == 1：视频常见格式
        if (!(lower.endsWith(".mp4")
                || lower.endsWith(".mov")
                || lower.endsWith(".avi")
                || lower.endsWith(".mkv")
                || lower.endsWith(".flv")
                || lower.endsWith(".wmv"))) {
            throw new BusinessException(ResponseCode.FILE_FORMAT_NOT_SUPPORTED);
        }
    }

    /**
     * 验证封面文件格式（必须是图片格式）
     *
     * @param cover 封面文件
     */
    private void validateCoverExtension(MultipartFile cover) {
        String name = cover.getOriginalFilename();
        if (name == null) {
            throw new BusinessException(ResponseCode.FILE_FORMAT_NOT_SUPPORTED);
        }
        String lower = name.toLowerCase(Locale.ROOT);

        // 封面必须是图片格式
        if (!(lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp")
                || lower.endsWith(".bmp"))) {
            throw new BusinessException(ResponseCode.FILE_FORMAT_NOT_SUPPORTED);
        }
    }

    /**
     * 计算文件内容的MD5哈希值（用于幂等性检查）
     *
     * @param fileBytes 文件字节数组
     * @return MD5哈希值（32位16进制字符串）
     */
    private String calculateFileHash(byte[] fileBytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(fileBytes);
            byte[] hashBytes = md.digest();
            
            // 转换为16进制字符串
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // 哈希计算失败，抛出异常
            throw new BusinessException(ResponseCode.FILE_UPLOAD_FAILED);
        }
    }

    private String buildObjectName(String originalFilename, Byte category) {
        String prefix = (category == 0) ? "images" : "videos";
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        
        // 提取扩展名（避免中文文件名导致路径问题）
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            int lastDotIndex = originalFilename.lastIndexOf(".");
            if (lastDotIndex >= 0 && lastDotIndex < originalFilename.length() - 1) {
                extension = originalFilename.substring(lastDotIndex).toLowerCase(Locale.ROOT);
            }
        }
        
        // 如果没有扩展名，根据category设置默认扩展名
        if (extension.isEmpty()) {
            extension = (category == 0) ? ".jpg" : ".mp4";
        }
        
        return prefix + "/" + datePath + "/" + uuid + extension;
    }

    /**
     * 上传/更新共用：校验 category、title/description 长度、uploaderUserId 非空
     */
    private void validateUploadParams(Byte category, String title, String description, Long uploaderUserId) {
        if (category == null || (category != 0 && category != 1)) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        if (uploaderUserId == null) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }
        if (title != null && title.length() > 32) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        if (description != null && description.length() > 128) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
    }

    /**
     * 上传/更新共用：根据分类确定封面路径（上传时 cover 已校验必填，此处不再判空）
     */
    private String resolveCoverPath(Byte category, String objectName, MultipartFile cover) {
        if (category == 0) {
            return objectName;
        }
        return buildCoverObjectName(cover.getOriginalFilename());
    }

    /**
     * 上传/更新共用：主文件上传到对象存储，失败抛 FILE_UPLOAD_FAILED
     */
    private void uploadMainFileToStorage(byte[] fileBytes, String contentType, String objectName) {
        try {
            storageService.upload(fileBytes, contentType, objectName);
        } catch (Exception e) {
            throw new BusinessException(ResponseCode.FILE_UPLOAD_FAILED);
        }
    }

    /**
     * 上传/更新共用：若有封面则校验并上传，失败不抛异常（不影响主流程）
     */
    private void uploadCoverToStorageIfPresent(MultipartFile cover, String coverPath) {
        if (cover == null || cover.isEmpty()) {
            return;
        }
        try {
            validateCoverExtension(cover);
            byte[] coverBytes = cover.getBytes();
            storageService.upload(coverBytes, cover.getContentType(), coverPath);
        } catch (Exception e) {
            // 封面上传失败不抛，与现有行为一致
        }
    }

    /**
     * 生成封面文件的对象存储路径
     * 封面统一存放在 images/covers 目录下
     *
     * @param originalFilename 原始文件名
     * @return 对象存储中的对象路径
     */
    private String buildCoverObjectName(String originalFilename) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        
        // 提取扩展名（避免中文文件名导致路径问题）
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            int lastDotIndex = originalFilename.lastIndexOf(".");
            if (lastDotIndex >= 0 && lastDotIndex < originalFilename.length() - 1) {
                extension = originalFilename.substring(lastDotIndex).toLowerCase(Locale.ROOT);
            }
        }
        
        // 如果没有扩展名，默认使用.jpg
        if (extension.isEmpty()) {
            extension = ".jpg";
        }
        
        return "images/covers/" + datePath + "/" + uuid + extension;
    }

    /**
     * 删除媒体相关的列表缓存（公共区/专区）
     * 用于 update、updateCover、approve、reject 等操作后删除列表缓存
     *
     * @param mediaId 媒体ID
     * @param category 媒体分类（0=图片，1=视频）
     */
    private void evictMediaListCache(Long mediaId, Byte category) {
        if (mediaId == null || category == null) {
            return;
        }
        try {
            // 查询媒体所属的专区列表
            List<Long> zoneUserIds = mediaVisibleService.getVisibleUserIdsByMediaId(mediaId);
            
            // 删除公共区列表缓存（zoneUserId=0）
            redisCacheMediaListService.evictMediaList(0L, category);
            
            // 删除所有成员专区的列表缓存
            if (zoneUserIds != null && !zoneUserIds.isEmpty()) {
                for (Long zoneUserId : zoneUserIds) {
                    if (zoneUserId != null) {
                        redisCacheMediaListService.evictMediaList(zoneUserId, category);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("evict media list cache failed mediaId={} category={} error={}", mediaId, category, e.getMessage());
        }
    }

    /**
     * 校验审核者权限（必须是管理员 level=1 或作者 level=0）
     */
    private void validateAuditorPermission(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }
        User user = userService.getById(userId);
        if (user == null || user.getState() == null || user.getState() != 0) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }
        Byte level = user.getLevel();
        if (level == null || (level != 0 && level != 1)) {
            throw new BusinessException(ResponseCode.MEDIA_AUDIT_PERMISSION_DENIED);
        }
    }

    @Override
    public MediaAuditResult approveMedia(List<Long> mediaIds, Long auditorUserId) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        if (auditorUserId == null) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }

        // 校验审核者权限
        validateAuditorPermission(auditorUserId);

        // 批量查询媒体
        List<Media> mediaList = this.listByIds(mediaIds);
        // 构建 mediaId -> Media 的映射，便于查找不存在的 mediaId
        Map<Long, Media> mediaMap = new HashMap<>();
        if (mediaList != null) {
            for (Media media : mediaList) {
                mediaMap.put(media.getId(), media);
            }
        }

        // 逐个处理，收集失败项
        List<MediaAuditResult.FailedItem> failedItems = new ArrayList<>();
        for (Long mediaId : mediaIds) {
            Media media = mediaMap.get(mediaId);
            // 如果媒体不存在，记录到失败列表
            if (media == null) {
                failedItems.add(new MediaAuditResult.FailedItem(mediaId, "媒体id: " + mediaId + "不存在"));
                continue;
            }
            // 校验 state=6（待审核）
            if (media.getState() == null || media.getState() != 6) {
                failedItems.add(new MediaAuditResult.FailedItem(media.getId(), media.getTitle()));
                continue;
            }
            // 更新状态为 0（正常）
            media.setState((byte) 0);
            media.setUpdateTime(LocalDateTime.now());
            // 逐个更新，失败则记录到失败列表
            if (!this.updateById(media)) {
                failedItems.add(new MediaAuditResult.FailedItem(media.getId(), media.getTitle()));
            } else {
                // 审核通过会改变 state（6→0），删除该媒体缓存和列表缓存
                // state 变为 0 后，会出现在公共区/专区列表中，需要删除列表缓存以刷新
                try {
                    redisCacheMediaCoreService.evictMediaCore(media.getId());
                    evictMediaListCache(media.getId(), media.getCategory());
                } catch (Exception e) {
                    log.warn("after approve media cache delete failed error={}", e.getMessage());
                }
            }
        }
        int successCount = mediaIds.size() - failedItems.size();
        log.info("approveMedia auditorUserId={} total={} success={} failed={}", auditorUserId, mediaIds.size(), successCount, failedItems.size());
        return new MediaAuditResult(failedItems);
    }

    @Override
    public MediaAuditResult rejectMedia(List<Long> mediaIds, Long auditorUserId) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        if (auditorUserId == null) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }

        // 校验审核者权限
        validateAuditorPermission(auditorUserId);

        // 批量查询媒体
        List<Media> mediaList = this.listByIds(mediaIds);
        // 构建 mediaId -> Media 的映射，便于查找不存在的 mediaId
        Map<Long, Media> mediaMap = new HashMap<>();
        if (mediaList != null) {
            for (Media media : mediaList) {
                mediaMap.put(media.getId(), media);
            }
        }

        // 逐个处理，收集失败项
        List<MediaAuditResult.FailedItem> failedItems = new ArrayList<>();
        for (Long mediaId : mediaIds) {
            Media media = mediaMap.get(mediaId);
            // 如果媒体不存在，记录到失败列表
            if (media == null) {
                failedItems.add(new MediaAuditResult.FailedItem(mediaId, "媒体id" + mediaId + "不存在"));
                continue;
            }
            // 校验 state=6（待审核）
            if (media.getState() == null || media.getState() != 6) {
                failedItems.add(new MediaAuditResult.FailedItem(media.getId(), media.getTitle()));
                continue;
            }
            // 更新状态为 7（审核未通过）
            media.setState((byte) 7);
            media.setUpdateTime(LocalDateTime.now());
            // 逐个更新，失败则记录到失败列表（驳回后保留对象存储中的文件和 media_visible，用户可修改后重新提交）
            if (!this.updateById(media)) {
                failedItems.add(new MediaAuditResult.FailedItem(media.getId(), media.getTitle()));
            } else {
                // 审核驳回会改变 state（6→7），删除该媒体缓存和列表缓存
                // 原 state=6，变为 state=7 后，会从公共区/专区列表中消失，需要删除列表缓存
                try {
                    redisCacheMediaCoreService.evictMediaCore(media.getId());
                    evictMediaListCache(media.getId(), media.getCategory());
                } catch (Exception e) {
                    log.warn("after reject media cache delete failed error={}", e.getMessage());
                }
            }
        }
        int successCount = mediaIds.size() - failedItems.size();
        log.info("rejectMedia auditorUserId={} total={} success={} failed={}", auditorUserId, mediaIds.size(), successCount, failedItems.size());
        return new MediaAuditResult(failedItems);
    }

    @Override
    public IMediaVisibleService.MediaPageResult listPendingMedia(Integer page, Integer size, Byte category, Long auditorUserId) {
        if (auditorUserId == null) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }

        // 校验审核者权限
        validateAuditorPermission(auditorUserId);

        // 分页参数处理
        int safePage = (page == null || page < 1) ? 1 : page;
        int safeSize = (size == null || size < 1) ? 10 : size;
        if (safeSize > 100) {
            safeSize = 100;
        }

        // 查询待审核媒体（state=6），可选按 category 筛选，复用 idx_media_state_category_update_time 索引
        LambdaQueryWrapper<Media> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(Media::getState, (byte) 6);
        if (category != null) {
            countWrapper.eq(Media::getCategory, category);
        }
        long total = this.count(countWrapper);
        log.info("listPendingMedia auditorUserId={} page={} size={} category={} total={}", auditorUserId, safePage, safeSize, category, total);

        LambdaQueryWrapper<Media> listWrapper = new LambdaQueryWrapper<>();
        listWrapper.eq(Media::getState, (byte) 6);
        if (category != null) {
            listWrapper.eq(Media::getCategory, category);
        }
        int offset = (safePage - 1) * safeSize;
        listWrapper.orderByDesc(Media::getUpdateTime).orderByDesc(Media::getId);
        listWrapper.last("limit " + offset + "," + safeSize);
        List<Media> records = this.list(listWrapper);

        // 转换为返回结构
        List<IMediaVisibleService.MediaListItem> list = new ArrayList<>();
        if (records != null) {
            for (Media m : records) {
                String coverPath = m.getCoverPath();
                String coverUrl = null;
                if (coverPath != null && !coverPath.trim().isEmpty()) {
                    try {
                        if (storageService.exists(coverPath)) {
                            coverUrl = storageService.getPresignedUrl(coverPath, COVER_URL_TTL_SECONDS);
                        }
                    } catch (Exception ignored) {
                    }
                }
                list.add(new IMediaVisibleService.MediaListItem(
                        m.getId(),
                        m.getCategory(),
                        m.getTitle(),
                        coverPath,
                        m.getUpdateTime(),
                        coverUrl
                ));
            }
        }

        return new IMediaVisibleService.MediaPageResult(total, list);
    }

    /**
     * 将 Media 实体转换为 MediaDetailResult
     * 处理 coverUrl：优先使用缓存中的值，如果不存在则重新生成
     *
     * @param media Media 实体
     * @return MediaDetailResult
     */
    private MediaDetailResult convertToMediaDetailResult(Media media) {
        if (media == null) {
            return null;
        }
        // 优先使用缓存中的 coverUrl，如果不存在则重新生成
        String coverUrl = media.getCoverUrl();
        if (coverUrl == null || coverUrl.trim().isEmpty()) {
            String coverPath = media.getCoverPath();
            if (coverPath != null && !coverPath.trim().isEmpty()) {
                try {
                    if (storageService.exists(coverPath)) {
                        coverUrl = storageService.getPresignedUrl(coverPath, COVER_URL_TTL_SECONDS);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return new MediaDetailResult(
                media.getId(),
                media.getCategory(),
                media.getTitle(),
                media.getDescription(),
                media.getStoragePath(),
                media.getCoverPath(),
                coverUrl,
                media.getUploaderId(),
                media.getUpdateTime()
        );
    }
}
