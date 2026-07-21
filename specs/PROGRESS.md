---
phase: P3
feature: P3-ADMIN-001
status: awaiting-user-acceptance
verified: 2026-07-21
---

# StudyStack 实施进度

## 当前状态

P3 Admin 的 Task 1-18 已通过 PR #6 合并到 `main`，产品与技术规格已通过 PR #7 补齐。当前状态为 `P3 / awaiting-user-acceptance`，这不授权开始 P4。

## P3 行为证据

- A1-A4：admin 模块边界、ADMIN/CSRF、append-only 审计、version 乐观并发与 `stale_version` 已由后端结构、服务和 API 集成测试覆盖。
- A5-A10：文章、分类、标签和项目的 CRUD、发布/归档、引用冲突及服务端 Markdown 预览已实现。
- A11-A12：简介、技能和经历的创建/更新、排序、可见性及日期约束已实现。
- A13-A17：P3 OpenAPI、generated TypeScript、strict Zod、同源 Session client、后台路由、通用状态和响应式管理页面已同步。
- A18：新增 Playwright 覆盖匿名/普通用户/管理员路由、CSRF、文章创建/预览/XSS/发布/并发、taxonomy 引用冲突、项目状态流和 portfolio 写入。

## 最终验证结果

| 检查 | 结果 |
|---|---|
| PR #6 backend | push 与 pull request 工作流均通过完整 `mvnw verify`，并生成 OpenAPI |
| PR #6 frontend | push 与 pull request 工作流均通过 frozen install、lint、typecheck、Vitest 和 production build |
| PR #6 contract | push 与 pull request 工作流均通过 generated TypeScript 与 OpenAPI 一致性检查 |
| PR #6 compose | push 与 pull request 工作流均通过 Compose 配置和生产策略测试 |
| PR #6 e2e | push 与 pull request 工作流均通过完整 Compose 拓扑、Playwright 和 volume 清理 |
| PR #7 文档跟进 | backend、frontend、contract、compose 和 e2e 的 push 与 pull request 工作流均通过 |
| `git diff --check` | 状态 0 |

本地没有重复运行完整 backend verify、Compose 生命周期和全量 Playwright；这些聚合项已由 PR #6 的 GitHub CI 执行并通过。PR #7 的文档跟进再次通过同一组聚合工作流。

## 安全与边界结论

- 管理路由要求 `ADMIN`；所有管理写请求通过同源 CSRF token，测试 token 不进入请求体。
- 生产 Compose 不注入本地测试身份、认证 bypass 或 GitHub secret 到 web 构建。
- Markdown 预览复用服务端白名单；恶意 Markdown 不产生可执行 script、图片或事件属性。
- 并发 409 保留用户输入，不自动重试覆盖服务端版本。
- Java 主代码未命中旧 Swagger 注解；`v-html` 仅存在于 `SafeMarkdownView`。
- admin 主代码未直接依赖 content/portfolio 的 domain 或 infrastructure 包。

## 环境外验证项

- 未使用真实生产 GitHub OAuth App 执行外部 provider 联调。
- 未执行公网域名、TLS、备份、监控或正式生产部署；这些属于后续交付阶段。

## 仓库状态与下一步

P3 实现和 CI 回归修复已通过 PR #6 合并；产品与技术规格已通过 PR #7 合并。回归修复包含 P3 模块契约/旧测试断言同步、无数据库测试的 admin mock，以及资料表单重复保存时仅映射允许字段。

下一步仅由用户决定是否验收 P3；生产 OAuth 与公网部署验证留待对应交付阶段。未经明确授权不得开始 P4。
