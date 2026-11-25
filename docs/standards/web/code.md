# Web 前端代码规范（Web Code Standard）

编写或评审 `apps/web-user/`、`apps/web-admin/`、`packages/ui/` 与 `packages/shared/` 源码时阅读本文件。通用规则与分级见 [CODING_STANDARDS.md](../../../CODING_STANDARDS.md)；接口契约与 Orval 生成规则见 [docs/standards/api-contract.md](../api-contract.md)。

`packages/shared/src/api/generated/` 由 `pnpm api:generate` 自动生成并提交入库（`.gitattributes` 默认折叠），不在手写代码评审范围内（严禁手工修改或手解合并冲突）。

## 工作区职责与应用内分层（Layers and boundaries）

前端 Monorepo 分四个明确边界：

- **`@landfall/shared`（`packages/shared`）**：承载入库的 `openapi.json` 与 Orval 生成并提交入库的纯请求函数/TS 类型（`src/api/generated/`）、统一 Axios `mutator.ts`（注入 `Bearer` 令牌、校验 `code === 0` 自动解包内层 `data`、按业务码分发 `401` `20001`/`20006` 与错误提示）以及跨端共享业务常量。保持纯 TypeScript，不耦合 Vue 组件。
- **`@landfall/ui`（`packages/ui`）**：双端共用的无业务语义设计系统底座，存放 `shadcn-vue`（底层 `reka-ui`）基础原语（`Button`、`Dialog`、`Input`、`Table`、`Checkbox`、`Skeleton` 等）、`cn()` 工具函数与基础 CSS 变量。
- **`@landfall/web-user`（`apps/web-user`）** 与 **`@landfall/web-admin`（`apps/web-admin`）**：两个独立 Vue 3 应用，各自内部统一按以下四层单向依赖组织：

| 调用方 \ 被调用方 | `views/` | `layouts/` | `features/*/index.ts` | 其他 `features/*` 内部文件 | `@landfall/shared` & `@landfall/ui` |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `router/` | 路由懒加载挂载 | 挂载布局壳 | 可读权限/守卫辅助 | **禁止** | 允许 |
| `views/`（薄页面壳） | **禁止** | 允许 | **允许** | **禁止** | 允许（布局与通用原语） |
| `layouts/`（全局导航壳） | **禁止** | 同层允许 | **允许**（如登录弹窗/头像） | **禁止** | 允许 |
| `features/<domain>/` | **禁止** | **禁止** | 按需调公共特性（如 `auth`） | **严禁跨特性深层导入** | **允许** |

- **`views/` 只做极薄路由页面壳**：读取路由参数/URL 查询参数、编排页面外层栅格间距，并组合挂载各 `features/<domain>/index.ts` 导出的业务组件（例如详情页壳组装 `media`、`interaction`、`social`、`ai-assist` 四个工作包的公开组件）。页面壳内不写接口请求转换与子业务内部交互，从目录结构上杜绝多人混改同一 `.vue` 大文件。
- **`features/<domain>/` 是深模块**：唯一公开契约是 `features/<domain>/index.ts`；特性内部的私有子组件、表单校验、DTO 转视图模型函数一律不对外导出，其他特性与页面严禁绕过 `index.ts` 深层 `import` 内部路径。

## 文件与标识符命名（Naming）

- Vue 单文件组件统一使用 `<script setup lang="ts">` 与 PascalCase 文件名（如 `CommentDrawer.vue`、`WaterfallGrid.vue`）；Composable 以 `use` 开头采用 kebab-case 或 camelCase 统一命名（同目录保持一致），其余纯 TS 模块按职责概念命名（`queries.ts`、`models.ts`、`tree-selection.ts`），**禁止新建笼统的 `utils.ts`、`helpers.ts`、`common.ts`**（`@landfall/ui/src/lib/utils.ts` 的 `cn` 除外）。
- 布尔变量与 Props 使用表达语义的前缀：`is`（状态）、`has`（存在性）、`can`（权限能力）、`should`（策略）；组件库原生受控属性（`open`、`disabled`、`checked`、`indeterminate`）保留原名。
- 封闭字符串集合优先使用 TypeScript 字面量联合类型或 `as const` 对象推导类型（不用 TS `enum` 语法），状态分支使用穷尽判断或常量映射表。

## 服务端状态（`@tanstack/vue-query`）与客户端状态（Pinia）

严格分离**服务端数据缓存**与**纯客户端状态**：

- **一切业务 API 请求走 `@landfall/shared` + `@tanstack/vue-query`**：
  - 除 `VideoPlayer` 直接消费的二进制媒体流 URL（HLS `.m3u8`/`.ts` 与 Range `206`）外，双前端**严禁手写裸 `axios` / `fetch` 请求或手抄后端 DTO 接口定义**；
  - 每个 `features/<domain>/` 在自身 `queries.ts` 中维护清晰的层级 Query Key 工厂（如 `postKeys = { all: ['posts'] as const, lists: () => [...postKeys.all, 'list'] as const, detail: (id: string) => [...postKeys.all, 'detail', id] as const }`），通过 `useQuery` 读取数据、通过 `useMutation` 执行写操作并在 `onSuccess` 中按层级 Key 调用 `invalidateQueries` 联动刷新跨组件视图（如包 4 发评或打分后自动刷新包 3 详情页统计，无需跨包手写事件总线）；
  - 多步串联的用户写操作（如“若含新选本地文件先上传拿 `assetId`、再提交创建 `Post`”）封装在同一个 `mutationFn` 内，确保整个动作期间加载态与防重点击连续生效。
- **Pinia 仅存纯客户端状态**：
  - 强制使用 Composition Setup Store 语法（`defineStore('auth', () => { ... })`）；
  - Pinia 只允许存放登录令牌会话摘要、侧边栏折叠、主题偏好等纯客户端跨页状态，**严禁把作品列表、评论分页、后台用户表等服务端接口响应塞进 Pinia 充当手工缓存**。
- **筛选与分页状态归 URL**：搜索关键词、选中标签、排序档位、后台表格页码与筛选条件优先同步在 Vue Router 的 URL `query` 参数中，保证刷新页面与分享链接状态不丢失。
- **弹窗草稿随挂载自然重置**：弹窗表单草稿状态放在 `DialogContent` 内部子组件中，关闭卸载即清空，不在渲染期间靠监听 `open` 手动逐字段清空。

## 样式令牌、组件复用与无障碍（Styling and accessibility）

- **统一 Tailwind CSS v4 + `shadcn-vue`（`reka-ui`）**：双端不再引入第二套重型 UI 组件库。管理后台复杂数据表格统一基于 `@landfall/ui` 的 `Table` 搭配 `@tanstack/vue-table` 构建；RBAC 角色权限树统一基于 `reka-ui` 的 `TreeRoot` + `Checkbox`（含父子节点级联与 `indeterminate` 半选态）构建；统计图表使用 `ECharts`。
- **保护 `@landfall/ui` 纯净原语**：`packages/ui` 中的组件保持无业务耦合的原语形态；`apps/web-user` 与 `apps/web-admin` 在各自入口 CSS 中配置 `@source` 扫描 `packages/ui`，并通过 `@theme` 与 CSS 变量定义各自主题令牌（社区主色、圆角、阴影）。业务组件优先引用语义主题类，禁止散落硬编码色值或从外部强行覆盖原语内部 DOM 选择器。
- **无障碍可定位名称（Accessible names）**：纯图标按钮（点赞、收藏、关闭弹窗、删除图片）必须提供准确的中文 `aria-label`，内部图标加 `aria-hidden="true"`；列表中每行重复的操作按钮（如后台封禁、删除评论、移除第 N 张上传图）在 `aria-label` 中带上目标标识，既保障可访问性也便于测试精准定位。
