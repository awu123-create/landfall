# Java 后端代码规范（Java Code Standard）

编写或评审 `backend/` 下的非测试 Java 代码时阅读本文件。通用规则与分级定义见 [CODING_STANDARDS.md](../../../CODING_STANDARDS.md)；REST 契约与错误码号段见 [docs/standards/api-contract.md](../api-contract.md)；表结构与 Flyway 规则见 [docs/standards/database.md](../database.md)。

## 模块化单体分包与可见性边界（Module boundaries）

后端在 `com.landfall` 下采用 **Spring Modulith 风格**按业务域垂直分包（`common`、`storage`、`module.{auth,media,post,interaction,social,admin,moderation,ai,stat}`），告别顶层水平铺开 `controller / service / mapper / entity` 的做法：

```text
com.landfall.module.post/                # 模块公开边界（其他模块唯一允许引用的包）
├── PostAccessPolicy.java                # 跨模块公开策略或门面 Service（public）
├── PostSummaryDto.java                  # 跨模块传递的不可变 Java 21 record（public）
└── internal/                            # 模块私有实现（ArchUnit 强门禁拦截外部访问）
    ├── PostController.java              # HTTP 路由入口（package-private 或 internal 内置）
    ├── PostService.java                 # 具体 @Service 业务类（不建 IXxxService 空接口）
    ├── PostMapper.java                  # extends BaseMapper<PostEntity>
    └── PostEntity.java                  # @TableName("posts") 持久化实体，严禁逃出 internal/
```

- **跨模块硬隔离**：模块 A（如 `admin`、`interaction`、`ai`）只允许通过构造器注入调用模块 B **根包**公开的 Service / `AccessPolicy` 方法及不可变 `record` DTO / Spring 事件；**严禁跨模块 `import` 他人 `.internal.*` 下的任何类（尤其是他人的 `Mapper` 与 `Entity`）**。该边界由常驻 `ArchUnit` 架构测试在 `mvn test` 阶段机械强制执行。
- **禁止 `IXxxService + XxxServiceImpl` 反模式**：Spring Boot 默认基于 CGLIB 代理具体类，单实现的业务服务直接编写具体的 `@Service` 类，**禁止建立 1:1 透传接口对**，更**严禁继承 MyBatis-Plus 的 `IService<Entity>` / `ServiceImpl<Mapper, Entity>`**（防止自动暴露 40 余个绕过权限与敏感词校验的裸表读写方法）。仅当系统内真有多个可替换实现时（如 `StorageAdapter` 的 `LocalDiskStorageAdapter` 与 `S3StorageAdapter`）才定义接口。
- **持久化细节零外泄**：`Controller` 只负责 HTTP 参数绑定（`@Valid`）、读取当前登录上下文、调用本模块 Service 并包装返回 `ApiResponse<XxxResp>`；**严禁在 `Controller` 中直接注入 `Mapper`、拼装 `LambdaQueryWrapper` 或将 `Entity` 暴露为接口入参/出参**。
- **DTO 统一使用 Java 21 `record`**：所有 HTTP 请求体（`*Req`）、响应体（`*Resp`）与跨模块传输对象统一声明为不可变 `record`；`Entity ↔ record` 转换使用模块内显式静态工厂或轻量转换方法，不引入重型反射拷贝工具（如 `BeanUtils.copyProperties`，编译期无法发现字段名拼写错位或类型不兼容）。

**Judgement.** 单文件超过 500~800 行或混杂读写两条重逻辑（如 `post` 的写链路与复杂多维检索读链路）时，按读写职责在 `.internal` 内拆分为聚焦的服务类（如 `PostCommandService` 与 `PostQueryService`）。

## 依赖注入与配置绑定（Construction and configuration）

- 全面使用**构造器注入**（显式构造器或 Lombok `@RequiredArgsConstructor` 配合 `private final` 字段），**严禁字段 `@Autowired` 或 `@Resource` 注入**，确保依赖不可变且单测可直接 `new` 构造无需启动 Spring 容器。
- 外部化配置按模块聚合为 `@ConfigurationProperties`（配合 `@Validated` 启动期强校验），避免在业务类中散落 `@Value("${...}")` 魔法字符串。

## 数据访问、事务与并发安全（Persistence and transactions）

- **类型安全条件构造**：模块内单表动态查询一律使用 `LambdaQueryWrapper` / `LambdaUpdateWrapper`（如 `Wrappers.<PostEntity>lambdaQuery().eq(PostEntity::getStatus, ...)`），**严禁使用字符串硬编码列名的 `QueryWrapper` / `UpdateWrapper`**，严禁将未校验的前端入参拼入 `.apply()` 或 `.last()`。
- **分页可配置上限与禁 N+1**：分页默认值（默认 `20`）与上限（默认 `100`）统一经 `PaginationProperties`（`landfall.pagination.*`）配置并在启动期校验 `1 <= defaultSize <= maxSize`；所有分页入口通过 `PaginationProperties.resolve(page, pageSize)` 归一化为 `PageQuery`（缺省取默认值，`pageSize > maxSize` 静默钳位至 `maxSize`，`page < 1` 或 `pageSize < 1` 显式抛 `BusinessException` 返回 `400` / `10400`）；`PageResult<T>` 仅守结构不变量、不硬编码上限。列表关联作者昵称头像、标签集合、当前用户是否已赞/互关等批量数据，必须通过 `IN` 批量查询内存组装或 Mapper XML 关联 SQL 一次取回，**严禁在 `for` / `stream` 循环内逐行查库或查 Redis**。
- **短事务红线**：`@Transactional(rollbackFor = Exception.class)` 只包裹纯数据库读写原子单元；**严禁在数据库事务内发起外部大模型 HTTP 调用、对象存储大文件上传或同步执行 FFmpeg 进程**（先落库提交状态或使用事务提交后钩子/异步任务投递，避免慢 I/O 耗尽数据库连接池）。
- **并发计数与幂等更新**：点赞、收藏、评论数与评分汇总（`rating_sum`、`rating_count`、`rating_avg`）先靠唯一索引防重（捕获唯一约束冲突幂等处理或返回明确状态），再在同事务内使用 SQL 原子增减（`SET like_count = like_count + 1`），禁止“Java 读出旧数字 + 1 后整行覆盖写回”导致并发丢数。

## 双层权限把守（Security and access policy）

- **第一层（功能级）**：公开接口显式列于安全白名单（携带失效 `OpaqueToken` 静默清除上下文按游客放行，绝不抛 `401`）；后台接口统一校验 `admin:access` 与 `@PreAuthorize("hasAuthority('...')")`。
- **第二层（数据级 `AccessPolicy`）**：涉及 `Post` 与 `Comment` 可见性、归属权判断时，所有模块必须调用集中策略类 `PostAccessPolicy` / `CommentAccessPolicy`（覆盖详情、评论列表、互动写入、收藏夹、信息流 SQL 口径、AI 总结 6 类入口），禁止在业务代码里各自手写 `if (!post.getAuthorId().equals(userId))`。下架处置只改 `status = TAKEDOWN`，严禁触碰 `visibility`。

## 异常处理与日志追踪（Errors and logging）

承接 [CODING_STANDARDS.md](../../../CODING_STANDARDS.md) 的**单次处理（Handle once）**规则：

1. **业务拒绝直接抛 `BusinessException`**：参数不合法、资源不存在、越权、敏感词拦截、配额超限等预期内分支直接 `throw new BusinessException(ErrorCode.XXX)`（或带简明上下文说明），**抛出点禁止打 `log.error` / `log.warn`**。
2. **全局唯一异常出口**：公共层 `@RestControllerAdvice` 统一捕获——`BusinessException` 与参数校验异常映射为对应 HTTP 状态码及 `ApiResponse`（不打印异常堆栈刷屏）；未预期的系统异常在此处**唯一记录一次 `log.error` 完整堆栈**，对外统一返回脱敏的 HTTP `500` + `ErrorCode` `10500`，严禁把 SQL 语句、类名或堆栈泄漏给前端。
3. **`traceId` 全链路贯通**：入口 `OncePerRequestFilter` 生成或提取 `traceId` 写入 SLF4J `MDC`（请求结束 `finally` 清除），同时填入返回的 `ApiResponse.traceId`，保证前端报错弹窗的 `traceId` 可在服务端日志一键检索。
4. **敏感信息脱敏**：严禁将明文密码、`password_hash`、`OpaqueToken` 原文、`LLM_API_KEY` 或数据库连接串写入任何级别日志。
