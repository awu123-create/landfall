# Landfall（见屿）

Landfall（见屿）是一个面向图文、单视频与纯文本创作的多媒体内容展示与社交互动社区，整体作为一个单一限界上下文运作。用户端与管理后台共享同一套模块化单体后端、关系型数据模型、会话权限底座与人机协同内容治理状态机。

> 仓库当前处于脚手架落地前的初始化阶段，以下核心术语提前锁死全组 8 个工作包在数据库表列、Java 类型、OpenAPI 契约与 Vue 特性模块中的统一命名。

## Language

### Content & Media（内容与媒体）

**Post（作品）**:
创作者发布的单篇社区内容聚合根，形态封闭为纯文本（`TEXT`）、多图图文（`IMAGE`，1~9 图）或单视频（`VIDEO`）三者之一，严禁单篇混排图片与视频。
_Avoid_: `Note`, `Article`, `Topic`, `Moment`, `FeedItem`（特指列表卡片投影时可作视图名，不替代领域实体名）

**MediaAsset（媒体素材）**:
用户上传的图片或视频资源实体，遵循“先上传登记 `UNBOUND`、发布作品时校验归属并转为 `BOUND`”生命周期，承载原始地址、缩略图、封面以及播放模式（`IMAGE` / `RANGE` / `HLS`）与转码状态。
_Avoid_: `PostMedia`, `Attachment`, `UploadFile`, `MediaResource`

**Visibility（可见性）**:
作者本人对 `Post` 设定的公开意愿（`PUBLIC` 公开、`FRIENDS` 仅互关好友可见、`PRIVATE` 仅自己可见），与平台治理状态完全独立，任何审核或下架操作不得改写该值。
_Avoid_: `Scope`, `PrivacyLevel`, 与 `PostStatus` 混用

**PostStatus / CommentStatus（作品状态 / 评论状态）**:
内容在平台侧的展示生命周期。`PostStatus` 取 `PUBLISHED` / `TAKEDOWN` / `DELETED`；`CommentStatus` 取 `NORMAL` / `SHADOW_HIDDEN` / `TAKEDOWN` / `DELETED`。其中 `TAKEDOWN` 表示违规下架（仅作者本人与管理员可见）。
_Avoid_: 将 `TAKEDOWN` 命名为 `Banned`, `Blocked`, `Offline`, `Rejected`

**Tag（标签）**:
经去首尾空白、剥离前导 `#`、英文转小写及敏感词检测归一化后的作品主题词。
_Avoid_: `Label`（保留给 GitHub Issue Triage 标签与 UI 表单标签）, `Category`, `Hashtag`

### Governance & Moderation（内容治理与审核）

**ShadowHidden（影子隐藏）**:
评论命中一般级（`NORMAL`）敏感词后进入的仿「阿瓦隆」展示态（`CommentStatus.SHADOW_HIDDEN`）：提交接口正常返回成功，查询时仅发评人本人与管理员可见，其他用户与游客不可见，同时进入人工待复审队列。
_Avoid_: `Folded`, `Muted`, `GhostDeleted`, `SoftBlocked`

**AuditStatus（审核状态）**:
`Post` 与 `Comment` 共用的 6 态人机协同合规流转状态：`PENDING_AI`、`AI_PASSED`、`MANUAL_REVIEW`、`AI_REJECTED`、`MANUAL_PASSED`、`MANUAL_REJECTED`。
_Avoid_: `ReviewStatus`, `CheckStatus`, `VerifyState`

**AuditRecord（审核记录）**:
每次同步 DFA 拦截/转审、异步 AI 语义复查或管理员人工裁决产生的只追加审计流水，记录触发源（`DFA` / `AI` / `ADMIN`）、裁决结论（`PASS` / `REVIEW` / `REJECT`）与原因。
_Avoid_: `ModerationLog`, `ReviewHistory`, `AuditLog`（避免与用户活跃日志混淆）

**SensitiveWord（敏感词）**:
后台维护并经 Redis 版本号同步热更新的 DFA 前缀树词条，按处置强度分为高危硬拦截（`HIGH`）与一般敏感词（`NORMAL`）。
_Avoid_: `BadWord`, `StopWord`, `BlockWord`

### Interaction & Social（互动与社交）

**Comment（评论）**:
挂接在 `Post` 下的两级平铺互动回复：一级评论 `rootId` 与 `parentId` 均为 `null`；所有子回复统一归属对应一级评论的 `rootId`，并通过 `parentId` 与 `replyToUserId` 标识回复对象。
_Avoid_: `Reply`（作为独立表或独立顶级实体）, 使用哨兵值 `0` 表示一级评论

**Collect（收藏）**:
登录用户将可见 `Post` 加入个人收藏列表的幂等互动行为与关联记录（对应统计字段 `collectCount`）。
_Avoid_: `Bookmark`, `Favorite`, `Star`, `Save`

**Rating（评分）**:
登录用户对他人 `Post` 提交的 1~5 星整数打分（每用户每作品唯一，可改分，禁止作者自评），驱动 `ratingSum`、`ratingCount` 与 `ratingAvg` 事务更新。
_Avoid_: `Score`（保留给推荐排序综合热度分）, `Vote`, `Rank`

**Follow & MutualFollow（单向关注与互关好友）**:
用户间的单向关注关系（`followerId -> followeeId`）；当双方同时存在单向 `Follow` 记录时实时构成 **MutualFollow（互关好友）**，作为解锁 `FRIENDS` 可见作品的唯一判定依据。
_Avoid_: `Subscribe`, 独立 `Friendship` 表, `is_mutual` 冗余布尔列

**ActivityLog（活跃行为日志）**:
采集登录、发帖、评论、点赞、收藏、评分关键事件的行为流水（`user_activity_logs`），支撑后台日活（DAU）与趋势聚合统计。
_Avoid_: `AuditRecord`, `AccessLog`, `SystemLog`

### Identity & Access（身份认证与访问控制）

**OpaqueToken（不透明会话令牌）**:
登录成功后签发的加密随机 Base64URL 会话凭据，通过 `Authorization: Bearer <token>` 传递，由服务端 Redis 维护会话详情与单用户在线令牌集合以支持滑动续期和全端即时踢下线。
_Avoid_: `JwtToken`, `AccessToken` + `RefreshToken` 双令牌对

**PermissionCode（权限点编码）**:
5 表树形 RBAC 模型中的细粒度功能权限字符串标识（如后台准入权限 `admin:access`、审核裁决权限 `admin:audit:review`）。
_Avoid_: 在业务代码中硬编码角色名判断具体资源操作权限

**AccessPolicy（数据访问策略）**:
集中判定当前访问者对目标资源是否具备读取或修改资格的领域策略组件（`PostAccessPolicy`、`CommentAccessPolicy`），统一收口作者本人、后台权限、下架状态与互关好友可见性规则。
_Avoid_: 在各 Controller 或查询调用点散落手写 `authorId.equals(userId)` 越权判断
