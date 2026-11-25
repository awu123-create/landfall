# Issue tracker: GitHub

本仓库（`awu123-create/landfall`）的活任务（Issue、子任务、阻塞依赖与 Triage 状态）均托管在 GitHub Issues，所有操作统一通过 `gh` CLI 执行。

## 操作约定

- **创建 Issue**：`gh issue create --title "..." --body "..."`；多行正文使用 heredoc。
- **读取 Issue**：`gh issue view <number> --json number,title,body,labels,comments`。
- **列出 Issue**：`gh issue list --state open --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'`，按需附加 `--label` 与 `--state` 过滤条件。
- **建立父子关系（Sub-issue）**：使用 `gh issue create --parent <parent> ...`，或在创建后执行 `gh issue edit <parent> --add-sub-issue <child>`（需 `gh` 2.94+）。旧版 `gh` 使用 `gh api --method POST repos/<owner>/<repo>/issues/<parent>/sub_issues -F sub_issue_id=<child-db-id>`（数据库 ID 获取方式见下方**阻塞关系**）。若仓库未启用 sub-issues，在子 Issue 正文首行写入 `Part of #<parent>`。
- **阻塞关系（Blocking）**：优先使用 GitHub 原生 **issue dependencies**（在 UI 中直接可见）。通过 `gh issue create --blocked-by <n>,<n> ...` 或 `gh issue edit <child> --add-blocked-by <blocker>` 添加依赖（需 `gh` 2.94+）。旧版 `gh` 使用 `gh api --method POST repos/<owner>/<repo>/issues/<child>/dependencies/blocked_by -F issue_id=<blocker-db-id>`，其中 `<blocker-db-id>` 为阻塞方 Issue 的数字型数据库 ID（通过 `gh api repos/<owner>/<repo>/issues/<n> --jq .id` 获取，**不要**使用 `#number` 或 `node_id`）。若原生依赖不可用，回退为在子 Issue 正文首行写入 `Blocked by: #<n>, #<n>`。所有阻塞方均关闭后，该任务解除阻塞。
- **统计未关闭的阻塞项**：`gh api repos/<owner>/<repo>/issues/<n> --jq .issue_dependencies_summary.blocked_by`（仅统计 open 状态；返回 `0` 表示可开工）。`gh issue view <n> --json blockedBy` 会包含已关闭项，使用时需过滤：`--jq '[.blockedBy.nodes[] | select(.state == "OPEN")] | length'`。
- **发表评论**：`gh issue comment <number> --body "..."`。
- **增删标签**：`gh issue edit <number> --add-label "..."` / `--remove-label "..."`。
- **关闭 Issue**：先用 `gh issue comment <number> --body "..."` 说明原因，再执行 `gh issue close <number>`（对已关闭的 Issue 使用 `gh issue close --comment` 会丢弃评论且仍返回退出码 0）。

在本地克隆目录中，`gh` 会根据 `git remote -v` 自动推断目标仓库。

仅向用户自己的仓库提交 Issue，除非用户拥有 `mattpocock/skills`，否则切勿向该上游 skill 仓库提交。在 fork 仓库中，`gh` 可能默认指向 upstream 父仓库；会话中首次执行 `gh issue create` 前，先用 `gh repo view --json nameWithOwner` 确认目标仓库，若不是预期仓库先向用户确认。

## 分支命名与 Issue 关联

开发分支从最新 `origin/dev` 切出，统一按 `<type>/<id>-<slug>` 命名：

- **`<type>`**：遵循 Conventional Commits 类型（`feat`、`fix`、`refactor`、`docs`、`test`、`style`、`chore`、`ci`、`perf`）。
- **`<id>`**：
  - 关联 GitHub Issue 时写作 `issue-<number>`（例如 `feat/issue-12-auth-token`）；
  - 无对应 Issue 时写作 `no-ticket`（例如 `chore/no-ticket-engineering-workflow`），仅适用于零散工程化或文档初始化，业务功能与缺陷优先建立 Issue。
- **`<slug>`**：简短英文 kebab-case 主题词。

本地多任务或多 Agent 并行开发时，在 `.worktrees/<slug>` 独立 worktree 中切出对应 `<type>/<id>-<slug>` 分支。

PR 关联与关单语义：
- 单任务 PR 在正文写 `Closes #<number>`（或 `Fixes #<number>`）；
- 属于父 Issue 的子任务 PR 写 `Part of #<parent>` 与 `Closes #<child>`，避免提前关闭父 Issue。

## 将 PR 纳入 Triage 入口

**PRs as a request surface: no.** _（如果本仓库把外部 PR 当作需求入口，改为 `yes`；`/triage` 会读取此开关。）_

设为 `yes` 时，外部 PR 按与 Issue 相同的标签和状态流转，命令替换为 `gh pr` 对应形式：

- **读取 PR**：先执行 `gh pr view <number>`，再执行 `gh pr view <number> --comments`（非 TTY 环境下 `--comments` 只输出评论不含正文），以及 `gh pr diff <number>` 查看改动。
- **列出待 triage 的外部 PR**：`gh api --paginate 'repos/{owner}/{repo}/pulls?state=open' --jq '.[] | select(.author_association | IN("OWNER","MEMBER","COLLABORATOR") | not) | {number, title, author: .user.login, author_association, labels: [.labels[].name]}'`。
- **评论 / 标签 / 关闭**：`gh pr comment`、`gh pr edit --add-label` / `--remove-label`、`gh pr close`。

GitHub 的 Issue 和 PR 共用同一套编号空间，遇到裸编号（如 `#42`）时先用 `gh pr view 42` 尝试解析，失败再回退 `gh issue view 42`。

## 当 skill 要求 "publish to the issue tracker"

按上方**创建 Issue** 约定创建对应的 GitHub Issue。

**`docs/specs/` 规划边界**：仓库 `docs/specs/` 下的方案纲要、路线图与规格草稿仅作为规划上下文，**不构成自动创建 GitHub Issue 的授权**；只有明确选定某个切片进入交付时，才将对应任务发布到 GitHub Issues。活工单的排期、阻塞关系、负责人与完成状态只维护在 GitHub Issues 中，不在 `docs/specs/` 内镜像同步。

## 当 skill 要求 "fetch the relevant ticket"

按上方**读取 Issue** 约定拉取内容。

## Wayfinding 操作

供 `/wayfinder` 使用。**Map** 是一个主 Issue，各子任务（**child**）作为其子 Issue 挂接：

- **Map**：单个带有 `wayfinder:map` 标签的 Issue，正文记录 Notes / Decisions-so-far / Fog：`gh issue create --label wayfinder:map`。
- **子任务（Child ticket）**：以 GitHub sub-issue 形式挂在 Map 下（见上方**建立父子关系**）。若未启用 sub-issues，将子任务加入 Map 正文的任务列表，并在子 Issue 顶部注明 `Part of #<map>`。标签按类型打 `wayfinder:<type>`（`research` / `prototype` / `grilling` / `task`）。认领后指派给负责开发者。
- **阻塞关系**：沿用上方**阻塞关系（Blocking）**约定。
- **可推进前沿查询（Frontier query）**：列出 Map 下所有 open 状态的 child（`gh issue list --state open`，范围限定为该 Map 的 sub-issues 或任务列表），排除存在未关闭阻塞项（见**统计未关闭的阻塞项**或正文 `Blocked by` 行）或已有指派人的条目；按 Map 顺序取第一项。
- **认领（Claim）**：`gh issue edit <n> --add-assignee @me`，作为会话中的首次写操作。
- **完结（Resolve）**：先 `gh issue comment <n> --body "<answer>"`，再 `gh issue close <n>`，并在 Map 的 Decisions-so-far 追加结论摘要与链接。
