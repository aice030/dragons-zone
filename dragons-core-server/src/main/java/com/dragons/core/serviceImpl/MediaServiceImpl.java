package com.dragons.core.serviceImpl;

import com.dragons.core.dto.MediaAuditResult;
import com.dragons.core.dto.MediaLikeEvent;
import com.dragons.core.dto.ResponseCode;
import com.dragons.core.entity.Media;
import com.dragons.core.dao.MediaMapper;
import com.dragons.core.entity.MediaVisible;
import com.dragons.core.entity.User;
import com.dragons.core.exception.BusinessException;
import com.dragons.core.cache.RedisCacheMediaCoreService;
import com.dragons.core.cache.RedisCacheMediaListService;
import com.dragons.core.cache.RedisCacheMediaLikeService;
import com.dragons.core.service.IMediaService;
import com.dragons.core.service.IMediaVisibleService;
import com.dragons.core.service.MediaLikePersistService;
import com.dragons.core.service.OssStsService;
import com.dragons.core.service.IUserService;
import com.dragons.core.mq.MediaLikeEventSender;
import com.dragons.core.storage.StorageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
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
    private final RedisCacheMediaLikeService redisCacheMediaLikeService;
    private final MediaLikePersistService mediaLikePersistService;
    private final MediaLikeEventSender mediaLikeEventSender;
    private final OssStsService ossStsService;

    /**
     * 封面预签名URL有效期（秒）
     * 设置为12分钟（720秒），覆盖缓存TTL（10分钟）并额外2分钟应对网络波动
     */
    private static final int COVER_URL_TTL_SECONDS = 720;

    /**
     * 上传预签名URL有效期（秒）
     * 设置为1小时，便于前端在短时间内完成直传
     */
    private static final int UPLOAD_URL_TTL_SECONDS = 3600;

    /**
     * 视频未传封面时的默认封面在 OSS 中的对象路径（与 classpath:images/default_cover.jpg 对应）
     */
    private static final String DEFAULT_COVER_OBJECT_NAME = "images/default_cover.jpg";

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
                            RedisCacheMediaListService redisCacheMediaListService,
                            RedisCacheMediaLikeService redisCacheMediaLikeService,
                            MediaLikePersistService mediaLikePersistService,
                            MediaLikeEventSender mediaLikeEventSender,
                            OssStsService ossStsService) {
        this.storageService = storageService;
        this.mediaVisibleService = mediaVisibleService;
        this.userService = userService;
        this.redisCacheMediaCoreService = redisCacheMediaCoreService;
        this.redisCacheMediaListService = redisCacheMediaListService;
        this.redisCacheMediaLikeService = redisCacheMediaLikeService;
        this.mediaLikePersistService = mediaLikePersistService;
        this.mediaLikeEventSender = mediaLikeEventSender;
        this.ossStsService = ossStsService;
    }

    @Override
    public UploadResult prepareUpload(String fileHash,
                                      Byte category,
                                      Long uploaderUserId,
                                      String title,
                                      String description,
                                      String filename) {
        validateUploadParams(category, title, description, uploaderUserId);

        if (fileHash == null || fileHash.trim().isEmpty()) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        // 幂等性检查：查询是否已存在相同文件（相同用户 + 相同哈希）
        LambdaQueryWrapper<Media> hashWrapper = new LambdaQueryWrapper<>();
        hashWrapper.eq(Media::getFileHash, fileHash)
                .eq(Media::getUploaderId, uploaderUserId);
        Media existingMedia = this.getOne(hashWrapper);

        // 如果已存在相同文件，直接返回已有记录（幂等，排除正在删除和已删除）
        if (existingMedia != null && existingMedia.getState() != null
                && existingMedia.getState() != 4 && existingMedia.getState() != 5) {
            log.info("prepareUpload: duplicate content, returning existing media userId={} mediaId={}", uploaderUserId, existingMedia.getId());
            String uploadUrl = storageService.getPresignedUploadUrl(existingMedia.getStoragePath(), UPLOAD_URL_TTL_SECONDS);
            return new UploadResult(
                    existingMedia.getId(),
                    existingMedia.getStoragePath(),
                    existingMedia.getCategory(),
                    null,
                    uploadUrl,
                    UPLOAD_URL_TTL_SECONDS,
                    ossStsService.getStsCredentials()
            );
        }

        // 生成对象存储路径（无主文件，仅根据可选文件名或按分类默认扩展名）；不落库 coverPath
        String objectName = buildObjectName(filename, category);

        // 保存 media（state=1，正在上传），不写入 coverPath，不写 media_visible
        Media media = new Media();
        media.setUploaderId(uploaderUserId);
        media.setFileHash(fileHash);
        media.setCategory(category);
        media.setTitle(title);
        media.setDescription(description);
        media.setStoragePath(objectName);
        media.setCoverPath(null);
        media.setState((byte) 1);
        // 创建 media 记录时封面状态置为 0（未上传）。封面要到 uploadComplete 中才会上传
        media.setCoverStatus((byte) 0);
        media.setUpdateTime(LocalDateTime.now());
        media.setLikeCount(0L);
        media.setLikeCountUpdateTime(LocalDateTime.now());

        boolean saved = saveWithRetry(media);
        if (!saved) {
            log.error("prepareUpload: media insert failed");
            throw new BusinessException(ResponseCode.FILE_UPLOAD_FAILED);
        }

        // 为新建的对象生成上传用预签名 URL，供前端直传使用
        String uploadUrl = storageService.getPresignedUploadUrl(objectName, UPLOAD_URL_TTL_SECONDS);

        log.info("prepareUpload success mediaId={} category={} uploaderId={}", media.getId(), category, uploaderUserId);
        return new UploadResult(media.getId(), objectName, category, null, uploadUrl, UPLOAD_URL_TTL_SECONDS, ossStsService.getStsCredentials());
    }

    @Override
    public void uploadComplete(Long mediaId,
                               boolean success,
                               List<Long> visibleUserIds,
                               Long currentUserId,
                               MultipartFile cover) {
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
        if (media == null || media.getState() == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        if (media.getUploaderId() == null || !media.getUploaderId().equals(currentUserId)) {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // 幂等：如果当前 state 不是 1（正在上传），视为已处理，直接返回成功
        if (media.getState() != 1) {
            log.info("uploadComplete: media already processed, mediaId={} state={}", mediaId, media.getState());
            return;
        }

        // 获取media元数据，用于后续验证
        String storagePath = media.getStoragePath();
        Byte category = media.getCategory();
        Long uploaderUserId = media.getUploaderId();

        if (!success) {
            // 前端传回失败时先看 OSS 是否已有内容：有则视为上传成功，继续后续流程；没有再按失败处理
            boolean ossHasContent = storagePath != null && !storagePath.trim().isEmpty() && storageService.exists(storagePath);
            if (ossHasContent) {
                log.info("uploadComplete: frontend reported failure but OSS has object, treating as success mediaId={} path={}", mediaId, storagePath);
                // 不 return，继续执行下方成功流程
            } else {
                // OSS 查不到内容，按上传失败处理
                media.setState((byte) 3);
                media.setUpdateTime(LocalDateTime.now());
                if (!updateWithRetry(media)) {
                    log.error("uploadComplete: mark failed state=3 update failed mediaId={}", mediaId);
                    throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
                }
                if (storagePath != null && !storagePath.trim().isEmpty()) {
                    try {
                        storageService.delete(storagePath);
                    } catch (Exception e) {
                        log.warn("uploadComplete: delete storage object failed mediaId={} path={}", mediaId, storagePath, e);
                    }
                }
                log.info("uploadComplete: marked upload failed mediaId={} userId={}", mediaId, currentUserId);
                return;
            }
        }

        // success = true（或前端报失败但 OSS 有内容）：先校验主文件对象是否真实存在
        if (storagePath == null || storagePath.trim().isEmpty()) {
            log.warn("uploadComplete: storagePath is blank, mark failed mediaId={}", mediaId);
            media.setState((byte) 3);
            media.setUpdateTime(LocalDateTime.now());
            updateWithRetry(media);
            throw new BusinessException(ResponseCode.FILE_UPLOAD_FAILED);
        }

        if (!storageService.exists(storagePath)) {
            log.warn("uploadComplete: storage object not found, mark failed mediaId={} path={}", mediaId, storagePath);
            media.setState((byte) 3);
            media.setUpdateTime(LocalDateTime.now());
            updateWithRetry(media);
            throw new BusinessException(ResponseCode.FILE_UPLOAD_FAILED);
        }

        // 封面可选：图片(category=0)未传封面时用主文件作封面；视频(category=1)未传封面时从 OSS 视频截取第一帧
        String coverPath;
        byte[] coverBytes = null;
        String coverContentType = null;
        if (category == 0) {
            // 图片：未传封面则主文件即封面
            if (cover == null || cover.isEmpty()) {
                coverPath = storagePath;
            } else {
                validateCoverExtension(cover);
                coverPath = storagePath;
                // 图片主文件即封面，无需再上传单独封面文件
            }
        } else {
            // 视频：未传封面则用默认封面兜底（OSS 已有则直接用，否则从 classpath 上传）；传了则用用户上传的封面
            if (cover == null || cover.isEmpty()) {
                coverPath = resolveDefaultCoverPath();
            } else {
                validateCoverExtension(cover);
                coverPath = buildCoverObjectName(cover.getOriginalFilename());
                try {
                    coverBytes = cover.getBytes();
                } catch (Exception e) {
                    throw new BusinessException(ResponseCode.FILE_UPLOAD_FAILED);
                }
                coverContentType = cover.getContentType();
            }
        }

        // 若图片传了封面且与主文件不同（当前设计下图片 coverPath=storagePath，不单独上传），此处不重复上传
        boolean needUploadCover = (category == 1 && coverBytes != null);

        // 对象存在，更新 state=2（上传成功）
        media.setState((byte) 2);
        media.setUpdateTime(LocalDateTime.now());
        boolean updated = updateStateWithRetry(media, 3);
        if (!updated) {
            log.error("uploadComplete: state update to 2 failed mediaId={}", mediaId);
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }

        // 落库 coverPath，设置 coverStatus（注意：此处设置的是 coverStatus，不要误改 media.state）
        // 若更新失败仅打日志不抛异常，保证后续 media_visible、state=6 仍能执行，避免主流程已成功却因封面元数据写库失败导致整次上传报错
        if (coverPath != null) {
            media.setCoverPath(coverPath);
            media.setUpdateTime(LocalDateTime.now());
            if (category == 0) {
                // 图片：主文件即封面，无需单独上传，coverStatus=2（上传成功）
                media.setCoverStatus((byte) 2);
            } else if (category == 1) {
                // 视频：若需单独上传用户封面则先置 coverStatus=1（正在上传），否则已是默认封面 coverStatus=2
                media.setCoverStatus(needUploadCover ? (byte) 1 : (byte) 2);
            }
            if (!updateWithRetry(media)) {
                log.error("uploadComplete: update coverPath/coverStatus failed mediaId={}, continuing to media_visible and state=6", mediaId);
            }
        }

        // 视频且传了封面：上传到 OSS 后把 coverStatus 置为 2（上传成功）
        if (needUploadCover && coverBytes != null && coverContentType != null) {
            try {
                storageService.upload(coverBytes, coverContentType, coverPath);
                media.setCoverStatus((byte) 2);
                media.setUpdateTime(LocalDateTime.now());
                if (!updateWithRetry(media)) {
                    log.warn("uploadComplete: update coverStatus to 2 failed mediaId={}", mediaId);
                }
            } catch (Exception e) {
                log.warn("uploadComplete: cover upload to OSS failed mediaId={} coverPath={}", mediaId, coverPath, e);
                // 封面上传失败不阻断主流程，coverStatus 保持 1（正在上传）或可后续置为 3（上传失败），与现有约定一致
            }
        }

        // 保存 media_visible
        boolean visibleSaved = saveMediaVisibleWithRetry(media.getId(), uploaderUserId, visibleUserIds);
        if (visibleSaved) {
            // 保存成功，更新 state=6（待审核）
            media.setState((byte) 6);
            media.setUpdateTime(LocalDateTime.now());
            updateWithRetry(media);
        } else {
            log.warn("uploadComplete: media_visible save failed mediaId={}, state remains 2", media.getId());
        }

        log.info("uploadComplete success mediaId={} category={} uploaderId={}", media.getId(), category, currentUserId);
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

        // 第三步：删除 media_visible 成功后，再删除对象存储中的对象，保证即使出错也不会让用户感知到
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

        // 第四步：删除相关缓存（每项独立 try-catch，失败只打日志不中断删除，与 overcome.md「缓存删除失败不回滚数据库」一致）
        evictMediaCacheForDelete(mediaId, category, uploaderId, zoneUserIds, true);

        // 删除缓存后，物理删除 media 记录
        this.removeById(mediaId);
        log.info("delete success mediaId={} userId={}", mediaId, currentUserId);

        // 延迟 500ms 后再删一次缓存（延迟双删）
        delayDeleteExecutor.schedule(() -> {
            try {
                evictMediaCacheForDelete(mediaId, category, uploaderId, zoneUserIds, false);
                log.info("delay double delete cache success mediaId={}", mediaId);
            } catch (Exception e) {
                log.warn("delay double delete cache failed mediaId={}", mediaId, e);
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
            MediaDetailResult r = convertToMediaDetailResult(cachedMedia);
            r.likeCount = resolveLikeCount(mediaId, cachedMedia);
            return r;
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
                    MediaDetailResult r = convertToMediaDetailResult(cachedMedia);
                    r.likeCount = resolveLikeCount(mediaId, cachedMedia);
                    return r;
                }
            }
            
            if (lockAcquired) {
                // 2.获取到分布式锁后，再次查询缓存（双重检测）
                cachedMedia = redisCacheMediaCoreService.getMediaCore(mediaId);
                if (cachedMedia != null) {
                    // 其他线程已经写入缓存，直接返回
                    MediaDetailResult r = convertToMediaDetailResult(cachedMedia);
                    r.likeCount = resolveLikeCount(mediaId, cachedMedia);
                    return r;
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
                // - 上传者本人：允许查看 state=0/2/6/7（2=上传成功；6/7=待审核/驳回便于查看原因；state=1 上传中不可见）
                // - 审核者：允许查看 state=6/7（审核流程中的预览）
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
                    if (!((isOwner && (state == 2 || state == 6 || state == 7)) || (isAuditor && (state == 6 || state == 7)))) {
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
                result.likeCount = resolveLikeCount(mediaId, media);
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
                
                // 访问规则校验（与上方持锁分支一致：上传者可看 2/6/7，state=1 不可见；审核者可看 6/7）
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
                    if (!((isOwner && (state == 2 || state == 6 || state == 7)) || (isAuditor && (state == 6 || state == 7)))) {
                        throw new BusinessException(ResponseCode.NOT_FOUND);
                    }
                }
                
                // 转换为 MediaDetailResult（会自动处理 coverUrl）
                result = convertToMediaDetailResult(media);
                result.likeCount = resolveLikeCount(mediaId, media);
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
        if (currentState != null && currentState == 0) {
            try {
                redisCacheMediaCoreService.evictMediaCore(mediaId);
                evictMediaListCacheAllCategories(mediaId, true);
                redisCacheMediaLikeService.evictMediaLikeData(mediaId);
            } catch (Exception e) {
                log.error("update media cache failed mediaId={} reason={}", mediaId, e.getMessage());
            }
            // 延迟双删：500ms 后再删一次缓存
            delayDeleteExecutor.schedule(() -> {
                try {
                    redisCacheMediaCoreService.evictMediaCore(mediaId);
                    evictMediaListCacheAllCategories(mediaId, false);
                    redisCacheMediaLikeService.evictMediaLikeData(mediaId);
                    log.info("update delay double delete cache success mediaId={}", mediaId);
                } catch (Exception e) {
                    log.warn("update delay double delete cache failed mediaId={}", mediaId, e);
                }
            }, 500, TimeUnit.MILLISECONDS);
        } else {
            try {
                redisCacheMediaCoreService.evictMediaCore(mediaId);
            } catch (Exception e) {
                log.error("update media cache failed mediaId={} reason={}", mediaId, e.getMessage());
            }
        }
        log.info("media update success mediaId={}", mediaId);
        return new UploadResult(media.getId(), media.getStoragePath(), media.getCategory(), null, null, null, null);
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

        // 上传前记录旧封面 path，成功后若非默认封面则从 OSS 删除，防止孤儿资源
        final String oldCoverPath = media.getCoverPath();

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
        media.setCoverStatus((byte) 2); // 封面上传成功
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

        // 2.5) 删除旧封面，防止 OSS 孤儿资源（仅当旧封面非预设默认封面时删除）
        if (oldCoverPath != null && !oldCoverPath.trim().isEmpty()
                && !DEFAULT_COVER_OBJECT_NAME.equals(oldCoverPath.trim())) {
            try {
                storageService.delete(oldCoverPath);
                log.info("updateCover deleted old cover mediaId={} oldPath={}", mediaId, oldCoverPath);
            } catch (Exception e) {
                log.warn("updateCover delete old cover failed mediaId={} oldPath={}", mediaId, oldCoverPath, e);
            }
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
        // updateCover 只允许 state=0 的媒体，更新后会变为 state=6，需要删除列表缓存与点赞相关缓存
        try {
            redisCacheMediaCoreService.evictMediaCore(mediaId);
            evictMediaListCacheAllCategories(mediaId, true);
            redisCacheMediaLikeService.evictMediaLikeData(mediaId);
        } catch (Exception e) {
            log.error("media cache update failed mediaId={}", mediaId);
        }
        // 延迟双删：500ms 后再删一次缓存
        delayDeleteExecutor.schedule(() -> {
            try {
                redisCacheMediaCoreService.evictMediaCore(mediaId);
                evictMediaListCacheAllCategories(mediaId, false);
                redisCacheMediaLikeService.evictMediaLikeData(mediaId);
                log.info("updateCover delay double delete cache success mediaId={}", mediaId);
            } catch (Exception e) {
                log.warn("updateCover delay double delete cache failed mediaId={}", mediaId, e);
            }
        }, 500, TimeUnit.MILLISECONDS);
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
        // 受影响的成员专区：新增的与移除的（公共区 zoneUserId=0 不依赖 media_visible，无需删除）
        Set<Long> affectedZones = new HashSet<>(toAdd);
        affectedZones.addAll(toRemove);

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
        boolean stateCorrectedTo0 = false;
        if (media.getState() != null && media.getState() == 2) {
            log.info("rebuildVisible: correcting media state from 2 to 0 mediaId={}", mediaId);
            media.setState((byte) 0);
            media.setUpdateTime(LocalDateTime.now());
            if (!updateWithRetry(media)) {
                log.error("rebuildVisible failed mediaId={} reason=state_correct_2_to_0_failed", mediaId);
                throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
            }
            stateCorrectedTo0 = true;
        }

        // 写时删除：可见范围变更，删除受影响专区的 media:list 缓存（三种 category 全删）
        // 成员专区：toAdd ∪ toRemove；state 2→0 时公共区也需删除（媒体新出现在公共区）
        if (!affectedZones.isEmpty() || stateCorrectedTo0) {
            if (stateCorrectedTo0) {
                affectedZones.add(0L);
            }
            try {
                evictMediaListCacheForZones(mediaId, affectedZones, true);
            } catch (Exception e) {
                log.warn("rebuildVisible evict media list cache failed mediaId={} error={}", mediaId, e.getMessage());
            }
            // 延迟双删：500ms 后再删一次缓存
            delayDeleteExecutor.schedule(() -> {
                try {
                    evictMediaListCacheForZones(mediaId, affectedZones, false);
                    log.info("rebuildVisible delay double delete cache success mediaId={}", mediaId);
                } catch (Exception e) {
                    log.warn("rebuildVisible delay double delete cache failed mediaId={}", mediaId, e);
                }
            }, 500, TimeUnit.MILLISECONDS);
        }

        log.info("media visible rebuild success mediaId={}", mediaId);
        return new UploadResult(media.getId(), media.getStoragePath(), media.getCategory(), visibleUserIds, null, null, null);
    }

    /**
     * 点赞：校验后走一种同步方式。当前使用「先 DB 再 Redis」；高并发时改为调用 likeSyncViaMq（注释本行、取消下一行注释）。
     */
    @Override
    public void like(Long mediaId, Long currentUserId) {
        if (mediaId == null) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        if (currentUserId == null) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }
        Media media = this.getById(mediaId);
        if (media == null || media.getState() == null || media.getState() != 0) {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        Byte category = media.getCategory();
        if (category == null) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        likeSyncDbFirst(mediaId, currentUserId, category);
        // likeSyncViaMq(mediaId, currentUserId, category);
    }

    /**
     * 取消点赞：校验后走一种同步方式。当前使用「先 DB 再 Redis」；高并发时改为调用 unlikeSyncViaMq（注释本行、取消下一行注释）。
     */
    @Override
    public void unlike(Long mediaId, Long currentUserId) {
        if (mediaId == null) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        if (currentUserId == null) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }
        Media media = this.getById(mediaId);
        if (media == null || media.getState() == null || media.getState() != 0) {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        Byte category = media.getCategory();
        if (category == null) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }
        unlikeSyncDbFirst(mediaId, currentUserId, category);
        // unlikeSyncViaMq(mediaId, currentUserId, category);
    }

    /** 点赞同步方式一：先 DB 再 Redis（轻量，无 MQ）。Redis 用 Lua 原子写回位图 + 双 ZSET。 */
    private void likeSyncDbFirst(Long mediaId, Long currentUserId, Byte category) {
        if (redisCacheMediaLikeService.getLikedFromCache(mediaId, currentUserId).orElse(false)) {
            return;
        }
        mediaLikePersistService.persist(new MediaLikeEvent(MediaLikeEvent.Operation.LIKE, mediaId, currentUserId, category));
        Media media = this.getById(mediaId);
        long likeCount = media != null && media.getLikeCount() != null ? media.getLikeCount() : 0L;
        redisCacheMediaLikeService.setLikedAndScoreForMedia(mediaId, currentUserId, category, likeCount, true);
    }

    /** 点赞同步方式二：先 Redis 再 MQ，消费者落库；发送失败回滚 Redis（高并发方案）。切换时在 like() 中注释 DbFirst、取消本方法调用注释。 */
    @SuppressWarnings("unused")
    private void likeSyncViaMq(Long mediaId, Long currentUserId, Byte category) {
        boolean updated = redisCacheMediaLikeService.like(mediaId, currentUserId, category);
        if (!updated) {
            return;
        }
        try {
            mediaLikeEventSender.send(new MediaLikeEvent(MediaLikeEvent.Operation.LIKE, mediaId, currentUserId, category));
        } catch (Exception e) {
            log.error("like: MQ send failed, rolling back Redis mediaId={} userId={} error={}", mediaId, currentUserId, e.getMessage());
            redisCacheMediaLikeService.rollbackLike(mediaId, currentUserId, category);
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /** 取消点赞同步方式一：先 DB 再 Redis（轻量，无 MQ）。Redis 用 Lua 原子写回位图 + 双 ZSET。 */
    private void unlikeSyncDbFirst(Long mediaId, Long currentUserId, Byte category) {
        if (!redisCacheMediaLikeService.getLikedFromCache(mediaId, currentUserId).orElse(false)) {
            return;
        }
        mediaLikePersistService.persist(new MediaLikeEvent(MediaLikeEvent.Operation.UNLIKE, mediaId, currentUserId, category));
        Media media = this.getById(mediaId);
        long likeCount = media != null && media.getLikeCount() != null ? media.getLikeCount() : 0L;
        redisCacheMediaLikeService.setLikedAndScoreForMedia(mediaId, currentUserId, category, likeCount, false);
    }

    /** 取消点赞同步方式二：先 Redis 再 MQ，消费者落库；发送失败回滚 Redis（高并发方案）。切换时在 unlike() 中注释 DbFirst、取消本方法调用注释。 */
    @SuppressWarnings("unused")
    private void unlikeSyncViaMq(Long mediaId, Long currentUserId, Byte category) {
        boolean updated = redisCacheMediaLikeService.unlike(mediaId, currentUserId, category);
        if (!updated) {
            return;
        }
        try {
            mediaLikeEventSender.send(new MediaLikeEvent(MediaLikeEvent.Operation.UNLIKE, mediaId, currentUserId, category));
        } catch (Exception e) {
            log.error("unlike: MQ send failed, rolling back Redis mediaId={} userId={} error={}", mediaId, currentUserId, e.getMessage());
            redisCacheMediaLikeService.rollbackUnlike(mediaId, currentUserId, category);
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
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
     * 视频未传封面时的兜底：若 OSS 已有默认封面则直接返回路径，否则从 classpath 读取并上传后返回路径。
     * 资源文件需放在 src/main/resources/images/default_cover.jpg
     *
     * @return 默认封面在 OSS 中的对象路径，失败时返回 null
     */
    private String resolveDefaultCoverPath() {
        if (storageService.exists(DEFAULT_COVER_OBJECT_NAME)) {
            return DEFAULT_COVER_OBJECT_NAME;
        }
        byte[] bytes = loadDefaultCoverBytes();
        if (bytes == null || bytes.length == 0) {
            log.warn("resolveDefaultCoverPath: default cover resource not found or empty");
            return null;
        }
        try {
            storageService.upload(bytes, "image/jpeg", DEFAULT_COVER_OBJECT_NAME);
            log.info("resolveDefaultCoverPath: uploaded default cover to OSS path={}", DEFAULT_COVER_OBJECT_NAME);
            return DEFAULT_COVER_OBJECT_NAME;
        } catch (Exception e) {
            log.warn("resolveDefaultCoverPath: upload default cover failed path={}", DEFAULT_COVER_OBJECT_NAME, e);
            return null;
        }
    }

    /**
     * 从 classpath 读取默认封面图片（images/default_cover.jpg）
     */
    private byte[] loadDefaultCoverBytes() {
        try {
            InputStream is = new ClassPathResource("images/default_cover.jpg").getInputStream();
            return FileCopyUtils.copyToByteArray(is);
        } catch (Exception e) {
            log.warn("loadDefaultCoverBytes: read failed", e);
            return null;
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
     * 删除媒体时使用的缓存清理：core、列表（先 all 再 category）、我的上传、点赞。
     * 供 delete 第一次删除与延迟双删共用，保证逻辑一致。
     *
     * @param mediaId         媒体ID（仅用于日志）
     * @param category        媒体分类，null 时只删 all 维度
     * @param uploaderId      上传者ID
     * @param zoneUserIds     可见专区ID列表
     * @param perCallTryCatch true=每项 evict 单独 try-catch（第一次删除）；false=不包 try-catch，由调用方统一 catch（延迟双删）
     */
    private void evictMediaCacheForDelete(Long mediaId, Byte category, Long uploaderId, List<Long> zoneUserIds, boolean perCallTryCatch) {
        Runnable evictCore = () -> redisCacheMediaCoreService.evictMediaCore(mediaId);
        Runnable evictLike = () -> redisCacheMediaLikeService.evictMediaLikeData(mediaId);
        if (perCallTryCatch) {
            try { evictCore.run(); } catch (Exception e) { log.warn("delete: evictMediaCore failed mediaId={} error={}", mediaId, e.getMessage()); }
        } else {
            evictCore.run();
        }
        // 列表：先 all，再具体 category
        evictMediaListOnce(mediaId, 0L, null, perCallTryCatch);
        if (category != null) {
            evictMediaListOnce(mediaId, 0L, category, perCallTryCatch);
        }
        if (zoneUserIds != null && !zoneUserIds.isEmpty()) {
            for (Long zoneUserId : zoneUserIds) {
                if (zoneUserId != null) {
                    evictMediaListOnce(mediaId, zoneUserId, null, perCallTryCatch);
                    if (category != null) {
                        evictMediaListOnce(mediaId, zoneUserId, category, perCallTryCatch);
                    }
                }
            }
        }
        if (perCallTryCatch) {
            try { evictLike.run(); } catch (Exception e) { log.warn("delete: evictMediaLikeData failed mediaId={} error={}", mediaId, e.getMessage()); }
        } else {
            evictLike.run();
        }
    }

    /**
     * 删除单个 (zoneUserId, category) 的 media:list 缓存。供 evictMediaCacheForDelete 与 evictMediaListCacheAllCategories 共用。
     */
    private void evictMediaListOnce(Long mediaId, Long zoneUserId, Byte category, boolean perCallTryCatch) {
        Runnable r = () -> redisCacheMediaListService.evictMediaList(zoneUserId, category);
        if (perCallTryCatch) {
            try {
                r.run();
            } catch (Exception e) {
                log.warn("evictMediaList failed mediaId={} zoneUserId={} category={} error={}", mediaId, zoneUserId, category, e.getMessage());
            }
        } else {
            r.run();
        }
    }

    /**
     * 驳回时使用的缓存清理：media:core。
     * 不删 media:list（被驳回媒体从未出现在公共区/专区列表）；不删 media:my（已移除 myUploadList 缓存）。
     * 供 rejectMedia 第一次删除与延迟双删共用。
     */
    private void evictMediaCacheForReject(Long mediaId, Long uploaderId, Byte category, boolean perCallTryCatch) {
        Runnable evictCore = () -> redisCacheMediaCoreService.evictMediaCore(mediaId);
        if (perCallTryCatch) {
            try { evictCore.run(); } catch (Exception e) { log.warn("reject: evictMediaCore failed mediaId={} error={}", mediaId, e.getMessage()); }
        } else {
            evictCore.run();
        }
    }

    /**
     * 删除指定专区的 media:list 缓存，三种 category（all/0/1）全部删除。
     * 用于 rebuildVisible：可见范围变更时，删除受影响的专区（含成员专区和/或公共区 zoneUserId=0）。
     *
     * @param mediaId        媒体ID（用于日志）
     * @param zoneUserIds    要删除的专区ID集合（可含 0=公共区）
     * @param perCallTryCatch true=每项 evict 单独 try-catch
     */
    private void evictMediaListCacheForZones(Long mediaId, Set<Long> zoneUserIds, boolean perCallTryCatch) {
        if (mediaId == null || zoneUserIds == null || zoneUserIds.isEmpty()) {
            return;
        }
        for (Long zoneUserId : zoneUserIds) {
            if (zoneUserId == null) {
                continue;
            }
            evictMediaListOnce(mediaId, zoneUserId, null, perCallTryCatch);
            evictMediaListOnce(mediaId, zoneUserId, (byte) 0, perCallTryCatch);
            evictMediaListOnce(mediaId, zoneUserId, (byte) 1, perCallTryCatch);
        }
    }

    /**
     * 删除媒体展示列表缓存（公共区+专区），三种 category（all/0/1）全部删除。
     * 用于 update、rebuildVisible、updateCover、approveMedia 等操作后的缓存失效及延迟双删。
     *
     * @param mediaId        媒体ID
     * @param perCallTryCatch true=每项 evict 单独 try-catch（第一次删除）；false=不包 try-catch（延迟双删）
     */
    private void evictMediaListCacheAllCategories(Long mediaId, boolean perCallTryCatch) {
        if (mediaId == null) {
            return;
        }
        try {
            List<Long> zoneUserIds = mediaVisibleService.getVisibleUserIdsByMediaId(mediaId);
            // 公共区 + 成员专区
            List<Long> zones = new ArrayList<>();
            zones.add(0L);
            if (zoneUserIds != null && !zoneUserIds.isEmpty()) {
                for (Long z : zoneUserIds) {
                    if (z != null && !z.equals(0L)) {
                        zones.add(z);
                    }
                }
            }
            for (Long zoneUserId : zones) {
                evictMediaListOnce(mediaId, zoneUserId, null, perCallTryCatch);
                evictMediaListOnce(mediaId, zoneUserId, (byte) 0, perCallTryCatch);
                evictMediaListOnce(mediaId, zoneUserId, (byte) 1, perCallTryCatch);
            }
        } catch (Exception e) {
            log.warn("evict media list cache all categories failed mediaId={} error={}", mediaId, e.getMessage());
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
                    evictMediaListCacheAllCategories(media.getId(), true);
                } catch (Exception e) {
                    log.warn("after approve media cache delete failed error={}", e.getMessage());
                }
                // 延迟双删：500ms 后再删一次缓存
                // 必须用 final 变量捕获当次循环的 mediaId：media 在循环中会变，若直接捕获 media 则 500ms 后执行时会是最后一次循环的值
                final Long idToEvict = media.getId();
                delayDeleteExecutor.schedule(() -> {
                    try {
                        redisCacheMediaCoreService.evictMediaCore(idToEvict);
                        evictMediaListCacheAllCategories(idToEvict, false);
                        log.info("approveMedia delay double delete cache success mediaId={}", idToEvict);
                    } catch (Exception e) {
                        log.warn("approveMedia delay double delete cache failed mediaId={}", idToEvict, e);
                    }
                }, 500, TimeUnit.MILLISECONDS);
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
                // 审核驳回会改变 state（6→7），删除 media:core（不删 media:list；media:my 已移除未启用）
                try {
                    evictMediaCacheForReject(media.getId(), media.getUploaderId(), media.getCategory(), true);
                } catch (Exception e) {
                    log.warn("after reject media cache delete failed error={}", e.getMessage());
                }
                // 延迟双删：500ms 后再删一次缓存
                // 必须用 final 变量捕获当次循环的值：media 在循环中会变
                final Long idToEvict = media.getId();
                final Long finalUploaderId = media.getUploaderId();
                final Byte finalCategory = media.getCategory();
                delayDeleteExecutor.schedule(() -> {
                    try {
                        evictMediaCacheForReject(idToEvict, finalUploaderId, finalCategory, false);
                        log.info("rejectMedia delay double delete cache success mediaId={}", idToEvict);
                    } catch (Exception e) {
                        log.warn("rejectMedia delay double delete cache failed mediaId={}", idToEvict, e);
                    }
                }, 500, TimeUnit.MILLISECONDS);
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

        // 转换为返回结构（仅 coverStatus=2 或 null 时生成封面 URL）
        List<IMediaVisibleService.MediaListItem> list = new ArrayList<>();
        if (records != null) {
            for (Media m : records) {
                String coverPath = m.getCoverPath();
                String coverUrl = null;
                if (isCoverReadyForUrl(m) && coverPath != null && !coverPath.trim().isEmpty()) {
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
     * 解析点赞数：优先读 Redis ZSET score；未在榜时用 Media.likeCount（仅 DB 有值，media:core 已不存 likeCount）。
     */
    private long resolveLikeCount(Long mediaId, Media media) {
        return redisCacheMediaLikeService.getLikeCountFromRank(mediaId)
                .orElse(media != null && media.getLikeCount() != null ? media.getLikeCount() : 0L);
    }

    /**
     * 仅当封面已就绪（coverStatus=2 或未设置，兼容旧数据）时才生成/使用封面 URL，避免对「正在上传」「上传失败」的封面发起 OSS 请求或展示无效 URL。
     */
    private static boolean isCoverReadyForUrl(Media media) {
        Byte cs = media != null ? media.getCoverStatus() : null;
        return cs == null || cs == 2;
    }

    /**
     * 将 Media 实体转换为 MediaDetailResult
     * 处理 coverUrl：仅当封面就绪（coverStatus=2 或 null）时生成；优先使用缓存中的值。
     *
     * @param media Media 实体
     * @return MediaDetailResult
     */
    private MediaDetailResult convertToMediaDetailResult(Media media) {
        if (media == null) {
            return null;
        }
        String coverUrl = null;
        if (isCoverReadyForUrl(media)) {
            coverUrl = media.getCoverUrl();
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
                media.getUpdateTime(),
                null
        );
    }
}
