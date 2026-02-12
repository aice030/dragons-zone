package com.dragons.core.serviceImpl;

import com.dragons.core.dto.MediaAuditResult;
import com.dragons.core.dto.ResponseCode;
import com.dragons.core.entity.Media;
import com.dragons.core.dao.MediaMapper;
import com.dragons.core.entity.MediaVisible;
import com.dragons.core.entity.User;
import com.dragons.core.exception.BusinessException;
import com.dragons.core.service.IMediaService;
import com.dragons.core.service.IMediaVisibleService;
import com.dragons.core.service.IUserService;
import com.dragons.core.storage.StorageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
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
import java.util.stream.Collectors;

/**
 * <p>
 * 媒体资源表 服务实现类
 * </p>
 *
 * @author aice
 * @since 2026-01-17
 */
@Service
public class MediaServiceImpl extends ServiceImpl<MediaMapper, Media> implements IMediaService {

    private final StorageService storageService;
    private final IMediaVisibleService mediaVisibleService;
    private final IUserService userService;
    // 默认封面路径
    private static final String DEFAULT_COVER_PATH = "images/default-cover.jpg";

    @Autowired
    public MediaServiceImpl(StorageService storageService, IMediaVisibleService mediaVisibleService, IUserService userService) {
        this.storageService = storageService;
        this.mediaVisibleService = mediaVisibleService;
        this.userService = userService;
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
            // 返回已有记录的ID和路径
            return new UploadResult(
                    existingMedia.getId(),
                    existingMedia.getStoragePath(),
                    existingMedia.getCategory(),
                    visibleUserIds 
            );
        }

        // 6) 生成MinIO路径
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
            throw new BusinessException(ResponseCode.FILE_UPLOAD_FAILED);
        }

        // 9) 上传媒体文件到 MinIO（使用字节数组上传）
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
            // 更新状态失败，清理已上传的文件和数据库记录
            try {
                // 删除 MinIO 中的文件
                storageService.delete(objectName);
            } catch (Exception e) {
                // 删除失败，记录日志（不影响主流程）
                // TODO: 记录日志
            }
            // 删除数据库中的 media 记录
            try {
                this.removeById(media.getId());
            } catch (Exception e) {
                // 删除失败，记录日志（不影响主流程）
                // TODO: 记录日志
            }
            // 返回上传失败
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
            // 保存失败，state 保持 2（上传成功但不可见）
            // 文件已经上传成功，不回滚，可以后续重试或手动修复
            // TODO: 记录日志或提供重试机制
        }

        // 12) 上传封面文件到 MinIO（如果用户提供了封面）
        uploadCoverToStorageIfPresent(cover, coverPath);

        return new UploadResult(media.getId(), objectName, category, visibleUserIds);
    }

    @Override
    public String getDownloadUrl(Long mediaId) {
        // 1. 查询数据库中的媒体记录
        Media media = this.getById(mediaId);
        // 排除正在删除（state=4）、已删除（state=5）、待审核（state=6）、审核未通过（state=7）的记录
        if (media == null || media.getState() == 4 || media.getState() == 5 || media.getState() == 6 || media.getState() == 7) {
            // 数据库记录不存在或已删除或待审核/驳回，返回404
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // 2. 检查MinIO中文件是否实际存在（防止缓存不一致导致的问题）
        if (!storageService.exists(media.getStoragePath())) {
            // MinIO中文件不存在，返回404
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // 3. 生成预签名URL（2小时有效，7200秒）
        return storageService.getPresignedUrl(media.getStoragePath(), 7200);
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
        // 仅排除已删除（state=5）；state=4（正在删除）允许再次进入，用于收尾清理 visible/MinIO 并置为 5，解决中途失败的一致性问题
        if (media == null || media.getState() == 5) {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        if (media.getUploaderId() == null || !media.getUploaderId().equals(currentUserId)) {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // 保存原始状态，用于回滚
        final Byte originalState = media.getState();
        final String storagePath = media.getStoragePath();
        final String mediaCoverPath = media.getCoverPath();
        final String defaultCoverPath = DEFAULT_COVER_PATH;
        final String coverPath = (mediaCoverPath != null 
                && !mediaCoverPath.equals(storagePath)
                && !mediaCoverPath.equals(defaultCoverPath)) 
                ? mediaCoverPath 
                : null;

        // 第一步：若是首次删除，将 media 的 state 改为 4（正在删除），重试3次
        // 若已经是正在删除状态，即上次删除执行失败，本次为重试，则跳过，直接进入第二步
        if (media.getState() != 4) {
            media.setState((byte) 4);
            media.setUpdateTime(LocalDateTime.now());
            boolean stateUpdated = updateStateWithRetry(media, 3);
            if (!stateUpdated) {
                // 更新状态失败，返回删除失败
                throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
            }
        }

        // 第二步：删除所有 media_visible 数据，重试3次
        boolean visibleDeleted = deleteMediaVisibleWithRetry(mediaId, 3);
        if (!visibleDeleted) {
            // 删除失败，回滚 media 的 state 状态
            media.setState(originalState);
            updateWithRetry(media);
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }

        // 3) 删除media_visible成功后，再删除 MinIO 对象，保证即使出错也不会让用户感知到
        // 删除主文件
        if (storagePath != null && !storagePath.trim().isEmpty()) {
            try {
                storageService.delete(storagePath);
            } catch (Exception e) {
                // 删除失败，记录日志（不影响主流程）
                // TODO: 记录日志
            }
        }
        // 删除封面文件（如果封面路径与主文件路径不同，且不是默认封面路径）
        if (coverPath != null && !coverPath.trim().isEmpty()) {
            try {
                storageService.delete(coverPath);
            } catch (Exception e) {
                // 删除失败，记录日志（不影响主流程）
                // TODO: 记录日志
            }
        }

        // 第四步：将 media 的 state 改为 5（已删除）
        media.setState((byte) 5);
        media.setUpdateTime(LocalDateTime.now());
        updateStateWithRetry(media, 3);
        // 注意：即使更新失败也不影响，因为数据已经标记为正在删除，可以后续清理
    }

    @Override
    public MediaDetailResult getMediaDetail(Long mediaId, Long currentUserId) {
        if (mediaId == null) {
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        Media media = this.getById(mediaId);
        if (media == null || media.getState() == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // 访问规则：
        // - 游客/非上传者：仅允许查看 state=0（已审核通过）
        // - 上传者本人：允许查看 state=0/6/7（便于查看违规原因并做修改）
        byte state = media.getState();
        if (state != 0) {
            boolean isOwner = currentUserId != null
                    && media.getUploaderId() != null
                    && media.getUploaderId().equals(currentUserId);
            if (!(isOwner && (state == 6 || state == 7))) {
                throw new BusinessException(ResponseCode.NOT_FOUND);
            }
        }

        // 说明：
        // “专区”只用于前端筛选展示，不用于做权限控制。
        // 因此详情不做专区校验：只要资源存在且 state=0，就允许查看详情。

        // 为封面生成预签名URL（2小时有效），便于前端详情页/弹窗直接展示
        String coverUrl = null;
        String coverPath = media.getCoverPath();
        if (coverPath != null && !coverPath.trim().isEmpty()) {
            try {
                if (storageService.exists(coverPath)) {
                    coverUrl = storageService.getPresignedUrl(coverPath, 7200);
                }
            } catch (Exception ignored) {
            }
        }

        return new MediaDetailResult(
                media.getId(),
                media.getCategory(),
                media.getTitle(),
                media.getDescription(),
                media.getStoragePath(),
                coverPath,
                coverUrl,
                media.getUploaderId(),
                media.getUpdateTime()
        );
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
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }

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

        // 1) 先上传 MinIO，失败直接返回封面上传失败
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
                coverUrl = storageService.getPresignedUrl(coverPath, 7200);
            }
        } catch (Exception ignored) {
            // ignore
        }

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
                throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
            }
        }

        // 补救逻辑：
        // 若此前为 state=2（上传成功但不可见，通常是 upload 阶段写入 media_visible 失败导致），
        // 调用本接口修复可见范围成功后，将状态修正为 state=0（正常可查看）。
        // 其余状态下，本接口仅维护 media_visible，不影响媒体审核状态（state）。
        if (media.getState() != null && media.getState() == 2) {
            media.setState((byte) 0);
            media.setUpdateTime(LocalDateTime.now());
            if (!updateWithRetry(media)) {
                throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
            }
        }

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
     * 上传/更新共用：根据分类与是否上传封面确定封面路径
     */
    private String resolveCoverPath(Byte category, String objectName, MultipartFile cover) {
        if (category == 0) {
            return objectName;
        }
        if (cover != null && !cover.isEmpty()) {
            return buildCoverObjectName(cover.getOriginalFilename());
        }
        return DEFAULT_COVER_PATH;
    }

    /**
     * 上传/更新共用：主文件上传到 MinIO，失败抛 FILE_UPLOAD_FAILED
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
     * 生成封面文件的MinIO路径
     * 封面统一存放在 images/covers 目录下
     *
     * @param originalFilename 原始文件名
     * @return MinIO对象路径
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
            }
        }

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
            // 逐个更新，失败则记录到失败列表（驳回后保留 MinIO 文件和 media_visible，用户可修改后重新提交）
            if (!this.updateById(media)) {
                failedItems.add(new MediaAuditResult.FailedItem(media.getId(), media.getTitle()));
            }
        }

        return new MediaAuditResult(failedItems);
    }

    @Override
    public IMediaVisibleService.MediaPageResult listPendingMedia(Integer page, Integer size, Long auditorUserId) {
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

        // 查询待审核媒体（state=6）
        LambdaQueryWrapper<Media> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(Media::getState, (byte) 6);
        long total = this.count(countWrapper);

        LambdaQueryWrapper<Media> listWrapper = new LambdaQueryWrapper<>();
        listWrapper.eq(Media::getState, (byte) 6);
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
                            coverUrl = storageService.getPresignedUrl(coverPath, 7200);
                        }
                    } catch (Exception ignored) {
                    }
                }
                list.add(new IMediaVisibleService.MediaListItem(
                        m.getId(),
                        m.getCategory(),
                        m.getTitle(),
                        coverPath,
                        coverUrl
                ));
            }
        }

        return new IMediaVisibleService.MediaPageResult(total, list);
    }
}
