# Landfall（见屿）

- 产品范围、架构取舍纲要与 8 人工作包边界（规划上下文，非现行代码树）：`docs/specs/design-proposal.md`。
- GitHub PR、源分支更新/重开与合入 `dev` 校验：`docs/agents/github.md`（PR 正文模板 `.github/pull_request_template.md`）。

## Agent skills

### Issue tracker

活任务（Issue、子任务与阻塞依赖）托管在 GitHub Issues（`gh` CLI），开发分支按 `<type>/<id>-<slug>` 命名。See `docs/agents/issue-tracker.md`.

### Triage labels

使用默认 5 个标准 Triage 标签（`needs-triage`、`needs-info`、`ready-for-agent`、`ready-for-human`、`wontfix`）。See `docs/agents/triage-labels.md`.

### Domain docs

单上下文（single-context）布局：根目录 `GLOSSARY.md` 与 `docs/adr/`。See `docs/agents/domain.md`.
