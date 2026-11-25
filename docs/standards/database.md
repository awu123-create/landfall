# 数据库与迁移规范（Database Schema Standard）

新增或修改 PostgreSQL 表、列、索引、约束、种子数据或 Flyway 迁移脚本时阅读本文件。调用方原则与通用分级见 [CODING_STANDARDS.md](../../CODING_STANDARDS.md)；后端持久化读写规范见 [docs/standards/java/code.md](./java/code.md)；真库迁移验证见 [docs/standards/java/testing.md](./java/testing.md)。

表只存储代码真实读取的**业务事实**，由数据库约束兜底核心不变量。

## 表、列、主键与约束（Tables and constraints）

- **调用方原则（Callers）**：新增的表、列、索引、枚举值或预置权限种子行，必须在同一 PR 中有生产代码读或写（包 1 阶段 1 地基迁移除外），禁止提前建无人读写的预留列。
- **全局主键与时间类型**：
  - 所有业务表与关联表的主键、外键统一采用 PostgreSQL 原生 `UUID` 类型，应用层统一通过包 1 封装的 **UUIDv7** 生成器（毫秒时间戳有序前缀）赋值，避免随机 UUIDv4 造成 B-Tree 页分裂并消除前端 JS 整数精度丢失；
  - 所有时间列统一使用 `TIMESTAMPTZ`（默认 `now()`）；按自然日筛选或按天统计聚合统一以 `Asia/Shanghai` 时区折算左闭右开区间 `[startDate 00:00:00, endDate + 1 day 00:00:00)`。
- **外键、唯一性与 `CHECK` 约束**：
  - 表间引用显式声明 `REFERENCES` 外键；`Comment` 的一级评论顶层根标识 `root_id` 与 `parent_id` 必须存真 `NULL`，**严禁使用哨兵值 `0` 或空 UUID** 绕过自关联外键；
  - 幂等互动与关联关系必须在库层建联合唯一约束（如 `(post_id, user_id)` 点赞/收藏/评分、`(comment_id, user_id)` 评论赞、`(follower_id, followee_id)` 关注），靠数据库唯一约束拦截并发重复写入；
  - 封闭状态集与数值边界（如 `score BETWEEN 1 AND 5`、非负计数字段）优先辅以 `CHECK` 约束。
- **部分唯一索引与显式软删过滤对齐**：
  - 支持软删除重注册的唯一字段（如 `sys_user.username`、`sys_user.email`）使用部分唯一索引 `UNIQUE (...) WHERE deleted_at IS NULL`；
  - 查询代码中的过滤谓词必须与部分索引 `WHERE` 子句完全一致；**全仓禁止启用 MyBatis-Plus 全局 `@TableLogic` 自动改写 SQL**，避免多态业务状态（`PUBLISHED` / `SHADOW_HIDDEN` / `TAKEDOWN` / `DELETED`）与 `deleted_at` 被框架暗中拼接错误条件。
- **禁存竞态派生布尔列**：能由现有关联索引实时判定的关系不加冗余状态列——`user_follows` **严禁添加 `is_mutual` 列**，统一建 `(follower_id, followee_id)` 与 `(followee_id, follower_id)` 双向索引并通过 SQL `EXISTS` 判定 `MutualFollow`。

**Judgement.**

- 主体定义与只追加流水分离：`posts` / `comments` 存当前状态，`audit_records` 与 `user_activity_logs` 只追加不可变事件记录。
- 查询每次都会兜底成同一固定常量的列（如计数列 `0`、重试次数 `0`），直接声明 `NOT NULL DEFAULT 0`；仅当“缺失本身具备业务含义”时才设为可空（如未绑定作品时的 `media_assets.post_id`、未软删时的 `deleted_at`）。

## Flyway 迁移纪律（Migrations）

- **DDL 唯一入口**：一切建表、改列、索引、约束与基础角色/权限种子数据均通过 `backend/src/main/resources/db/migration/` 下的 Flyway 脚本管理；**严禁开启任何 ORM 自动建表或自动改表配置**。
- **时间戳命名规范**：统一命名为 `VYYYYMMDD_NN__<module>_<description>.sql`（如 `V20261126_01__auth_init_rbac.sql`），采用正向演进（Forward-only），不编写 Flyway `U` 回滚脚本。
- **已合入脚本铁律不可改**：任何已合入 `dev` 的历史 SQL 脚本**严禁修改哪怕一个空格或注释**（否则直接击穿全组本地库与云端库的 Flyway checksum 校验）。对已合入表结构的调整一律新建更高版本号的 `V...sql` 增量脚本。
- **拉齐 `dev` 排序检查**：个人分支 `rebase origin/dev` 后，须确认本分支新增脚本的版本号严格晚于 `origin/dev` 上最新迁移版本；若发生同日同序号撞号，保留 `dev` 已有文件不动，用 `git mv` 顺延本分支尚未合入的新脚本序号并重跑真库迁移测试。

**Judgement.** 同一尚未合入 `dev` 的 PR 内，若本分支新增的迁移脚本存在疏漏，直接原地修改该未合入脚本，不在同一 PR 内叠加第二个补丁迁移文件。
