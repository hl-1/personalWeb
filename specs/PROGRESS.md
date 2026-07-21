---
phase: P3
feature: P3-ADMIN-001
status: awaiting-user-acceptance
verified: 2026-07-20
---

# StudyStack 实施进度

## 当前状态

P3 Admin 的 Task 1-18 已在 `codex/p3-admin` 本地实现，当前状态为 `P3 / awaiting-user-acceptance`。这不授权开始 P4，也不授权暂存、提交、推送或创建 PR。

## P3 行为证据

- A1-A4：admin 模块边界、ADMIN/CSRF、append-only 审计、version 乐观并发与 `stale_version` 已由后端结构、服务和 API 集成测试覆盖。
- A5-A10：文章、分类、标签和项目的 CRUD、发布/归档、引用冲突及服务端 Markdown 预览已实现。
- A11-A12：简介、技能和经历的创建/更新、排序、可见性及日期约束已实现。
- A13-A17：P3 OpenAPI、generated TypeScript、strict Zod、同源 Session client、后台路由、通用状态和响应式管理页面已同步。
- A18：新增 Playwright 覆盖匿名/普通用户/管理员路由、CSRF、文章创建/预览/XSS/发布/并发、taxonomy 引用冲突、项目状态流和 portfolio 写入。

## 本次验证结果

| 检查 | 结果 |
|---|---|
| `OpenApiDevelopmentIntegrationTest` | 状态 0；生成 P3 OpenAPI |
| `pnpm contract:check` RED | 按预期失败，generated 类型与 P3 OpenAPI 存在差异 |
| `pnpm contract:generate` + `contract:check` | 状态 0 |
| `pnpm lint` | 状态 0，无 warning |
| `pnpm typecheck` | 状态 0 |
| `pnpm build` | 状态 0，265 个模块完成生产构建 |
| 新增管理 E2E | 状态 0，8 个 Playwright 测试通过 |
| Compose 配置测试 | 状态 0，7 个测试通过；生产 Compose 不含测试身份旁路 |
| `git diff --check` | 状态 0 |

全量 Vitest 聚合首次运行时，已启动的 25 个文件和 170 个断言均通过，但并行资源争用导致 11 个 worker 启动超时，命令最终状态 1；低并发复跑被用户消息中断，未宣称全绿。

后端完整 `mvnw verify` 已按用户明确要求停止，不再运行。本阶段此前的定向后端测试和本次 OpenAPI 集成测试通过，但不能替代完整后端聚合。

## 安全与边界结论

- 管理路由要求 `ADMIN`；所有管理写请求通过同源 CSRF token，测试 token 不进入请求体。
- 生产 Compose 不注入本地测试身份、认证 bypass 或 GitHub secret 到 web 构建。
- Markdown 预览复用服务端白名单；恶意 Markdown 不产生可执行 script、图片或事件属性。
- 并发 409 保留用户输入，不自动重试覆盖服务端版本。
- Java 主代码未命中旧 Swagger 注解；`v-html` 仅存在于 `SafeMarkdownView`。
- admin 主代码未直接依赖 content/portfolio 的 domain 或 infrastructure 包。

## 未运行项

- 未完成后端完整 `verify`，原因是用户明确要求不需要。
- 未运行完整 Compose `up`、全量 P0-P3 Playwright 和数据库生命周期清理；本次新增管理 E2E 使用本地 Vite 与 Playwright route mock，不调用真实 GitHub。
- 未使用真实 GitHub OAuth App，未触发 GitHub CI，未执行公网部署。

## 仓库状态与下一步

当前分支为 `codex/p3-admin`。P3 Task 1-18 改动均保留在工作区，未暂存、未提交、未推送。`server/.idea/` 为既有未跟踪目录，未修改或清理。

下一步仅由用户决定：验收 P3、补跑被跳过的聚合项，或明确授权 Git 操作。未经明确授权不得开始 P4。
