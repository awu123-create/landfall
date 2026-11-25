# Web 前端测试与校验规范（Web Testing Standard）

校验前端或契约改动、以及在 `packages/shared/`、`packages/ui/`、`apps/web-user/`、`apps/web-admin/` 编写 Vitest 测试时阅读本文件。通用分级见 [CODING_STANDARDS.md](../../../CODING_STANDARDS.md)。

## 每次改动的硬门禁（Verifying a change）

当工作区脚手架落盘后，任何涉及前端源码或 `packages/shared/openapi.json` 的 PR 必须按顺序通过以下三项检查：

1. **生成代码与契约零漂移**：导出最新 OpenAPI 并执行 `pnpm api:generate` 后，`git diff --exit-code` 必须为空（确保已入库的 `packages/shared/src/api/generated/` 与契约完全同步）；
2. **全工作区类型检查（`vue-tsc --noEmit`）**：`packages/shared`、`packages/ui`、`apps/web-user`、`apps/web-admin` 零 TypeScript / Vue 模板类型错误；
3. **单元与契约测试 + 生产构建**：Vitest 测试全绿且双端 `vite build` 正常产出。

## 自动化测试聚焦范围（What to unit test）

结合 8 人并行交付节奏，前端 Vitest 单元测试不追求为每个展示型 `.vue` 页面堆砌脆弱的快照或全量标记测试，而是精准锁死**高风险公共底座与纯业务状态逻辑**：

- **`@landfall/shared` 传输拦截层（必测）**：
  - `ApiResponse<T>` 成功（`code === 0`）自动解包返回 `data`；
  - 业务错误码（如 `30004` 敏感词拦截、`60001` AI 限流）抛出类型化错误对象并保留 `code`、`message`、`traceId`；
  - HTTP `401` 分支：`20001`（未登录）与 `20006`（会话失效/被踢下线）触发清理本地失效令牌与对应登录引导，且请求头注入逻辑正确。
- **`features/<domain>/` 内的纯逻辑与状态辅助函数（按需必测）**：
  - RBAC 权限树的父子级联勾选、取消勾选与 `indeterminate` 半选状态推导纯函数；
  - Query Key 工厂的层级前缀包含关系、URL 筛选参数解析与归一化（如标签输入裁剪、日期区间格式校验）；
  - 媒体上传前的大小/格式预检函数与播放降级状态判断。
- **跨包端到端主链路验收**：双前端与真实后端容器的跨工作包交互冒烟与回归用例由包 8 统一维护。

## 组件测试写法约定（Component test conventions）

当为复杂交互组件（如通用表单控件、权限树选择器）编写组件测试时：

- **按用户可见语义查询元素**：一律优先使用 `getByRole`、`getByLabelText`、`getByText`（异步渲染使用 `findBy*` 或 `waitFor`），**禁止通过脆弱的 CSS 类名、DOM 层级下标或 `toHaveClass` 断言 Tailwind 工具类字符串**。
- **断言结果而非内部实现**：断言界面渲染出的状态、无障碍属性（`aria-checked`、`disabled`）或对外发出的业务事件载荷，不刺探组件内部私有 `ref` 变量名。
- **测试数据贴合真实契约**：构造模拟响应或视图入参时基于 `@landfall/shared` 的生成类型编写工厂函数（仅覆盖当前用例关心的字段），防止手写假数据结构与后端真实 `openapi.json` 脱节。
