package com.dragons.core.cache;

import java.util.List;
import java.util.Optional;

/**
 * 媒体点赞 Redis 缓存服务（media:rank:* ZSET + media:liked:* 位图）。
 * <p>
 * 按 Redis_DESIGN.md：点赞/取消点赞先改 Redis（Lua）→ 发 MQ → 消费者事务落库，失败回滚 Redis。
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
     * 将指定媒体在排行榜 ZSET（all + category）中的 score 设为给定点赞数。
     * 用于「先 DB 再 Redis」同步：落库后以 media.like_count 回写缓存。ZADD 语义：存在则更新，不存在则插入。
     *
     * @param mediaId  媒体ID
     * @param category 媒体分类 0=图片，1=视频
     * @param count    点赞数（通常来自 DB media.like_count）
     */
    void setScoreForMedia(Long mediaId, Byte category, long count);

    /**
     * 原子写回：位图（该用户已赞/未赞）+ 双 ZSET 的 score。
     * 用于「先 DB 再 Redis」时保证位图与点赞数同时成功或同时不写（Lua 脚本）。
     *
     * @param mediaId  媒体ID
     * @param userId   用户ID（位图 offset）
     * @param category 媒体分类 0=图片，1=视频
     * @param count    点赞数（ZSET score，通常来自 DB media.like_count）
     * @param liked    true 已赞，false 未赞
     */
    void setLikedAndScoreForMedia(Long mediaId, Long userId, Byte category, long count, boolean liked);

    /**
     * 媒体删除时清理点赞相关 Redis：从 3 个排行榜 ZSET 中 ZREM 该 mediaId，并 DEL 该媒体的已赞 bitmap。
     * 按 Redis_DESIGN.md 与 media 生命周期一致。
     *
     * @param mediaId 媒体ID
     */
    void evictMediaLikeData(Long mediaId);

    /**
     * 回滚一次「点赞」（幂等）：仅当位图该用户位为 1 时置 0 并双 ZSET -1（score&gt;0 才减）；否则不操作。
     */
    void rollbackLike(Long mediaId, Long userId, Byte category);

    /**
     * 回滚一次「取消点赞」（幂等）：仅当位图该用户位为 0 时置 1 并双 ZSET +1；否则不操作。
     */
    void rollbackUnlike(Long mediaId, Long userId, Byte category);

    /**
     * 排行榜单项：媒体ID与点赞数（来自 ZSET score）。
     */
    record RankEntry(long mediaId, long likeCount) {}

    /**
     * 从排行榜 ZSET 取 Top N（按点赞数降序），带 score（likeCount）。
     *
     * @param category 分类：null=全部，0=图片，1=视频（对应 media:rank:all / 0 / 1）
     * @param limit    取前几条（建议 size+10 以便过滤 state!=0 后仍能凑满 size）
     * @return 按点赞数降序的 (mediaId, likeCount) 列表，可能不足 limit 条
     */
    List<RankEntry> getRankMediaIdsWithScores(Byte category, int limit);

    /**
     * 按 mediaId 从排行榜 ZSET 取点赞数（用于详情/列表展示）。先读 ZSET，未在榜时再用 DB（media:core 不存 likeCount）。
     *
     * @param mediaId 媒体ID
     * @return 有 score 时 Optional.of(likeCount)，未在榜时 Optional.empty()
     */
    Optional<Long> getLikeCountFromRank(Long mediaId);

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
