package com.dragons.core.cache;

import java.util.Optional;

/**
 * 媒体点赞 Redis 缓存服务（media:rank:* ZSET + media:liked:* 位图）。
 * <p>
 * 按 Redis_DESIGN.md：点赞/取消点赞仅写 Redis ZSET，定时任务回写 DB。
 * 使用位图（BITMAP）记录「用户已赞」：offset=userId，bit=1 表示已赞；适合 userId 连续且范围有界的场景。
 * </p>
 *
 * @author aice
 * @since 2026-02-20
 */
public interface RedisCacheMediaLikeService {

    String RANK_KEY_ALL = "media:rank:all";
    String RANK_KEY_0 = "media:rank:0";
    String RANK_KEY_1 = "media:rank:1";
    /** 已赞位图 key 前缀：media:liked:{mediaId}，offset=userId 表示该用户是否已赞 */
    String LIKED_BITMAP_PREFIX = "media:liked:";
    /** 查询已赞状态时的分布式锁 key 前缀：lock:media:liked:{mediaId}，防击穿，粒度按 mediaId */
    String LOCK_MEDIA_LIKED_KEY_PREFIX = "lock:media:liked:";
    /** 分布式锁 TTL（秒） */
    int LOCK_TTL_SECONDS = 5;

    /**
     * 点赞：位图该用户位若为 0 则置 1 并对排行榜 ZSET 执行 +1（all + category 双 key，Lua 原子）；已为 1 则幂等返回 false。
     *
     * @param mediaId  媒体ID
     * @param userId   用户ID
     * @param category 媒体分类 0=图片，1=视频
     * @return true 表示本次新点赞并已更新 ZSET，false 表示已赞过（幂等）
     */
    boolean like(Long mediaId, Long userId, Byte category);

    /**
     * 取消点赞：位图该用户位若为 1 则置 0 并对排行榜 ZSET 执行 -1（仅当 score&gt;0，Lua 原子，保证不为负）；已为 0 则幂等返回 false。
     *
     * @param mediaId  媒体ID
     * @param userId   用户ID
     * @param category 媒体分类 0=图片，1=视频
     * @return true 表示本次取消成功并已更新 ZSET，false 表示未赞过（幂等）
     */
    boolean unlike(Long mediaId, Long userId, Byte category);

    /**
     * 查询缓存中当前用户是否已赞该媒体。
     * <ul>
     *   <li>若 bitmap 存在且该用户位为 1 → 返回 Optional.of(true)，表示已点赞</li>
     *   <li>若 bitmap 不存在或该用户位为 0 → 返回 Optional.empty()，由调用方查 DB 并写回缓存</li>
     * </ul>
     *
     * @param mediaId 媒体ID
     * @param userId  用户ID
     * @return 已点赞时为 Optional.of(true)，需查 DB 时为 Optional.empty()
     */
    Optional<Boolean> getLikedFromCache(Long mediaId, Long userId);

    /**
     * 写回缓存：将当前用户对该媒体的已赞状态写入 bitmap（用于 DB 查询后回填）。
     * <p>
     * Redis 对不存在的 key 执行 SETBIT 时会自动创建 key，无需先创建 bitmap。
     * </p>
     *
     * @param mediaId 媒体ID
     * @param userId  用户ID
     * @param liked   true 已赞，false 未赞
     */
    void setLiked(Long mediaId, Long userId, boolean liked);

    /**
     * 媒体删除时清理点赞相关 Redis：从 3 个排行榜 ZSET 中 ZREM 该 mediaId，并 DEL 该媒体的已赞 bitmap。
     * 按 Redis_DESIGN.md 与 media 生命周期一致。
     *
     * @param mediaId 媒体ID
     */
    void evictMediaLikeData(Long mediaId);

    /**
     * 根据分类返回排行榜 ZSET key。
     */
    static String rankKeyByCategory(Byte category) {
        if (category == null) {
            return RANK_KEY_ALL;
        }
        return category == 0 ? RANK_KEY_0 : RANK_KEY_1;
    }

    // ---------- 分布式锁（SET NX，防击穿：查询已赞状态缓存未命中时同一 media 仅一人查 DB 并写回） ----------

    /**
     * 尝试获取「查询已赞状态」的分布式锁。锁粒度：同一 mediaId 仅允许一个请求执行查 DB + 写回。
     * 使用 SET key requestId EX TTL NX，仅持有者可用 requestId 释放/续期。
     *
     * @param mediaId   媒体ID
     * @param requestId 请求唯一标识（用于释放时校验，防止误释他人锁）
     * @return 获取成功返回 true，已被占用或异常返回 false
     */
    boolean tryLockLiked(Long mediaId, String requestId);

    /**
     * 释放「查询已赞状态」的分布式锁。仅当 Redis 中锁的值等于 requestId 时才删除（Lua 原子），防止误释其他请求的锁。
     */
    void unlockLiked(Long mediaId, String requestId);

    /**
     * 续期「查询已赞状态」的分布式锁。仅当锁的值等于 requestId 时才延长 TTL（Lua 原子），防止查 DB 时间过长导致锁过期。
     *
     * @return 续期成功返回 true，否则 false
     */
    boolean renewLockLiked(Long mediaId, String requestId);
}
