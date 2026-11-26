# 接口契约规范（API Contract Standard）

新增或修改后端 Controller 路由、请求/响应 DTO、SpringDoc OpenAPI 注解、错误码或 `frontend/packages/shared/openapi.json` 时阅读本文件。调用方原则与通用分级见 [CODING_STANDARDS.md](../../CODING_STANDARDS.md)。

## 路由前缀与资源路径（Paths）

所有后端 REST 接口统一挂载 `/api/v1` 前缀，路径片段命名资源，查询条件与命令参数随 Query 或 Body 传递：

- **显式公开只读前缀 `/api/v1/public/**`**：游客可直接访问的只读接口（公开配置、热门标签、发现流、公开检索、公开作品详情、公开评论分页、公开个人主页）统一归入 `/api/v1/public/**` 并在安全配置中逐条显式放行，严禁按 HTTP `GET` 方法粗暴全局放行。
- **前台认证与需登录业务前缀**：`/api/v1/auth/**`、`/api/v1/posts/**`、`/api/v1/media/**`、`/api/v1/comments/**`、`/api/v1/users/**`、`/api/v1/ai/**`（静态媒体流读取端点单独声明白名单以兼容浏览器原生媒体标签与 HLS 分片）。
- **管理后台前缀 `/api/v1/admin/**`**：后台全部接口挂载于此，统一强制要求 `admin:access` 准入权限及对应细粒度 `PermissionCode`。
- 路径层级表达资源从属关系（如 `/api/v1/posts/{postId}/comments`），路径变量统一采用 camelCase 资源标识（`{postId}`、`{commentId}`、`{userId}`）；所有只读查询无论过滤条件多少均使用 `GET` + Query 参数。

**Judgement.**

- 非标准增删改的业务动作作为目标资源下的动词子资源表达（如 `POST /api/v1/admin/users/{userId}/reset-password`、`PUT /api/v1/admin/users/{userId}/status`）。
- 用户分步执行的两个独立动作拆为两个端点，不用 `mode` 或 `dryRun` 布尔字段把两份契约塞进同一个接口。

## 响应信封、DTO 与字段一致性（Bodies and envelopes）

- **显式泛型信封**：除二进制媒体流（HLS `.m3u8`/`.ts` 与 HTTP `206` Range 分片）外，所有 Controller 方法签名**必须显式返回 `ApiResponse<T>`**（列表分页返回 `ApiResponse<PageResult<T>>`，无业务数据写操作返回 `ApiResponse<Void>`）。禁止返回裸对象或裸 `String` 依赖全局 `ResponseBodyAdvice` 暗中包装，保证 SpringDoc 导出的 Schema 与真实 HTTP 响应完全一致。
- **信封与分页固定结构**：
  - `ApiResponse<T>` 固定包含 `code`（成功为 `0`）、`message`、`data`、`traceId`；请求头可选传 `X-Trace-Id`（仅接受 `^[A-Za-z0-9_-]{1,64}$`，缺失或非法时由服务端生成），服务端通过同名响应头 `X-Trace-Id` 与 `ApiResponse.traceId` 同步回传；
  - `PageResult<T>` 固定包含 `items`、`page`（从 `1` 起算）、`pageSize`（本次实际生效的页大小）、`total`、`hasMore`（时间流可附加可选 `cursor`）；
  - **分页入参契约**：`page` 与 `pageSize` 接受连续整数区间而非固定白名单（前端表格的 `10 / 20 / 50 / 100` 档位归 UI 预设）；缺省分别取 `1` 与可配置 `defaultSize`（默认 `20`）；`pageSize` 超过可配置 `maxSize`（默认 `100`）时静默钳位至 `maxSize` 并在 `PageResult.pageSize` 回传；`page < 1` 或 `pageSize < 1` 显式返回 `400` / `10400`。
- **DTO 命名与类型**：请求体使用 `<Operation>Req`，响应体使用 `<Operation>Resp`（Java 21 `record`），严禁将数据库 `Entity` 直接作为请求或响应类型暴露到契约层。
- **全契约同名同义**：同一领域概念在所有接口中保持相同字段名与类型——例如主键与关联 ID 在 JSON 中统一为 36 字符 UUID `string`（`postId`、`authorId`、`assetId`），所有时间字段统一为 ISO-8601 字符串，封闭状态集统一声明为枚举并在 `@Schema` 中暴露枚举值。
- 请求字段仅在服务端缺省即拒绝时标为必填（`@NotNull` / `@NotBlank`）；响应字段仅在每次返回均保证非空时标为 `required`，让 Orval 生成准确的 TypeScript 可空性。

## HTTP 状态码与错误码号段（Error codes）

HTTP 状态码表达传输与认证语义，业务分支判断**仅依赖 `ErrorCode` 数值码**，`message` 仅供人类阅读提示（前端逻辑严禁匹配 `message` 文本）：

| HTTP 状态码 | 适用语义 | 错误码号段归属（统一登记于公共 `ErrorCode` 枚举） |
| :--- | :--- | :--- |
| `200` | 业务执行成功（含阿瓦隆评论进入 `SHADOW_HIDDEN`） | `0` |
| `400` | 参数校验/反序列化失败或同步业务规则拒绝 | `10400` 通用参数错误；各包规则拦截归各自号段 |
| `401` | 未携带凭证（`20001`）或会话已过期/被踢下线（`20006`） | `2xxxx`（认证与会话） |
| `403` | 已登录但账号被封禁（`20004`）、缺后台准入（`20007`）或操作越权（`20003`） | `2xxxx`（RBAC 与权限） |
| `404` / `405` | 路由不存在（`10404`）、方法不支持（`10405`），或目标业务资源不存在/因可见性隐藏 | `1xxxx` 协议层；`3xxxx` 作品与媒体、`4xxxx` 评论与评分、`5xxxx` 社交用户 |
| `429` | 通用频控（`10429`）或个人每日 AI 助手额度耗尽（`60001`） | `1xxxx`、`6xxxx` |
| `500` / `503` | 内部未预期异常（`10500`）或上游大模型超时/未配密钥降级（`60002`） | `1xxxx`、`6xxxx` |

**5 位错误码百位子域分配表**（跨包并行防撞码；`1xxxx` 统一采用 `10000 + HTTP 状态码`）：

| 大段与归属 | 百位子段 | 覆盖范围与已登记基线码 |
| :--- | :--- | :--- |
| `1xxxx` 公共底座与协议层 | `104xx` / `105xx` | `10000 + HTTP 状态码`：`10400` 参数校验/反序列化、`10404` 路由不存在、`10405` 方法不支持、`10429` 通用频控、`10500` 系统内部异常 |
| `2xxxx` 认证与账号（包 1） | `200xx` / `201xx` | `200xx` 会话与权限（`20001`/`20003`/`20004`/`20006`/`20007`）；`201xx` 注册/登录/改密业务拒绝（`20101` 重名、`20102` 重邮、`20103` 账密错误、`20104` 原密码错误） |
| `3xxxx` 内容、媒体与同步拦截（包 2 / 3 / 7） | `300xx` / `301xx` / `302xx` | `300xx` 作品与同步敏感词（`30001`/`30004`）；`301xx` 媒体上传/转码/先传后绑（包 2）；`302xx` 标签归一化与限制（包 2/3） |
| `4xxxx` 评论、评分与互动（包 4） | `400xx` / `401xx` | `400xx` 评分与评论核心（`40001`/`40003`）；`401xx` 回复层级与互动冲突扩展 |
| `5xxxx` 社交与关注（包 5） | `500xx` | `500xx` 用户主页与关注关系（`50002`） |
| `6xxxx` 大模型与 AI 代理（包 7） | `600xx` | `600xx` 配额与上游降级（`60001`/`60002`） |
| `7xxxx` 后台治理与 RBAC（包 1 / 6 / 7） | `700xx` / `701xx` / `702xx` | `700xx` RBAC 角色与权限树（包 1，`70001`）；`701xx` 敏感词库与人工复审治理（包 7）；`702xx` 后台用户/作品/评论/标签治理（包 6） |

登记与治理铁律：
- 全后端只在公共 `ErrorCode` 枚举登记唯一真相，新增错误码必须在所属百位子域内登记且**同 PR 有真实抛出或映射方**（禁止提前写空壳常量）；
- 已对外使用的错误码退役后禁止复用于其他语义；
- 按 `ErrorCode` 绑定的 HTTP 状态码区分日志与告警责任：`4xx` 不打 `ERROR` 日志；`500` 在全局异常处理器唯一记录一次完整堆栈 `log.error` 并对外脱敏；`503` 归属外部依赖故障，独立记录降级日志而不伪装为用户输入错误或内部 bug。

## OpenAPI 快照与 Orval 生成流水线（Contract generation）

1. **契约与生成代码一并提交入库（严禁手改）**：后端新增或修改 Controller 路由、参数、DTO 字段或枚举时，在同一提交中导出更新 `frontend/packages/shared/openapi.json`，并在 `frontend/` 下运行 `pnpm api:generate`（Orval 按 `@Tag` 拆分，仅生成纯请求函数与 TypeScript 类型，排除二进制媒体流端点）将 `frontend/packages/shared/src/api/generated/` 一并提交入库。GitHub 上看代码可直接追踪生成的函数与类型签名；根目录 `.gitattributes` 通过 `frontend/packages/shared/src/api/generated/** linguist-generated=true` 自动折叠 PR 中的生成文件 Diff 噪音。
2. **合并冲突零手解规矩**：拉齐 `origin/dev` 时只要 `frontend/packages/shared/src/api/generated/` 出现冲突（或双边均修改过接口契约），**永远不手解 `generated/` 冲突**，对齐 `openapi.json` 后直接在 `frontend/` 下重跑一行 `pnpm api:generate` 全量覆盖。
3. **CI 防漏同步与类型门禁**：CI 流水线依次执行导出 OpenAPI → `pnpm --dir frontend api:generate` → `git diff --exit-code`（忘记重新生成提交直接红灯阻断），并在 `frontend/` 下运行全工作区 `vue-tsc --noEmit` 确保双端调用点零类型错误。
