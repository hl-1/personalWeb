---
task_id: P1-IDENTITY-001
title: StudyStack 身份与权限产品规格
phase: P1
status: awaiting-user-acceptance
created: 2026-07-16
updated: 2026-07-17
design_refs:
  - C:/softWare/project/workDoc/java-personal-website-design.md
  - docs/AI-PROJECT-DELIVERY-PLAN.md
  - specs/features/P0-FOUNDATION-001/PRODUCT.md
---

# P1 Identity Product Specification

## 1. Summary

P1 为 StudyStack 建立身份与权限基线：访客可通过 GitHub OAuth 登录，服务端将外部身份绑定到本地账号，认证状态由 PostgreSQL Session 持久化；前端能够读取当前用户、获取 CSRF token、安全退出，并基于服务端角色保护管理员页面。

本阶段只定义身份、Session、认证 API 与最小登录体验。作品、文章、评论、媒体和管理 CRUD 属于后续阶段。

## 2. Actors And Goals

| 角色 | 目标 |
|---|---|
| 访客 | 通过 GitHub 登录，获得不泄露 provider 数据的失败反馈 |
| 已登录用户 | 在刷新或应用重启后保持有效会话，查看最小身份信息并安全退出 |
| 管理员 | 由服务端白名单获得管理员权限，访问受保护的管理入口 |
| 被禁用用户 | 无法重新登录；已有 Session 在下一次请求时失效 |
| 运维人员 | 通过环境变量配置 OAuth、数据库与管理员白名单，不把秘密写入仓库 |

## 3. Stable Behavior Contracts

### I1 OAuth Flow

- 登录入口固定为 `GET /oauth2/authorization/github`，回调固定为 `/login/oauth2/code/github`。
- OAuth `state`、authorization code 交换和 token 校验由 Spring Security 负责。
- 成功后固定重定向至 `/login?status=success`，不采纳 saved request 或外部跳转目标。
- 失败只允许输出 `oauth_denied`、`invalid_profile`、`identity_conflict`、`account_disabled`、`login_failed`。

### I2 First Login

- 首次成功登录创建一个本地 UUID 用户和一条 GitHub 外部身份绑定。
- 本地账号保存 login、displayName、可空 avatarUrl、状态和审计时间；GitHub ID 不作为本地主键。
- API 和浏览器状态不得暴露 GitHub subject。

### I3 Repeat And Concurrent Login

- 同一 `(github, providerSubject)` 重复登录复用本地账号，并更新展示资料和最后登录时间。
- 一个本地用户最多绑定一个 GitHub 身份，一个 GitHub subject 最多绑定一个本地用户。
- 并发首次登录由数据库唯一约束收口；可识别竞争恢复为既有绑定，其他完整性冲突失败且不产生孤立数据。

### I4 GitHub Claims

- `id` 只接受正数范围内的 `Long`、`Integer` 或纯 ASCII 数字字符串，并归一化为无前导零的十进制字符串。
- `login` trim 后必须为 1 至 255 个字符。
- `name` 缺失或空白时回退 login；类型错误或超过 255 个字符时拒绝。
- `avatar_url` 仅接受长度不超过 2048、包含 host 的绝对 HTTPS URI；无效值映射为 `null`。
- claims 异常只公开字段和固定原因，不保存原始 Map、非法值或解析异常。

### I5 Session Persistence

- 认证 Session 使用 PostgreSQL Spring Session JDBC，超时固定为 24 小时。
- Session ID 在登录成功后轮换；同一数据库和有效 Cookie 可在应用重启后恢复认证。
- 过期 Session 不得恢复认证，退出后对应 Session 数据必须删除。
- 清理任务每 10 分钟运行，应用不得自动创建 Session 表。

### I6 Cookie Profiles

- Cookie 固定为 `STUDYSTACK_SESSION`、`HttpOnly=true`、`SameSite=Lax`、`Path=/`。
- dev/test 使用 `Secure=false`，prod 使用 `Secure=true`。

### I7 Current User

- `GET /api/v1/auth/me` 对匿名和已登录请求均返回 `200`。
- 匿名响应为 `{ "authenticated": false, "user": null }`。
- 登录响应为 `{ "authenticated": true, "user": AuthUser }`。
- `AuthUser` 只包含本地 UUID、login、displayName、可空 avatarUrl 和按 `USER`、`ADMIN` 顺序输出的 roles。

### I8 CSRF

- `GET /api/v1/auth/csrf` 返回非空 token 和固定 header 名 `X-CSRF-TOKEN`。
- 状态变更请求必须携带有效 token；缺失或无效时返回 `403`。
- 前端只在认证 client 实例内存中缓存 token，不持久化到 Pinia、Storage 或 URL。

### I9 Logout

- `POST /api/v1/auth/logout` 成功返回 `204`，匿名退出保持幂等。
- 退出清除 SecurityContext、服务端 Session、Cookie 和前端认证查询缓存。
- logout 遇到 `403` 时清除前端缓存的 CSRF token，且不自动重试。

### I10 Administrator Authorization

- 有效账号始终拥有 `USER`；仅 GitHub subject 命中服务端白名单时拥有 `ADMIN`。
- prod 白名单必须显式配置为逗号分隔的正 ASCII 整数；空值或非法值导致启动失败。
- `/api/v1/admin/**` 由服务端要求 `ADMIN`：匿名返回 `401`，普通用户返回 `403`。
- 前端路由守卫只是体验层，不替代服务端授权。

### I11 Disabled Accounts

- `DISABLED` 账号登录时返回 `account_disabled`。
- 已登录账号被禁用或删除后，下一次请求返回 `401` 并清理认证 Session。

### I12 Vue Authentication Experience

- 登录页提供 GitHub 登录入口、固定成功/失败反馈和退出操作。
- 认证状态只由 TanStack Query 管理，Pinia 不保存用户或角色副本。
- 后端响应必须先通过严格 Zod schema；未知字段、未知或重复角色、非法 UUID 和非法头像 URL 均拒绝。
- 管理路由对匿名用户跳转登录页并记住安全的站内 return target；普通用户跳转禁止页。
- return target 只允许同源绝对路径，拒绝协议相对路径、反斜杠、控制字符及敏感查询参数。

### I13 Contract And Secrets

- Controller、OpenAPI、生成类型与前端运行时 schema 必须保持一致。
- prod 只从环境变量读取 GitHub client、数据库凭据和管理员白名单；仓库只允许 `EXAMPLE_ONLY_` 示例值。
- URL、日志、API、Session payload 和前端状态不得包含 code、token、client secret、完整 claims、GitHub subject 或 Session ID。

## 4. Failure Contract

| 场景 | 对外行为 |
|---|---|
| 用户拒绝 GitHub 授权 | `/login?error=oauth_denied` |
| GitHub claims 非法 | `/login?error=invalid_profile` |
| 身份唯一性冲突 | `/login?error=identity_conflict` |
| 账号被禁用 | `/login?error=account_disabled` |
| token、user-info 或未知登录故障 | `/login?error=login_failed` |
| 匿名访问管理员 API | `401`，不重定向 provider |
| 普通用户访问管理员 API | `403` |
| logout 缺少或携带无效 CSRF | `403`，Session 保持有效 |
| 认证响应不符合前端 schema | `invalid_response`，不保存响应正文 |

Provider 消息、异常 message、响应正文和敏感 cause 均不得成为浏览器可见错误内容。

## 5. Acceptance Criteria

- I1-I13 均有自动化测试证据，映射见 `TECH.md`。
- Flyway V2 创建身份和 Spring Session 表，Hibernate `validate` 通过。
- 首次、重复、并发、禁用及冲突登录路径通过集成测试。
- Session 持久化、轮换、重启恢复、过期和退出删除通过集成测试。
- 匿名、用户和管理员的 API/路由授权矩阵通过。
- 前端 schema、client、Query、页面、路由和 E2E 回归通过。
- OpenAPI 契约、生成类型、配置策略和敏感数据扫描通过。

## 6. Non-Goals

- 不实现账号密码、邮箱登录、多 OAuth provider、账号合并或自助解绑。
- 不提供后台用户管理、角色编辑、禁用操作 API 或审计界面。
- 不实现作品、文章、标签、技能、简历等内容域功能。
- 不定义真实 GitHub OAuth App 的生产部署流程。

## 7. Scope Gate

P1 实现已合并，当前状态为 `awaiting-user-acceptance`。P2 必须使用独立阶段规格和分支，不得通过修改 P1 契约提前引入内容域行为。
