# Java 后端测试规范（Java Testing Standard）

编写或评审 `backend/src/test/` 下的测试代码时阅读本文件。通用分级见 [CODING_STANDARDS.md](../../../CODING_STANDARDS.md)。

## 测试分层与唯一归属（Owners）

一条业务行为只在最贴近它的归属层测一次：纯内存单测已覆盖的细碎规则分支不在重型集成测试里重复罗列；依赖 PostgreSQL 或 Redis 真实语义的行为必须在真实中间件上验证。

| 层级 | 运行方式 | 归属验证内容 | 中间件依赖 |
| :--- | :--- | :--- | :--- |
| **模块架构门禁** | JUnit 5 + `ArchUnit` | 校验各 `com.landfall.module.<pkg>` 之间无循环依赖、无跨模块访问 `.internal.*`、Controller 不直接依赖 `Mapper`/`Entity` | 纯字节码扫描（毫秒级） |
| **纯业务规则单测** | JUnit 5 + Mockito / 轻量内存 Fake（**不启动 `@SpringBootTest`**） | `PostAccessPolicy` / `CommentAccessPolicy` 全权限矩阵、同步 DFA 匹配与标签归一化、6 态 `AuditStatus` 与阿瓦隆状态迁移、评分均值计算、UUIDv7 单调性 | 零外部依赖（毫秒级） |
| **存储与集成测试** | `@SpringBootTest` + MockMvc / WebTestClient | Flyway 全量迁移可执行性、部分唯一索引（`WHERE deleted_at IS NULL`）重注册、SQL `EXISTS` 互关好友可见性过滤、阿瓦隆 `SHADOW_HIDDEN` 查询隔离、并发点赞/评分原子计数、Redis `OpaqueToken` 全端踢下线与公开接口残留过期 Token 静默降级为游客 | **真实 PostgreSQL 16 + Redis 7**（Testcontainers 或本机/CI Compose 服务容器） |

**Judgement.** 保留任何测试前反问：删掉它之后，哪一类真实线上/演示 bug 会漏过去？答不出来就删掉或合并。

## 严禁使用 H2 冒充 PostgreSQL

全仓后端测试**严禁引入 H2 内存数据库**。本项目核心依赖 PostgreSQL 原生 `UUID`、`TIMESTAMPTZ`、`JSONB`、`ILIKE`、双向联合索引 `EXISTS` 以及 `WHERE deleted_at IS NULL` 部分唯一索引——H2 兼容模式既无法验证真实 Flyway SQL 语法，也会掩盖索引与事务隔离差异。

- 集成测试统一复用单例 Testcontainers 容器（PostgreSQL 16 + Redis 7）或连接 `docker-compose.yml` / GitHub Actions Service Containers 提供的真实实例。
- 测试用例之间必须保持数据隔离（使用 `@Transactional` 测试回滚，或在需要验证跨线程/异步提交可见性时使用唯一随机 `UUIDv7` 标识并在 `@AfterEach` 清理关联数据与独立 Redis 测试库 Key），确保任意顺序运行测试结果一致。

## 测试编写纪律（Test discipline）

- **测外部可观测行为**：断言方法返回值、抛出的 `BusinessException.getErrorCode()`、HTTP 状态码与响应 JSON 契约，或落库后的真实业务状态；不紧耦合断言内部私有辅助方法的调用次数。
- **外部不可控依赖用接口隔离**：大模型 OpenAI 兼容客户端与 FFmpeg 进程执行器在集成测试中通过接口替换或本地 `MockWebServer` 模拟绿区/黄区/红区/超时 `503` 响应，测试严禁依赖外网真实大模型 API Key。
- **禁止 `Thread.sleep` 盲等异步结果**：验证异步 AI 审核投递、用户软删后互动计数回算或超时补偿逻辑时，直接单测调度处理器方法，或使用 `Awaitility` 带超时条件轮询，禁止硬写 `Thread.sleep(...)` 拖慢构建并制造偶发失败。
- **命名清晰表达场景**：测试方法名写明“前置状态 + 执行动作 + 预期结果”（如 `canView_friendsPost_returnsFalseWhenFollowIsOneWay`），参数化多分支矩阵优先使用 `@ParameterizedTest` + `@MethodSource` / `@CsvSource`。
