# Landfall（见屿）

- 产品范围、架构取舍纲要与 8 人工作包边界（规划上下文，非现行代码树）：`docs/specs/design-proposal.md`。
- 任何代码改动必读 `CODING_STANDARDS.md`，再按场景读对应规范：
  - Java 后端（`backend/`）：代码与模块边界见 `docs/standards/java/code.md`，单测与真库集成测试见 `docs/standards/java/testing.md`。
  - 双前端与共享包（`frontend/apps/web-user/`、`frontend/apps/web-admin/`、`frontend/packages/ui/`、`frontend/packages/shared/`）：代码、状态与样式分层见 `docs/standards/web/code.md`，校验与测试见 `docs/standards/web/testing.md`。
  - REST 接口契约、错误码或 `frontend/packages/shared/openapi.json`：`docs/standards/api-contract.md`。
  - PostgreSQL 表结构与 Flyway 迁移：`docs/standards/database.md`。
  - 变更对外行为/契约、新增 ADR 或维护 `docs/`：`docs/standards/records.md`。
  - 严苛结构与可维护性审查（Thermo-nuclear review）：`.agents/skills/thermo-nuclear-code-quality-review/SKILL.md`。
- GitHub PR、源分支更新/重开与合入 `dev` 校验：`docs/agents/github.md`（PR 正文模板 `.github/pull_request_template.md`）。

## Agent skills

### Issue tracker

活任务（Issue、子任务与阻塞依赖）托管在 GitHub Issues（`gh` CLI），开发分支按 `<type>/<id>-<slug>` 命名。See `docs/agents/issue-tracker.md`.

### Triage labels

使用默认 5 个标准 Triage 标签（`needs-triage`、`needs-info`、`ready-for-agent`、`ready-for-human`、`wontfix`）。See `docs/agents/triage-labels.md`.

### Domain docs

单上下文（single-context）布局：根目录 `GLOSSARY.md` 与 `docs/adr/`。See `docs/agents/domain.md`.
