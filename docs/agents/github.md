# Pull Requests 与分支协作（GitHub）

本仓库采用 **`dev` 日常集成主干 + `main` 里程碑发布分支** 模式：日常所有开发切片通过 Pull Request 合入 `dev`，里程碑验收稳定后再由 `dev` 合入 `main`。统一使用 `gh` CLI 操作；自动化脚本或 Agent 调用 `gh` 时统一前置 `GH_PROMPT_DISABLED=1`，避免非 TTY 环境下挂起。

## 目标分支（Target）

```bash
GH_PROMPT_DISABLED=1 gh pr create --base dev --title "..." --body "..."
```

- 日常开发禁止直接向 `dev` 或 `main` 推送提交，一律走独立分支与 PR。
- 单个 PR 只覆盖一个可独立验收的小切片；标题遵循 Conventional Commits（如 `feat(auth): ...`、`chore: ...`）。
- PR 正文遵循 `.github/pull_request_template.md` 模板填写。
- 合入 `dev` 默认使用 **Squash merge**（保持 `dev` 单切片线性历史）。合入后及时删除远端源分支、本地分支与对应 `.worktrees/<slug>` 目录。

## 源分支：更新与重开规则（Source branch）

操作前先确认当前分支对应的远端源分支 `<source>`：

```bash
git status -sb
```

`git status -sb` 输出形如 `local...origin/<source>` 时，`<source>` 为 PR 源分支；若没有 `...`，说明尚未建立上游跟踪，`<source>` 即当前本地分支名。查询已有 PR 时以 `<source>` 为准：

```bash
GH_PROMPT_DISABLED=1 gh pr list --head "<source>" --state all
```

- **存在 `OPEN` PR**：在原分支继续提交并运行 `git push` 更新 PR；若刚执行过 `rebase`，仅允许使用 `git push --force-with-lease`（严禁裸 `--force`）。
- **尚无远端分支**：首次推送时使用 `git push -u origin HEAD`。
- **仅存在 `MERGED` 或 `CLOSED` PR**：禁止复用旧分支继续提交。`dev` 上的 Squash merge 提交即文件真相，必须从最新 `origin/dev` 切出新的 `<type>/<id>-<slug>` 分支承载新改动，并清理旧分支与对应 worktree。
- **跨切片或无关改动**：不混入当前分支，从最新 `origin/dev` 另开新分支（并行开发使用独立 `.worktrees/<slug>`）。

## 拉齐 `dev` 与合入校验清单

当 `dev` 推进后，个人 / 单 Agent 分支通过 `rebase` 拉齐最新基线：

```bash
git fetch origin
git rebase origin/dev
git push --force-with-lease
```

（若多人或多 Agent 共享同一远程分支，改用 `git merge origin/dev` + 普通 `git push`，避免改写共享历史。）

在推送更新或合入 `dev` 前，逐项确认适用门禁通过：

1. **工作区与文档一致性**
   - `git status` 干净，无遗留临时文件、`.env` 密钥或跨切片改动；
   - 新增或修改 Markdown 文档时，相对路径链接全部有效；术语与既有 `GLOSSARY.md` / `docs/adr/`（若已存在）无静默冲突。
2. **后端与数据库迁移检查**（当仓库已落盘 `apps/backend` 且改动涉及后端或 SQL 时）
   - 通过 Maven 编译与测试（如 `./mvnw test` 或 `mvn test`）；
   - 严禁修改任何已合入 `dev` 的历史 Flyway 迁移脚本；同分支拉齐 `dev` 若发生迁移时间戳/序号冲突，仅顺延当前未合入分支的新增脚本并重跑迁移验证。
3. **前端与契约同步检查**（当仓库已落盘 `pnpm-workspace.yaml` 且改动涉及前端或 REST 契约时）
   - 变更后端接口契约（或拉齐 `dev` 带入契约更新）后，同步导出 `packages/shared/openapi.json` 并运行 `pnpm api:generate` 将 `packages/shared/src/api/generated/` 一并提交入库；遇 `generated/` 冲突严禁手解，直接重跑 `pnpm api:generate` 覆盖并确认 `git diff --exit-code` 为空；
   - 通过工作区类型检查与构建（如 `pnpm typecheck` / `pnpm build`）。
4. **PR 检查与单向门确认**
   - 若仓库已启用 GitHub Actions CI，确认 PR 检查全绿；
   - 涉及数据库表结构迁移、密钥/配置、删除级联等单向门改动时，在 PR 正文写明影响范围与回滚方案，经人工确认后再执行 Squash merge。
