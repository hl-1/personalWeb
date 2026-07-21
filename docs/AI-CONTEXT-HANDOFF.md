---
project: StudyStack
purpose: AI 会话上下文衔接
updated: 2026-07-20
current_phase: P3
current_status: awaiting-user-acceptance
---

# StudyStack AI 上下文衔接

## 当前状态

当前分支 `codex/p3-admin`。P3 Task 1-18 已在本地实现，未暂存、未提交、未推送；不得自动开始 P4。

新会话依次读取：

1. `C:\Users\6666\.codex\AGENTS.md`
2. `specs/PROGRESS.md`
3. `specs/features/P3-ADMIN-001/IMPLEMENTATION_PLAN.md`
4. P3 最新 session handoff（如后续创建）

## 已实现基线

- 后端：admin 应用/领域/web 边界、审计、ADMIN/CSRF、version 并发、文章/taxonomy/项目/profile/skill/experience 管理与 Markdown 预览。
- 前端：strict Zod、同源 Session/admin client、TanStack Query、后台布局和所有 P3 管理页面。
- 契约：运行中 P3 OpenAPI 已生成，`web/src/shared/api/generated/openapi.d.ts` 已同步。
- 浏览器：新增 8 个管理 E2E 已通过，覆盖权限、CSRF、内容、作品集、XSS 与冲突路径。

## 最近验证

- 通过：OpenAPI 定向集成测试、contract check、lint、typecheck、build、Compose 配置测试 7/7、新增管理 E2E 8/8、`git diff --check`。
- 全量 Vitest：25 个文件中已启动的 170 个断言通过，但并行 worker 资源超时导致最终状态 1；低并发复跑被中断。
- 后端完整 `verify`：按用户要求停止，不再运行。
- 完整 Compose 拓扑和全量 P0-P3 Playwright：本次未运行。

真实细节与未运行项以 `specs/PROGRESS.md` 为准，不得把未完成聚合描述为全绿。

## 固定边界

- PostgreSQL 是唯一关系数据库，Flyway 是唯一 schema 所有者，Hibernate 使用 `validate`。
- 管理写接口只允许 ADMIN，并要求同源 CSRF；测试身份不得进入生产 Compose。
- API 数据进入前端 union 前必须 runtime 解析；禁止直接强转信任响应。
- 原始 Markdown 不进入公开响应，`v-html` 只允许在 `SafeMarkdownView`。
- 不得回滚或清理现有工作区；`server/.idea/` 是既有未跟踪目录。
- 未经用户明确授权，不得暂存、提交、推送、创建 PR 或开始 P4。

## 高效恢复

只按用户指定范围运行定向测试。不要默认重跑后端完整 `verify`、完整 Compose 或全量 E2E；需要补聚合时先取得用户明确要求。
