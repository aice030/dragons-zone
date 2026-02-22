package com.dragons.core.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 媒体点赞 Redis 实现：位图记录已赞用户（offset=userId），ZSET 存储点赞数排行；点赞/取消点赞用 Lua 保证双 key 原子更新。
 *
 * @author aice
 * @since 2026-02-20
 */
@Slf4j
@Service
public class RedisCacheMediaLikeServiceImpl implements RedisCacheMediaLikeService {

    private final StringRedisTemplate stringRedisTemplate;

    /** 点赞：对两个 ZSET 分别 ZINCRBY 1 */
    private static final String LUA_LIKE =
            "redis.call('ZINCRBY', KEYS[1], 1, ARGV[1]); "
                    + "redis.call('ZINCRBY', KEYS[2], 1, ARGV[1]); "
                    + "return 1";

    /** 取消点赞：仅当 score > 0 时 ZINCRBY -1，保证不为负 */
    private static final String LUA_UNLIKE =
            "local s1 = redis.call('ZSCORE', KEYS[1], ARGV[1]); "
                    + "if s1 and tonumber(s1) > 0 then redis.call('ZINCRBY', KEYS[1], -1, ARGV[1]); end; "
                    + "local s2 = redis.call('ZSCORE', KEYS[2], ARGV[1]); "
                    + "if s2 and tonumber(s2) > 0 then redis.call('ZINCRBY', KEYS[2], -1, ARGV[1]); end; "
                    + "return 1";

    /** 释放锁：仅当 key 的值等于 requestId 时 DEL（防误释他人锁） */
    private static final String LUA_UNLOCK =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
    /** 续期锁：仅当 key 的值等于 requestId 时 EXPIRE */
    private static final String LUA_RENEW =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('expire', KEYS[1], ARGV[2]) else return 0 end";
    /** 媒体删除时：从 3 个 ZSET 中 ZREM mediaId，并 DEL 已赞 bitmap（与 media 生命周期一致） */
    private static final String LUA_EVICT_MEDIA_LIKE =
            "redis.call('ZREM', KEYS[1], ARGV[1]); "
                    + "redis.call('ZREM', KEYS[2], ARGV[1]); "
                    + "redis.call('ZREM', KEYS[3], ARGV[1]); "
                    + "return redis.call('DEL', KEYS[4])";

    /** 回滚点赞（幂等）：仅当 bitmap 该位为 1 时置 0 并双 ZSET -1（score>0 才减）。否则直接 return 0。 */
    private static final String LUA_ROLLBACK_LIKE =
            "local b = redis.call('GETBIT', KEYS[1], tonumber(ARGV[2])); "
                    + "if b == 0 then return 0 end; "
                    + "redis.call('SETBIT', KEYS[1], tonumber(ARGV[2]), 0); "
                    + "local s1 = redis.call('ZSCORE', KEYS[2], ARGV[1]); if s1 and tonumber(s1) > 0 then redis.call('ZINCRBY', KEYS[2], -1, ARGV[1]); end; "
                    + "local s2 = redis.call('ZSCORE', KEYS[3], ARGV[1]); if s2 and tonumber(s2) > 0 then redis.call('ZINCRBY', KEYS[3], -1, ARGV[1]); end; "
                    + "return 1";

    /** 回滚取消点赞（幂等）：仅当 bitmap 该位为 0 时置 1 并双 ZSET +1。否则直接 return 0。 */
    private static final String LUA_ROLLBACK_UNLIKE =
            "local b = redis.call('GETBIT', KEYS[1], tonumber(ARGV[2])); "
                    + "if b == 1 then return 0 end; "
                    + "redis.call('SETBIT', KEYS[1], tonumber(ARGV[2]), 1); "
                    + "redis.call('ZINCRBY', KEYS[2], 1, ARGV[1]); "
                    + "redis.call('ZINCRBY', KEYS[3], 1, ARGV[1]); "
                    + "return 1";

    /** 原子写回：SETBIT 位图 + 双 ZSET ZADD（先 DB 再 Redis 时用，保证位图与 score 同成功）。KEYS: likedKey, rankAll, rankCategory；ARGV: userId, mediaIdStr, score, bit(0/1)。 */
    private static final String LUA_SET_LIKED_AND_SCORE =
            "redis.call('SETBIT', KEYS[1], tonumber(ARGV[1]), tonumber(ARGV[4])); "
                    + "redis.call('ZADD', KEYS[2], ARGV[3], ARGV[2]); "
                    + "redis.call('ZADD', KEYS[3], ARGV[3], ARGV[2]); "
                    + "return 1";

    public RedisCacheMediaLikeServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /** 根据分类返回排行榜 ZSET key（实现细节，仅本类使用）。 */
    private static String rankKeyByCategory(Byte category) {
        if (category == null) {
            return RANK_KEY_ALL;
        }
        return category == 0 ? RANK_KEY_0 : RANK_KEY_1;
    }

    @Override
    public boolean like(Long mediaId, Long userId, Byte category) {
        if (mediaId == null || userId == null || category == null) {
            return false;
        }
        String likedKey = LIKED_BITMAP_PREFIX + mediaId;
        String mediaIdStr = mediaId.toString();
        long offset = userId;

        try {
            // 1.检查 bitmap 该用户位是否已为 1，避免重复点赞
            Boolean alreadyLiked = stringRedisTemplate.opsForValue().getBit(likedKey, offset);
            if (Boolean.TRUE.equals(alreadyLiked)) {
                log.info("like skip already liked mediaId={} userId={}", mediaId, userId);
                return false;
            }
            // 2.位图置 1，并更新排行榜 ZSET（all + category 双 key）
            stringRedisTemplate.opsForValue().setBit(likedKey, offset, true);
            List<String> keys = List.of(RANK_KEY_ALL, rankKeyByCategory(category));
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(LUA_LIKE);
            script.setResultType(Long.class);
            stringRedisTemplate.execute(script, keys, mediaIdStr);
            log.info("like success mediaId={} userId={} category={}", mediaId, userId, category);
            return true;
        } catch (Exception e) {
            log.error("like failed mediaId={} userId={} error={}", mediaId, userId, e.getMessage());
            try {
                stringRedisTemplate.opsForValue().setBit(likedKey, offset, false);
            } catch (Exception ex) {
                log.warn("like rollback SETBIT 0 failed mediaId={} userId={}", mediaId, userId);
            }
            throw e;
        }
    }

    @Override
    public boolean unlike(Long mediaId, Long userId, Byte category) {
        if (mediaId == null || userId == null || category == null) {
            return false;
        }
        String likedKey = LIKED_BITMAP_PREFIX + mediaId;
        String mediaIdStr = mediaId.toString();
        long offset = userId;

        try {
            // 1.检查 bitmap 该用户位是否为 1，未赞则幂等返回
            Boolean alreadyLiked = stringRedisTemplate.opsForValue().getBit(likedKey, offset);
            if (!Boolean.TRUE.equals(alreadyLiked)) {
                log.info("unlike skip was not liked mediaId={} userId={}", mediaId, userId);
                return false;
            }
            // 2.位图置 0，并对排行榜 ZSET 执行 -1（仅当 score>0，Lua 保证）
            stringRedisTemplate.opsForValue().setBit(likedKey, offset, false);
            List<String> keys = List.of(RANK_KEY_ALL, rankKeyByCategory(category));
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(LUA_UNLIKE);
            script.setResultType(Long.class);
            stringRedisTemplate.execute(script, keys, mediaIdStr);
            log.info("unlike success mediaId={} userId={} category={}", mediaId, userId, category);
            return true;
        } catch (Exception e) {
            log.error("unlike failed mediaId={} userId={} error={}", mediaId, userId, e.getMessage());
            throw e;
        }
    }

    /**
     * 从 Redis bitmap 查询当前用户是否已赞该媒体。
     * 每个 mediaId 对应一个 key：「bitmap 不存在」即该 key 在 Redis 中不存在
     *（该媒体从未有过任何点赞或查库写回，即没有任何缓存值）。
     * Redis bitmap 底层是 string：当 key 不存在时，GETBIT 任意 offset 也返回 0，因此无法区分
     *「key 不存在（media:liked:{mediaId} 整条缓存都没有）」与「key 存在但该用户位为 0（未赞）」，统一视为缓存未命中，返回 empty，由调用方查 DB 并写回。
     * 只有该用户位为 1（已赞）时返回 true，不查 DB。
     */
    @Override
    public Optional<Boolean> getLikedFromCache(Long mediaId, Long userId) {
        if (mediaId == null || userId == null) {
            return Optional.empty();
        }
        String likedKey = LIKED_BITMAP_PREFIX + mediaId;
        long offset = userId;
        try {
            // 步骤1：GETBIT，key 不存在或该位为 0 时 Redis 均返回 0
            Boolean bit = stringRedisTemplate.opsForValue().getBit(likedKey, offset);
            if (Boolean.TRUE.equals(bit)) {
                log.info("getLikedFromCache hit mediaId={} userId={} result=liked", mediaId, userId);
                return Optional.of(true);
            }
            log.info("getLikedFromCache miss mediaId={} userId={} need DB", mediaId, userId);
            return Optional.empty();
        } catch (Exception e) {
            log.error("getLikedFromCache failed mediaId={} userId={} error={}", mediaId, userId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 写回「当前用户对该媒体是否已赞」到 Redis bitmap。
     * 当该 key 不存在时（该媒体从未有过任何缓存写入）：
     * Redis 的 bitmap 底层是 string，不需要先创建 key。对不存在的 key 执行 SETBIT 时，Redis 会<em>自动创建</em>该 key，
     * 并按需扩展底层字符串以覆盖指定 offset，未设置的位默认为 0。因此查 DB 后写回时，只需调用 SETBIT：
     * 若 key 不存在则被隐式创建，只写入当前用户这一位（0 或 1），其它位保持为 0。
     *
     * @param liked true 已赞，false 未赞
     */
    @Override
    public void setLiked(Long mediaId, Long userId, boolean liked) {
        if (mediaId == null || userId == null) {
            return;
        }
        String likedKey = LIKED_BITMAP_PREFIX + mediaId;
        long offset = userId;
        try {
            // 写回当前用户这一位；key 不存在时 SETBIT 会自动创建
            stringRedisTemplate.opsForValue().setBit(likedKey, offset, liked);
            log.info("setLiked success mediaId={} userId={} liked={}", mediaId, userId, liked);
        } catch (Exception e) {
            log.error("setLiked failed mediaId={} userId={} liked={} error={}", mediaId, userId, liked, e.getMessage());
        }
    }

    @Override
    public void setScoreForMedia(Long mediaId, Byte category, long count) {
        if (mediaId == null) {
            return;
        }
        String member = mediaId.toString();
        try {
            stringRedisTemplate.opsForZSet().add(RANK_KEY_ALL, member, count);
            stringRedisTemplate.opsForZSet().add(rankKeyByCategory(category), member, count);
            log.info("setScoreForMedia success mediaId={} category={} count={}", mediaId, category, count);
        } catch (Exception e) {
            log.error("setScoreForMedia failed mediaId={} category={} count={} error={}", mediaId, category, count, e.getMessage());
        }
    }

    @Override
    public void setLikedAndScoreForMedia(Long mediaId, Long userId, Byte category, long count, boolean liked) {
        if (mediaId == null || userId == null) {
            return;
        }
        String likedKey = LIKED_BITMAP_PREFIX + mediaId;
        List<String> keys = List.of(likedKey, RANK_KEY_ALL, rankKeyByCategory(category));
        String mediaIdStr = mediaId.toString();
        String scoreStr = String.valueOf(count);
        String bitStr = liked ? "1" : "0";
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(LUA_SET_LIKED_AND_SCORE);
            script.setResultType(Long.class);
            stringRedisTemplate.execute(script, keys, userId.toString(), mediaIdStr, scoreStr, bitStr);
            log.info("setLikedAndScoreForMedia success mediaId={} userId={} category={} count={} liked={}", mediaId, userId, category, count, liked);
        } catch (Exception e) {
            log.error("setLikedAndScoreForMedia failed mediaId={} userId={} category={} count={} liked={} error={}", mediaId, userId, category, count, liked, e.getMessage());
        }
    }

    @Override
    public List<RedisCacheMediaLikeService.RankEntry> getRankMediaIdsWithScores(Byte category, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        // 根据 category 确认要获取的排行榜类别
        String rankKey = rankKeyByCategory(category);
        try {
            // 从 ZSET 获取指定数量的成员，members 和对应的 score
            Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                    .reverseRangeWithScores(rankKey, 0, limit - 1);
            if (tuples == null || tuples.isEmpty()) {
                log.warn("getRankMediaIdsWithScores no data category={} limit={}", category, limit);
                return List.of();
            }
            // 将 ZSET 返回的集合，规范为每个元素为 Long 类型的 mediaId 和 Long 类型的 likeCount 的列表（原 score 可能为 null），并排序
            List<RedisCacheMediaLikeService.RankEntry> result = new ArrayList<>(tuples.size());
            for (ZSetOperations.TypedTuple<String> t : tuples) {
                if (t == null || t.getValue() == null) {
                    continue;
                }
                long mediaId = Long.parseLong(t.getValue(), 10);
                long likeCount = t.getScore() != null ? t.getScore().longValue() : 0L;
                result.add(new RedisCacheMediaLikeService.RankEntry(mediaId, likeCount));
            }
            // 按 likeCount 降序排序，likeCount 相同则按 mediaId 升序排序
            result.sort(Comparator.comparingLong(RedisCacheMediaLikeService.RankEntry::likeCount).reversed()
                    .thenComparingLong(RedisCacheMediaLikeService.RankEntry::mediaId));
            log.info("getRankMediaIdsWithScores success category={} limit={} result={}", category, limit, result);
            return result;
        } catch (NumberFormatException e) {
            log.warn("getRankMediaIdsWithScores parse mediaId failed category={} limit={} error={}", category, limit, e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.error("getRankMediaIdsWithScores failed category={} limit={} error={}", category, limit, e.getMessage());
            return List.of();
        }
    }

    @Override
    public Optional<Long> getLikeCountFromRank(Long mediaId) {
        if (mediaId == null) {
            return Optional.empty();
        }
        try {
            Double score = stringRedisTemplate.opsForZSet().score(RANK_KEY_ALL, mediaId.toString());
            if (score == null) {
                return Optional.empty();
            }
            return Optional.of(score.longValue());
        } catch (Exception e) {
            log.warn("getLikeCountFromRank failed mediaId={} error={}", mediaId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void evictMediaLikeData(Long mediaId) {
        if (mediaId == null) {
            return;
        }
        String likedKey = LIKED_BITMAP_PREFIX + mediaId;
        String mediaIdStr = mediaId.toString();
        List<String> keys = List.of(RANK_KEY_ALL, RANK_KEY_0, RANK_KEY_1, likedKey);
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(LUA_EVICT_MEDIA_LIKE);
            script.setResultType(Long.class);
            stringRedisTemplate.execute(script, keys, mediaIdStr);
            log.info("evictMediaLikeData success mediaId={}", mediaId);
        } catch (Exception e) {
            log.error("evictMediaLikeData failed mediaId={} error={}", mediaId, e.getMessage());
        }
    }

    /** 撤销一次点赞（幂等）：仅当位图为 1 时置 0 并 ZSET -1。 */
    @Override
    public void rollbackLike(Long mediaId, Long userId, Byte category) {
        if (mediaId == null || userId == null || category == null) {
            return;
        }
        String likedKey = LIKED_BITMAP_PREFIX + mediaId;
        String mediaIdStr = mediaId.toString();
        String userIdStr = userId.toString();
        List<String> keys = List.of(likedKey, RANK_KEY_ALL, rankKeyByCategory(category));
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(LUA_ROLLBACK_LIKE);
            script.setResultType(Long.class);
            stringRedisTemplate.execute(script, keys, mediaIdStr, userIdStr);
            log.info("rollbackLike success mediaId={} userId={} category={}", mediaId, userId, category);
        } catch (Exception e) {
            log.error("rollbackLike failed mediaId={} userId={} category={} error={}", mediaId, userId, category, e.getMessage());
            throw e;
        }
    }

    /** 撤销一次取消点赞（幂等）：仅当位图为 0 时置 1 并 ZSET +1。 */
    @Override
    public void rollbackUnlike(Long mediaId, Long userId, Byte category) {
        if (mediaId == null || userId == null || category == null) {
            return;
        }
        String likedKey = LIKED_BITMAP_PREFIX + mediaId;
        String mediaIdStr = mediaId.toString();
        String userIdStr = userId.toString();
        List<String> keys = List.of(likedKey, RANK_KEY_ALL, rankKeyByCategory(category));
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(LUA_ROLLBACK_UNLIKE);
            script.setResultType(Long.class);
            stringRedisTemplate.execute(script, keys, mediaIdStr, userIdStr);
            log.info("rollbackUnlike success mediaId={} userId={} category={}", mediaId, userId, category);
        } catch (Exception e) {
            log.error("rollbackUnlike failed mediaId={} userId={} category={} error={}", mediaId, userId, category, e.getMessage());
            throw e;
        }
    }

    // ---------- 分布式锁（SET NX，粒度按 mediaId） ----------

    private static String buildLikedLockKey(Long mediaId) {
        return LOCK_MEDIA_LIKED_KEY_PREFIX + mediaId;
    }

    /**
     * 尝试获取「查询已赞状态」的分布式锁。SET key requestId EX TTL NX，锁粒度按 mediaId。
     */
    @Override
    public boolean tryLockLiked(Long mediaId, String requestId) {
        if (mediaId == null || requestId == null || requestId.trim().isEmpty()) {
            return false;
        }
        String key = buildLikedLockKey(mediaId);
        try {
            Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, requestId, LOCK_TTL_SECONDS, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(success)) {
                log.info("distributed lock acquired for liked mediaId={} requestId={}", mediaId, requestId);
                return true;
            }
            log.warn("distributed lock already held for liked mediaId={}", mediaId);
            return false;
        } catch (Exception e) {
            log.error("tryLockLiked failed mediaId={} requestId={} error={}", mediaId, requestId, e.getMessage());
            return false;
        }
    }

    /**
     * 释放「查询已赞状态」的分布式锁。仅当锁的值等于 requestId 时删除（Lua 原子）。
     */
    @Override
    public void unlockLiked(Long mediaId, String requestId) {
        if (mediaId == null || requestId == null || requestId.trim().isEmpty()) {
            return;
        }
        String key = buildLikedLockKey(mediaId);
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(LUA_UNLOCK);
            script.setResultType(Long.class);
            Long result = stringRedisTemplate.execute(script, Collections.singletonList(key), requestId);
            if (result != null && result > 0) {
                log.info("distributed lock released for liked mediaId={} requestId={}", mediaId, requestId);
            } else {
                log.warn("distributed lock release failed: lock not held by this request mediaId={} requestId={}", mediaId, requestId);
            }
        } catch (Exception e) {
            log.error("unlockLiked failed mediaId={} requestId={} error={}", mediaId, requestId, e.getMessage());
        }
    }

    /**
     * 续期「查询已赞状态」的分布式锁。仅当锁的值等于 requestId 时延长 TTL（Lua 原子）。
     */
    @Override
    public boolean renewLockLiked(Long mediaId, String requestId) {
        if (mediaId == null || requestId == null || requestId.trim().isEmpty()) {
            return false;
        }
        String key = buildLikedLockKey(mediaId);
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(LUA_RENEW);
            script.setResultType(Long.class);
            Long result = stringRedisTemplate.execute(script, Collections.singletonList(key), requestId, String.valueOf(LOCK_TTL_SECONDS));
            if (result != null && result > 0) {
                log.info("distributed lock renewed for liked mediaId={} requestId={}", mediaId, requestId);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("renewLockLiked failed mediaId={} requestId={} error={}", mediaId, requestId, e.getMessage());
            return false;
        }
    }
}
