---
project: StudyStack
feature: P3-ADMIN-001
purpose: P3 Task 1-18 完成后的验收交接
updated: 2026-07-21
status: implemented-awaiting-user-acceptance
branch: codex/p3-admin
implementation_head: 4a24811
merged_pr: 6
---

# P3 Task 18 后会话交接

本文件记录 P3 管理后台已经实现和验证的事实。PR #6 已合并到 `main`；P3 仍等待用户验收，未经明确授权不得开始 P4。

## 1. 已交付范围

- A1-A4：admin named interface 边界、ADMIN/Session/CSRF、append-only 同事务审计、version 乐观并发和统一 ProblemDetail。
- A5-A8：文章 CRUD、发布/未来发布/归档、安全 Markdown 预览，以及分类和标签 CRUD、唯一性与引用删除保护。
- A9-A12：项目 CRUD/状态/预览，以及个人简介、技能和经历的写入、排序、可见性、日期和并发规则。
- A13-A17：P3 OpenAPI/generated TypeScript/strict Zod、同源 Session client、后台路由与外壳、文章/taxonomy/portfolio 管理页面和通用状态。
- A18：认证路由、文章与项目流程、taxonomy 冲突、Markdown XSS、并发冲突、portfolio 写入、Compose 和公开读取回归。

管理写入与 P2 公开读取共用同一数据库。只有符合公开状态、发布时间和 visible 规则的数据会出现在公开页面；草稿、归档和未来发布内容继续隐藏。

## 2. 最终 CI 证据

PR #6 的最新 push 与 pull request 工作流均通过：

| Job | 验证内容 | 结果 |
|---|---|---|
| backend | Java 21，`mvnw verify`，并上传 OpenAPI | PASS |
| frontend | lint、typecheck、Vitest、production build | PASS |
| contract | generated TypeScript 与 OpenAPI 一致 | PASS |
| compose | Compose config 与生产策略测试 | PASS |
| e2e | 完整 Compose 拓扑、Playwright、volume 清理 | PASS |

本地没有重复运行完整 backend `verify`，原因是用户明确要求减少无关验证；GitHub CI 已执行并通过完整 backend `verify`。

## 3. 合并前回归修复

- 将 P2 scope 和 Modulith 测试更新为兼容 P3，同时保留 admin 只能依赖批准 named interface 的边界。
- 为不连接数据库的 Web/OpenAPI 测试集中提供 admin service mock。
- 同步 P3 OpenAPI 版本、CSRF ProblemDetail、V6 taxonomy name 唯一约束和测试夹具。
- 修复管理员资料重复保存：API 响应只按允许字段映射到 strict 表单，避免 `id`、`createdAt`、`updatedAt` 等未知字段阻止第二次提交。
- 认证路由 E2E 使用最终的 `admin-dashboard-view`，并从后台外壳退出后验证全局登出流程。

## 4. 后续入口

1. 用户按 A1-A18 验收管理后台及公开页面联动。
2. 验收发现问题时，仅在 P3 范围内补回归测试和修复，并同步本规格目录。
3. 只有用户明确验收 P3 并授权后，才能更新阶段状态并开始 P4。
