# 记录 MySQL 中各数据表创建了哪些索引

---

## 1. `user` 用户表

**字段**：`id`, `login_name`, `password`, `nick_name`, `phone_number`, `level`, `state`, `update_time`

- **唯一索引**：`uk_user_login_name (login_name)`  
  用途：登录、找回密码按登录名查；保证登录名唯一。

- **唯一索引**：`uk_user_phone_number (phone_number)`  
  用途：注册手机号去重、找回密码校验。

- **唯一索引**：`uk_user_nick_name (nick_name)`  
  用途：保证昵称唯一。

- **联合索引**：`idx_user_level_id (level, id)`  
  用途：用户列表分页。`WHERE level != 0 ORDER BY id ASC`。

---

## 2. `media` 媒体资源表

**字段**：`id`, `uploader_id`, `file_hash`, `category`, `title`, `description`, `storage_path`, `cover_path`, `cover_status`, `state`, `update_time`, `like_count`, `like_count_update_time`

- **唯一索引**：`uk_media_uploader_file_hash (uploader_id, file_hash)`  
  用途：同一上传者+相同文件哈希防重复上传。`WHERE uploader_id = ? AND file_hash = ?`。

- **联合索引**：`idx_media_state_category_update_time (state, category, update_time)`  
  用途：公共区列表、待审核列表按类型筛选。`WHERE state = ? AND category = ? ORDER BY update_time DESC`。category 为 null 时仅用 state 前缀，需配合 `idx_media_state_update_time`。

- **联合索引**：`idx_media_state_update_time (state, update_time)`  
  用途：公共区/待审核列表「全部」筛选。`WHERE state = ? ORDER BY update_time DESC`。

- **联合索引**：`idx_media_uploader_state_category_update_time (uploader_id, state, category, update_time)`  
  用途：获取某用户上传列表。`WHERE uploader_id = ? [AND state/category 过滤] ORDER BY update_time DESC`。

- **联合索引**：`idx_media_state_like_update_time (state, like_count_update_time, like_count)`  
  用途：按点赞数排序，获取热门排行榜。`WHERE state = 0 AND like_count_update_time >= ? ORDER BY like_count DESC`。

---

## 3. `media_visible` 媒体可见权限(标签)表

**字段**：`id`, `media_id`, `user_id`

- **联合唯一索引**：`uk_media_visible_user_media (user_id, media_id)`  
  用途：按 user_id 查 media_id；防止同一 (user_id, media_id) 重复写入。JOIN 时 `ON mv.user_id = ?`。

- **普通索引**：`idx_media_visible_media_id (media_id)`  
  用途：按 media_id 删除 meida 可见关系（标签）。`WHERE media_id = ?`。

---

## 4. `user_like_record` 用户点赞记录表

**字段**：`id`, `user_id`, `media_id`

- **联合唯一索引**：`uk_user_like_record_user_media (user_id, media_id)`  
  用途：同一用户对同一 media 仅能点赞一次；按当前用户是否点赞当前 media 查询。`WHERE user_id = ? AND media_id = ?`。user_id 在前可先筛出该用户点赞的少数记录，查询更高效。

---

## 5. `tree_hole` 树洞表

**字段**：`id`, `owner_id`, `state`

- **唯一索引**：`uk_tree_hole_owner_id (owner_id)`  
  用途：一用户一树洞，按 owner_id 唯一查。`WHERE owner_id = ?`。

---

## 6. `tree_hole_blacklist` 树洞黑名单表

**字段**：`id`, `owner_id`, `blocked_user_id`, `state`, `reason`, `update_time`

- **联合索引**：`idx_tree_hole_blacklist_owner_blocked_state (owner_id, blocked_user_id, state)`  
  用途：校验是否被拉黑、拉黑/解除时查已有记录。`WHERE owner_id = ? AND blocked_user_id = ? [AND state = 0]`。

- **联合唯一索引**：`uk_tree_hole_blacklist_owner_blocked (owner_id, blocked_user_id)`  
  用途：同一用户在同一树洞仅能有一条被拉黑记录，防重复。

---

## 7. `tree_hole_message` 树洞留言表

**字段**：`id`, `tree_hole_id`, `tree_hole_owner_id`, `sender_id`, `sender_deleted`, `root_message_id`, `reply_message_id`, `content`, `state`, `update_time`

- **联合索引**：`idx_tree_hole_message_owner_root_state_time (tree_hole_owner_id, root_message_id, state, update_time)`  
  用途：留言列表（主人/非主人）先按 root_message_id 过滤再排序。`WHERE tree_hole_owner_id = ? AND state != 2 [AND root_message_id 条件] ORDER BY state ASC, update_time ASC`。

- **联合索引**：`idx_tree_hole_message_owner_sender_root (tree_hole_owner_id, sender_id, root_message_id)`  
  用途：非主人视角子查询，某用户在某树洞下投递的根留言。`WHERE tree_hole_owner_id = ? AND sender_id = ? AND root_message_id IS NULL`。

- **联合索引**：`idx_tree_hole_message_owner_sender_state (tree_hole_owner_id, sender_id, state)`  
  用途：防刷，某发送者在某树洞下存在未读消息则禁止投递新消息。`WHERE tree_hole_owner_id = ? AND sender_id = ? AND state = 0`。

---

## 8. `tree_hole_message_visible` 树洞消息可见表

**字段**：`id`, `message_id`, `owner_id`, `shared_by_user_id`

- **联合唯一索引**：`uk_tree_hole_message_visible_owner_message (owner_id, message_id)`  
  用途：按 owner_id 查分享列表；防止同一 (owner_id, message_id) 重复写入。`WHERE owner_id = ?`。

- **普通索引**：`idx_tree_hole_message_visible_message_id (message_id)`  
  用途：按 message_id 清理可见关系或联表。`WHERE message_id = ?`。

---
