# MySQL 索引建议清单

说明：
- 基于当前 **实体类表结构** 与 **ServiceImpl/Mapper 实际查询** 整理，不包含主键索引。
- 表名与字段以 `entity` 包下 `@TableName` / `@TableField` 为准。
- 索引名为建议名，可按现有规范调整。若索引已存在，请勿重复创建。

---

## 1. `user`（用户表）

**字段**：`id`, `login_name`, `password`, `nick_name`, `phone_number`, `level`, `state`, `update_time`

**查询场景**（来自 UserServiceImpl）：
- 登录 / 找回密码：`WHERE login_name = ?`
- 注册：检查 `login_name`、`phone_number` 是否已存在
- 按 id 查询：`getById`（主键已覆盖）

- **唯一索引**：`uk_user_login_name (login_name)`  
  - 用途：登录、找回密码按登录名查；保证登录名唯一。  
  - SQL：`CREATE UNIQUE INDEX uk_user_login_name ON user (login_name);`

- **唯一索引**：`uk_user_phone_number (phone_number)`  
  - 用途：注册手机号去重、找回密码校验。  
  - SQL：`CREATE UNIQUE INDEX uk_user_phone_number ON user (phone_number);`

- **唯一索引**：`uk_user_nick_name (nick_name)`  
  - 用途：保证昵称唯一。  
  - SQL：`CREATE UNIQUE INDEX uk_user_nick_name ON user (nick_name);`

- **普通索引（可选）**：`idx_user_state (state)`  
  - 用途：按状态筛选用户（如后台/黑名单列表）。  
  - SQL：`CREATE INDEX idx_user_state ON user (state);`

---

## 2. `media`（媒体资源表）

**字段**：`id`, `uploader_id`, `file_hash`, `category`, `title`, `description`, `storage_path`, `cover_path`, `state`, `update_time`, `like_count`, `like_count_update_time`

**查询场景**（来自 MediaServiceImpl、MediaVisibleServiceImpl）：
- 上传幂等：`WHERE uploader_id = ? AND file_hash = ?`
- 公共区列表：`WHERE state = 0 [AND category = ?]`，`ORDER BY update_time DESC, id DESC`，分页
- 专区列表：**INNER JOIN media_visible** 实现（`media m JOIN media_visible mv ON m.id = mv.media_id AND mv.user_id = ?`），`WHERE m.state = 0 [AND m.category = ?]`，`ORDER BY m.update_time DESC, m.id DESC`，分页；利用 media_visible 的 `(user_id, media_id)` 与 media 主键，无需为专区单独建 `(id, state, category, update_time)` 索引。
- 我的上传：`WHERE uploader_id = ? [AND state 过滤，如 state IN (0,1,2,3,4) 或 state != 5] [AND category = ?]`，`ORDER BY update_time DESC`，分页；直接查 media 表，不需 id；可选按 state 过滤（如排除已删除）。

- **唯一索引（建议）**：`uk_media_uploader_file_hash (uploader_id, file_hash)`  
  - 用途：同一上传者+相同文件哈希防重复上传。  
  - SQL：`CREATE UNIQUE INDEX uk_media_uploader_file_hash ON media (uploader_id, file_hash);`

- **联合索引（建议）**：`idx_media_state_category_update_time (state, category, update_time)`  
  - 用途：公共区列表：按 state、可选 category 筛选，按 update_time DESC、id DESC 排序分页。公共区不按 id 查，id 仅作排序次键；InnoDB 二级索引会带主键，故 DDL 不写 id 仍能按 (update_time, id) 有序。不做覆盖索引，列表所需 title、cover_path 回表读取。  
  - **字段顺序说明**：采用 `(state, category, update_time)` 而非 `(id, state, ...)`，原因有二：（1）**最左前缀**：公共区列表只有 `WHERE state = 0 [AND category = ?]`，无 id 条件；（2）**使用频率**：默认展示公共区，优先保证其走索引。  
  - SQL：`CREATE INDEX idx_media_state_category_update_time ON media (state, category, update_time);`

- **联合索引（建议）**：`idx_media_uploader_state_category_update_time (uploader_id, state, category, update_time)`  
  - 用途：我的上传列表（个人管理）：按 uploader_id、可选 state 过滤（如排除已删除）、可选 category，按 update_time DESC 排序分页。直接查 media 表，不需 id；state 放在 uploader_id 之后以支持多状态展示。  
  - SQL：`CREATE INDEX idx_media_uploader_state_category_update_time ON media (uploader_id, state, category, update_time);`

- **联合索引（可选）**：`idx_media_state_like_update_time (state, like_count_update_time, like_count)`  
  - 用途：在 MySQL 内做「过去一段时间内的热度排行榜」，例如 `WHERE state = 0 AND like_count_update_time >= ? ORDER BY like_count DESC`。  
  - SQL：`CREATE INDEX idx_media_state_like_update_time ON media (state, like_count_update_time, like_count);`

- **性能与监控**：专区列表已通过 **INNER JOIN media_visible** 实现，利用 `media_visible(user_id, media_id)` 与 media 主键，无需额外建专区专用索引。若遇性能问题再考虑单独索引 `(id, state, category, update_time)` 或进一步优化。

---

## 3. `media_visible`（媒体可见权限表）

**字段**：`id`, `media_id`, `user_id`

**查询场景**（来自 MediaVisibleServiceImpl、MediaServiceImpl）：
- 专区列表：INNER JOIN 时按 `user_id` 关联（`ON mv.user_id = ?`），需 `(user_id, media_id)` 支撑
- 删除媒体时：按 `media_id` 删除可见关系

- **联合唯一索引（建议）**：`uk_media_visible_user_media (user_id, media_id)`  
  - 用途：按 user_id 查 media_id 高效；防止同一 (user_id, media_id) 重复写入。  
  - SQL：`CREATE UNIQUE INDEX uk_media_visible_user_media ON media_visible (user_id, media_id);`

- **普通索引（建议）**：`idx_media_visible_media_id (media_id)`  
  - 用途：按 media_id 删除可见关系时加速。  
  - SQL：`CREATE INDEX idx_media_visible_media_id ON media_visible (media_id);`

---

## 4. `tree_hole`（树洞表）

**字段**：`id`, `owner_id`, `state`

**查询场景**（来自 TreeHoleServiceImpl、TreeHoleMessageServiceImpl、TreeHoleMessageVisibleServiceImpl）：
- 按主人查树洞：`WHERE owner_id = ?`

- **唯一索引（建议）**：`uk_tree_hole_owner_id (owner_id)`  
  - 用途：一用户一树洞，按 owner_id 唯一查。  
  - SQL：`CREATE UNIQUE INDEX uk_tree_hole_owner_id ON tree_hole (owner_id);`

---

## 5. `tree_hole_blacklist`（树洞黑名单表）

**字段**：`id`, `owner_id`, `blocked_user_id`, `state`, `reason`, `update_time`

**查询场景**（来自 TreeHoleMessageServiceImpl）：
- 投递前校验是否被拉黑：`WHERE owner_id = ? AND blocked_user_id = ? AND state = 0`

- **联合索引（建议）**：`idx_tree_hole_blacklist_owner_blocked_state (owner_id, blocked_user_id, state)`  
  - 用途：按树洞主人+被拉黑用户+状态查是否拉黑。  
  - SQL：`CREATE INDEX idx_tree_hole_blacklist_owner_blocked_state ON tree_hole_blacklist (owner_id, blocked_user_id, state);`

- **联合唯一索引（可选）**：若业务上同一 (owner_id, blocked_user_id) 只保留一条有效记录，可建 `uk_tree_hole_blacklist_owner_blocked (owner_id, blocked_user_id)` 防重复。
  - 用途：同一用户只能被同一树洞拉黑一次，防重复
  -SQL: `CREATE UNIQUE INDEX uk_tree_hole_blacklist_owner_blocked ON tree_hole_blacklist (owner_id, blocked_user_id);`

---

## 6. `tree_hole_message`（树洞留言表）

**字段**：`id`, `tree_hole_id`, `tree_hole_owner_id`, `sender_id`, `sender_deleted`, `root_message_id`, `reply_message_id`, `content`, `state`, `update_time`

**查询场景**（来自 TreeHoleMessageServiceImpl）：
- 留言列表（主人/投递者）：`WHERE tree_hole_owner_id = ? AND state != 2 [AND sender_id = ? ...]`，`ORDER BY state ASC, update_time ASC`，分页
- 防刷：`WHERE tree_hole_owner_id = ? AND sender_id = ? AND state = 0`（count 未读）
- 回复校验：`WHERE id = ? AND tree_hole_owner_id = ?`
- 发送者删留言：`WHERE id = ? AND sender_id = ?`
- 主人改状态：`WHERE id = ? AND tree_hole_owner_id = ?`

- **联合索引（建议）**：`idx_tree_hole_message_owner_state_time (tree_hole_owner_id, state, update_time)`  
  - 用途：留言列表按树洞主人、状态、更新时间排序分页。  
  - SQL：`CREATE INDEX idx_tree_hole_message_owner_state_time ON tree_hole_message (tree_hole_owner_id, state, update_time);`

- **联合索引（建议）**：`idx_tree_hole_message_owner_sender_state (tree_hole_owner_id, sender_id, state)`  
  - 用途：防刷：某发送者在某树洞下是否存在未读（state=0）。  
  - SQL：`CREATE INDEX idx_tree_hole_message_owner_sender_state ON tree_hole_message (tree_hole_owner_id, sender_id, state);`

- **普通索引（可选）**：`idx_tree_hole_message_sender_id (sender_id)`  
  - 用途：按发送者查“我投递的留言”等。  
  - SQL：`CREATE INDEX idx_tree_hole_message_sender_id ON tree_hole_message (sender_id);`

---

## 7. `tree_hole_message_visible`（树洞消息可见表）

**字段**：`id`, `message_id`, `owner_id`, `shared_by_user_id`

**查询场景**（来自 TreeHoleMessageVisibleServiceImpl、TreeHoleMessageVisibleMapper.xml）：
- 分享幂等：`WHERE owner_id = ? AND message_id = ?`
- 分享收件箱：`WHERE v.owner_id = ?` JOIN tree_hole_message，`ORDER BY v.id DESC` 分页
- 删除某条留言时若需清理可见关系：按 `message_id` 删

- **联合唯一索引（建议）**：`uk_tree_hole_message_visible_owner_message (owner_id, message_id)`  
  - 用途：按 owner_id 查分享列表；防止同一 (owner_id, message_id) 重复写入。  
  - SQL：`CREATE UNIQUE INDEX uk_tree_hole_message_visible_owner_message ON tree_hole_message_visible (owner_id, message_id);`

- **普通索引（建议）**：`idx_tree_hole_message_visible_message_id (message_id)`  
  - 用途：按 message_id 清理可见关系或联表。  
  - SQL：`CREATE INDEX idx_tree_hole_message_visible_message_id ON tree_hole_message_visible (message_id);`

- **普通索引（可选）**：`idx_tree_hole_message_visible_shared_by (shared_by_user_id)`  
  - 用途：按分享者查“我分享过的消息”。  
  - SQL：`CREATE INDEX idx_tree_hole_message_visible_shared_by ON tree_hole_message_visible (shared_by_user_id);`

---

## 汇总：建议优先创建的索引

| 表名 | 索引名 | 类型 | 字段 |
|------|--------|------|------|
| user | uk_user_login_name | 唯一 | login_name |
| user | uk_user_phone_number | 唯一 | phone_number |
| user | uk_user_nick_name | 唯一 | nick_name |
| media | uk_media_uploader_file_hash | 唯一 | uploader_id, file_hash |
| media | idx_media_state_category_update_time | 普通 | state, category, update_time |
| media | idx_media_uploader_state_category_update_time | 普通 | uploader_id, state, category, update_time |
| media_visible | uk_media_visible_user_media | 唯一 | user_id, media_id |
| media_visible | idx_media_visible_media_id | 普通 | media_id |
| tree_hole | uk_tree_hole_owner_id | 唯一 | owner_id |
| tree_hole_blacklist | idx_tree_hole_blacklist_owner_blocked_state | 普通 | owner_id, blocked_user_id, state |
| tree_hole_message | idx_tree_hole_message_owner_state_time | 普通 | tree_hole_owner_id, state, update_time |
| tree_hole_message | idx_tree_hole_message_owner_sender_state | 普通 | tree_hole_owner_id, sender_id, state |
| tree_hole_message_visible | uk_tree_hole_message_visible_owner_message | 唯一 | owner_id, message_id |
| tree_hole_message_visible | idx_tree_hole_message_visible_message_id | 普通 | message_id |

以上均与当前实现类中的查询条件、排序、分页及唯一约束需求一致。
