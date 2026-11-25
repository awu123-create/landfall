# 编码规范（Coding Standards）

本文件规定工具检查之外的跨语言通用工程标准。未标 **Judgement** 的条款均为 **Hard**：

- **Hard**：硬规矩。违反即阻断合入，评审须指明对应的规范文件与章节。
- **Judgement**：提醒项。评审时以“可能……”附理由提出，由作者决定是否调整。

**Judgement** 标题覆盖其后直到本节末尾的全部条款。**Smell baseline override** 声明本节规则替代的通用代码坏味道基准（供 `/code-review` 与 `.agents/skills/thermo-nuclear-code-quality-review/SKILL.md` 使用），本身不额外新增条款。

本文件的条款约束全仓库任何代码改动（Java、TypeScript/Vue、SQL、脚本）；具体技术栈细则归 `docs/standards/` 下对应文档，不在此重复。

**Judgement.** 大范围仅用于满足规范的纯结构调整须保持外部行为完全等价，单独开 PR 合入，不夹带在业务功能或缺陷修复 PR 中。

## 工具、校验与生成物（Tools, verification, and generated code）

格式化、静态检查、类型检查（`vue-tsc`）、架构测试（`ArchUnit`）与 CI 流水线覆盖的规则，以工具输出为最终裁决。规范文档只承载工具无法替代的工程判断；一条规则一旦能交由工具机械检查，就迁入工具并从文档删除。

- 抑制注释（`@SuppressWarnings`、`eslint-disable-next-line`、`@ts-expect-error`）必须在同行或紧邻行写明具体规则名与该处属于**误报**的原因。
- 抑制注释、排除规则或仅测试分支只允许用来处理工具误报；用它屏蔽真实问题让构建变绿属于违规，必须直接修代码。唯一例外是新启用检查规则时的既有问题基线清单，且后续提交只允许删减、不得新增。
- **生成代码入库、折叠与防漂移**：`packages/shared/openapi.json` 与 Orval 生成的 `packages/shared/src/api/generated/` **一并提交入库且严禁手工编辑**（根目录 `.gitattributes` 配置 `linguist-generated=true` 在 GitHub PR 中默认折叠）；遇 `generated/` 合并冲突时**永远不手解冲突**，直接重跑 `pnpm api:generate` 覆盖；CI 执行导出 OpenAPI → `pnpm api:generate` → `git diff --exit-code` 阻断漏同步（详见 [docs/standards/api-contract.md](./docs/standards/api-contract.md)）。

## 就近放置与抽取（Placement and extraction）

代码默认**就近放置**：表达式留在调用点，直到同一业务决策出现第 3 次（Rule of Three）暴露出真正共性再抽取，确保读者多跳的每一层都物有所值。

- 辅助函数从私有、放在调用方同文件起步；只有下一个作用域出现真实调用方时，才按 `同文件 → 同模块/同特性 → 公共包（common / packages/*）` 逐级外移，并带着对应测试一起移。
- 新增辅助函数或组件前先检索仓库是否已有同类实现；换个名字复制一份，或在调用点内联重写同一业务规则，同样视为重复。
- 共享业务常量、上限阈值、错误码与枚举集合在第 2 处使用时即提炼为命名常量或类型，不在多处手写重复字面量；同一条业务规则（如标签归一化、作品可见性判定）全仓只保留唯一实现。
- 仅原样转发参数或拼固定值、不增加任何业务决策也不收窄类型边界的函数、包装类、1:1 空接口（如单一实现的 `IXxxService`）或测试 helper 属于**无意义透传（pass-through）**，无论有多少调用方一律内联删除。

**Judgement.**

- 即使已出现第 3 处调用，如果函数体和函数名一样直白，继续留在调用点；只有封装复合判定、收窄类型或隐藏模块私有细节时才值得抽函数。
- 业务规则不同但长相相似的代码各自留在调用点；只有一个调用方的构造器直接原地写。
- 公共函数或组件一旦被迫加上 `boolean` / `mode` 开关参数，或长出只服务单一调用方的分支，说明抽象切错了：先内联回各调用方，删掉各自不需要的分支，再只抽真正共享的核心。
- 参数列表持续膨胀时先拆成职责单一的小函数；多数调用方都需要传多个可选配置时再引入参数对象或 Java `record`。

**Smell baseline override.** 本节规则替代通用 **Duplicated Code** 与 **Middle Man** 坏味道。

## 调用方原则（Callers）

同一 PR 新增的每一个对外面都必须在**该 PR 的生产代码里有真实调用方**。

- 公开类与方法、配置项、错误码、HTTP 路由、前端导出符号、数据库表列与索引，如果只在自身测试中被调用、或标注“预留给后续阶段”，一律删除，等第一个真实调用方落地时再引入（包 1 阶段 1 为全组提供的公共底座须配齐自身契约验证或首个接入调用点）。
- 代码自身不变量已保证永远走不到的死分支，连同强行构造它的测试一并删除；越过边界校验后对同一已保证条件做重复防御性判空同属此类。

**Judgement.**

- 当前只有 1 个成员的注册表、策略接口或查表直接写具体实现，出现第 2 个真实成员（如 `StorageAdapter` 的本地磁盘与 S3 两种实现）时再抽接口或查表。
- 当前 Issue 未要求的机制（额外审计表、预留功能开关、未使用的后台筛选项）留给需要它的 Issue。

**Smell baseline override.** 本节规则裁决 **Speculative Generality**。

## 显式失败（Failures）

失败必须**大声暴露（Loud failures）**，在后端、双前端与脚本中均禁止用静默兜底值掩盖问题。

- 按主键或唯一标识单点查询无结果时，返回明确的未命中结果或抛出对应领域未找到异常（如 `30001` / `40003` / `50002`），禁止用首元素、空壳对象（`?? items[0]`、`new PostResp()`）糊弄过去。
- 配置解析、入参校验、JSON 反序列化或数据库枚举读取遇到非法或未知值时当场报错，禁止用 `catch { return null }`、`.catch(() => undefined)` 或 `default:` 静默回退成零值；唯一允许静默降级的场景是公开白名单接口忽略浏览器残留的过期/失效 `OpaqueToken` 按游客放行（该规则属显式业务契约）。
- 基础设施与配置故障（数据库连接断开、Redis 异常、上游大模型超时 `60002`）按系统或上游异常上报，绝不伪装成用户输入错误或无权限；不同原因的业务拒绝各自使用独立 `ErrorCode`，不合并成笼统报错。

**Judgement.** 看到靠 `??`、`||`、`Optional.orElse(...)`、`catch` 或 `default` 填补默认值的代码，先追问它掩盖了哪条本应成立的不变量；若该值按契约必然存在，删掉兜底让错误直接暴露。

## 边界解析、单次处理与重试（Parse, handle once, retry）

Java 与前端的具体实现机制分别见 [docs/standards/java/code.md](./docs/standards/java/code.md) 与 [docs/standards/web/code.md](./docs/standards/web/code.md)。

- **边界解析一次（Parse）**：外部输入（HTTP 请求、环境变量、URL 查询参数、数据库读回字符串）在入口边界一次完成校验、裁剪归一化并转为强类型或枚举，越过边界后直接信任该类型；校验时做过归一化（如 `Tag` 去 `#` 转小写、去首尾空格）的字段，落库与后续传递必须使用归一化后的值。
- **错误只处理一次（Handle once）**：每个错误在“向上抛出返回 / 记录错误日志 / 按契约降级恢复”三者中**只选其一**，严禁在内部打完 `ERROR` 日志又把同一异常原样向上抛出造成重复刷屏。
- **出站有界与重试归上层（Retry）**：所有外部调用（大模型 HTTP 接口、S3 对象存储、FFmpeg 子进程）必须设置硬超时上限，底层客户端只发**单次尝试**；是否重试与退避策略由掌握幂等性与补偿状态机的上层任务决定。

**Judgement.** 越过入口边界后在深层代码反复重检同一格式条件，说明边界解析缺失或类型泄漏。

**Smell baseline override.** 仅对具有封闭值域（如 `Visibility`、`PostStatus`、`AuditStatus`）或明确业务不变量的概念报 **Primitive Obsession**；普通 `UUID` 与数值计数不在其列。

## 命名与注释（Names and comments）

- 领域概念在包名、类名、方法名、表列名、前端组件/文件名、错误文案与日志中一律使用 [GLOSSARY.md](./GLOSSARY.md) 定义的标准术语；使用词汇表 `_Avoid_` 列出的禁用同义词属于违规。
- 引用架构决策记录时写完整仓库路径（如 `docs/adr/0001-xxx.md`）或指向该文件的 Markdown 相对链接，不写无路径裸编号。

**Judgement.**

- 注释只写代码本身无法表达的信息：并发与事务边界约束、反直觉的业务原因（如为何下架严禁改 `visibility`）或对应 ADR 路径。
- Issue 与 PR 编号留在 commit 说明和 PR 正文，不写进源码注释，避免关单后沦为过时噪音。

## 记录同步（Records）

当改动影响对外行为或跨模块契约（REST 接口、错误码、环境变量、数据库表结构、CI 门禁），或新增 ADR 与维护 `docs/` 文档时，遵循 [docs/standards/records.md](./docs/standards/records.md)。
