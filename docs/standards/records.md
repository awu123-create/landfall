# 记录同步规范（Records Standard）

当改动改变系统对外行为或契约（REST API、`frontend/packages/shared/openapi.json`、错误码、环境变量、数据库表结构、测试或 CI 门禁），或新增 ADR 与维护 `docs/` 文档时阅读本文件。通用分级见 [CODING_STANDARDS.md](../../CODING_STANDARDS.md)。

## 权威记录与同 PR 同步

改变系统对外行为或跨工作包契约的改动，必须在**同一个 PR** 中同步更新所有声明该行为的权威记录，不留“后续再补文档”的尾巴。

Landfall 的权威记录清单：

- [GLOSSARY.md](../../GLOSSARY.md)：统一领域术语、英文代码标识与 `_Avoid_` 禁用同义词；
- `docs/adr/NNNN-*.md`：记录关键架构与领域设计取舍的 ADR；
- [README.md](../../README.md)：项目定位、已落地的仓库结构、本地启动与校验命令；
- `docs/ARCHITECTURE.md`（随包 1 阶段 1 真实工程脚手架同 PR 创建）：反映已落地代码树的模块拓扑、数据流与核心状态机；
- [CODING_STANDARDS.md](../../CODING_STANDARDS.md)、`docs/standards/**` 与 `docs/agents/**`：编码、测试、数据库、接口契约与协作规范。

## 无双规并存

- 被新决策取代的规则直接改写或删除；在 ADR 中如需保留演进脉络，统一使用**过去时态**描述旧方案并链接取代它的新 ADR。
- 禁止只在文档顶部加一句“部分内容已变更”，而在正文中继续以现行有效口吻保留旧规则或已淘汰组件名（如已统一为 `shadcn-vue` 后仍残留旧组件库说明）。

## 以当前代码树为真相

- 权威记录只描述当前分支磁盘上真实存在的代码结构、表迁移与可执行命令。`README.md` 与未来的 `docs/ARCHITECTURE.md` 不得预先填入尚未创建的目录、路由或脚本；先落盘代码，或在同一 PR 内同步提交代码与文档。
- 跨工作包总体蓝图、分阶段范围与分工规划放在 `docs/specs/`（如 [docs/specs/design-proposal.md](../specs/design-proposal.md)），仅作为规划上下文，不冒充现行代码树，也不在其中镜像活工单排期或完成状态（见 [docs/agents/issue-tracker.md](../agents/issue-tracker.md)）。

## 延后落地的架构提案（Proposed ADR）

- 若某项跨包架构决策先定方案、后续切片再写代码，新增 ADR 的状态标为 `Proposed（提案中）`，并在影响章节列出代码实现 PR 合入时需同步更新的权威记录。
- 实现代码合入 `dev` 时，同 PR 将该 ADR 状态更新为 `Accepted（已采纳）` 并同步刷新相关权威文档。

**Judgement.**

- 新文档如果只是把另一份权威记录已承载的内容重抄一遍（重复表结构、重复错误码表、重复启动命令），应删除重复内容并改为指向唯一权威文件的链接。
- 单次 PR 的排查过程、方案对比草稿或评审纪要属于 PR 正文或 Issue 评论，不落盘 `docs/`；只有后续维护者持续需要且代码本身无法自明的设计约束才写入 `docs/`。
