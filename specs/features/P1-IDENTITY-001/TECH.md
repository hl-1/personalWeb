---
task_id: P1-IDENTITY-001
title: StudyStack 身份与权限技术规格
phase: P1
status: awaiting-user-acceptance
created: 2026-07-16
updated: 2026-07-17
product_ref: specs/features/P1-IDENTITY-001/PRODUCT.md
implementation_ref: specs/features/P1-IDENTITY-001/IMPLEMENTATION_PLAN.md
design_refs:
  - C:/softWare/project/workDoc/java-personal-website-design.md
  - docs/AI-PROJECT-DELIVERY-PLAN.md
  - specs/features/P0-FOUNDATION-001/TECH.md
---

# P1 Identity Technical Specification

## 1. Technical Goals And Constraints

- 在 P0 Spring Boot 3.5.16、Vue 3 和 PostgreSQL 基线上增加 GitHub OAuth2 Client、Spring Security、Spring Session JDBC 与身份持久化。
- `identity` 模块独占身份模型、绑定规则、认证 principal、授权规则和认证 API。
- OAuth 协议、安全响应头、CSRF 与 Session fixation 使用 Spring Security 标准能力。
- schema 只由 Flyway 管理；JPA 使用 `ddl-auto=validate`，Spring Session 禁止自动建表。
- 浏览器只通过同源 `/api` 访问后端，不保存 OAuth token 或 GitHub subject。

## 2. Ownership And Boundaries

| 层 | 责任 | 禁止事项 |
|---|---|---|
| `identity.application` | claims 归一化、管理员 ID 策略、身份绑定 | 不依赖 Web DTO，不记录原始 claims |
| `identity.domain` | 用户、外部身份、状态和 Repository 端口 | 不承担 OAuth 协议和 Cookie 逻辑 |
| `identity.infrastructure.security` | OAuth user service、principal、handlers、filter chain、活动账号过滤 | 不复制 provider attributes，不保存 token |
| `identity.web` | `/api/v1/auth` DTO 和 Controller | 不返回 subject、email、token 或 Session ID |
| `db/migration` | 身份与 Session schema | 不使用运行时自动建表或数据库 UUID 扩展 |
| `web/src/features/auth` | Zod schema、同源 client、TanStack Query、return target | 不复制认证状态到 Pinia/Storage |
| `web/src/router` | 登录回调与管理员页面体验守卫 | 不替代服务端授权 |

其他业务模块不得直接读取身份表或解析 GitHub claims。

## 3. Data Model And Migration

Flyway `V2__identity_and_sessions.sql` 创建四张表：

### 3.1 `identity_user_account`

- `id UUID` 由应用生成，无数据库默认值。
- `login VARCHAR(255)`、`display_name VARCHAR(255)` 非空且 trim 后非空。
- `avatar_url VARCHAR(2048)` 可空。
- `status VARCHAR(16)` 仅允许 `ACTIVE`、`DISABLED`。
- `created_at`、`updated_at`、`last_login_at` 为非空 `TIMESTAMPTZ`。
- `version BIGINT NOT NULL DEFAULT 0` 且不得为负，用于乐观锁。

### 3.2 `identity_external_identity`

- `id UUID`、`user_id UUID` 非空，用户外键使用 `ON DELETE CASCADE`。
- `provider` 固定为 `github`；`provider_subject VARCHAR(64)` trim 后非空。
- 唯一约束为 `(provider, provider_subject)` 和 `(user_id, provider)`。

### 3.3 Spring Session

- `spring_session`、`spring_session_attributes` 使用 Spring Session JDBC 3.5.x PostgreSQL 结构。
- Session ID 唯一，并为 expiry、principal 建立索引。
- attributes 外键指向 Session 主键并 `ON DELETE CASCADE`。

所有约束和索引使用稳定显式名称。迁移不得依赖 `uuid_generate_v4()`、`gen_random_uuid()` 或额外扩展。

## 4. Authentication Pipeline

```text
Browser
  -> /oauth2/authorization/github
  -> GitHub authorization/token/user-info
  -> GitHubOAuth2UserService
  -> GitHubClaimsNormalizer
  -> IdentityBindingService
  -> StudyStackPrincipal
  -> Session fixation + PostgreSQL Session
  -> /login?status=success
```

- `GitHubOAuth2UserService` 只接受 registration `github`，默认 user-info client 返回后立即归一化和绑定。
- `StudyStackPrincipal` 只保存本地 UUID、GitHub subject、login、displayName、avatarUrl；attributes 为空，`toString()` 固定脱敏。
- principal 初始 authority 为 `ROLE_USER`；`ActiveAccountFilter` 每次请求查询账号状态，并按 `AdminGithubIdPolicy` 动态构造 `USER`/`ADMIN`。
- 成功 handler 删除 authorized client并固定跳转。失败 handler沿 cause chain 只读取固定 OAuth error code，不读取 message 或 provider 正文。

## 5. Identity Binding Rules

- provider subject 是绑定键，email 不参与绑定。
- 首次登录在单个事务中创建用户和外部身份。
- 重复登录复用用户，更新展示资料、`last_login_at` 和审计字段。
- 并发创建命中 `uk_identity_external_provider_subject` 时恢复读取既有绑定；其他完整性异常映射为 `identity_conflict`。
- `DISABLED` 用户在绑定阶段拒绝登录；活动账号过滤器处理已有 Session 的禁用和删除。

## 6. Security Configuration

- 单个 `SecurityFilterChain` 启用 OAuth2 Login，保留 CSRF 与安全响应头，不启用 form login 或 HTTP Basic。
- `/api/v1/admin/**` 要求 `ROLE_ADMIN`，其他 P1 路径为 `permitAll`；匿名访问受保护 API 使用 `401` entry point。
- 管理员配置前缀为 `studystack.identity.security`，生产值来自 `STUDYSTACK_ADMIN_GITHUB_IDS`。
- 管理员 ID 只在 `AdminGithubIdPolicy` 解析：逗号分隔、trim、正 ASCII 64 位整数、去前导零、去重；prod 禁止空配置。

## 7. Session And Cookie Configuration

| 配置 | 固定值 |
|---|---|
| `spring.session.store-type` | `jdbc` |
| `spring.session.timeout` | `24h` |
| `spring.session.jdbc.initialize-schema` | `never` |
| `spring.session.jdbc.cleanup-cron` | `0 */10 * * * *` |
| Cookie name | `STUDYSTACK_SESSION` |
| HttpOnly / SameSite / Path | `true` / `Lax` / `/` |
| Secure | dev/test `false`，prod `true` |

OAuth 测试可在独立上下文关闭 cleanup cron，不能改变生产策略。

## 8. Authentication API

| Method | Path | Success | Security |
|---|---|---|---|
| GET | `/api/v1/auth/me` | `200 AuthStateResponse` | 匿名可访问 |
| GET | `/api/v1/auth/csrf` | `200 CsrfTokenResponse` | 匿名可访问 |
| POST | `/api/v1/auth/logout` | `204` | 有效 CSRF token，匿名幂等 |

匿名与登录响应分别为：

```json
{ "authenticated": false, "user": null }
```

```json
{
  "authenticated": true,
  "user": {
    "id": "00000000-0000-0000-0000-000000000000",
    "login": "octocat",
    "displayName": "The Octocat",
    "avatarUrl": "https://avatars.example.test/u/1",
    "roles": ["USER", "ADMIN"]
  }
}
```

`CsrfTokenResponse` 只包含 token 和固定 `headerName=X-CSRF-TOKEN`。认证 API 不定义通用错误正文，避免扩展敏感外部契约。

## 9. Frontend Runtime Boundary

- `auth-schema.ts` 使用 strict Zod object 和 discriminated union，角色仅允许 `USER`、`ADMIN` 且不得重复。
- `auth-client.ts` 使用可注入 fetch、相对同源路径和 `credentials: 'same-origin'`；错误固定为 `network`、`unauthorized`、`forbidden`、`invalid_response`。
- client 不保存响应正文或未知字段；CSRF token 只在闭包内存中缓存，logout 不自动重试。
- `auth-query.ts` 使用 key `['auth', 'me']`，成功退出后删除精确认证缓存。
- 登录回调必须刷新服务端认证状态，再消费一次性 return target。
- return target 校验集中在 `return-to.ts`，拒绝跨源、路径歧义、控制字符和敏感查询键。

## 10. Environment And Delivery

- dev/test 只使用 `EXAMPLE_ONLY_` OAuth 值，管理员列表为空，Cookie 非 Secure。
- prod 必须提供 `GITHUB_CLIENT_ID`、`GITHUB_CLIENT_SECRET`、`STUDYSTACK_ADMIN_GITHUB_IDS` 和数据库变量，无秘密默认值。
- Caddy 同源转发 `/api/*`、OAuth 入口和 callback；SPA fallback 不得吞掉后端认证路径。
- OpenAPI 在 dev/test 启用、prod 禁用；契约生成与前端类型检查进入聚合验证。

## 11. Security And Privacy Invariants

- 不记录或返回 code、access token、client secret、完整 claims、GitHub subject、Session ID 或数据库密码。
- authorized client 在成功认证后删除，Session 只保存最小可序列化 principal。
- OAuth 错误 URL 只包含批准的固定码；未知错误统一 `login_failed`。
- Zod 校验前的响应不可进入 Query 状态，不得用 `as` 强转未知认证响应。
- 管理员权限每次请求动态计算，不把 ADMIN 快照持久化进 principal 或数据库。

## 12. Behavior-To-Test Traceability

| 行为 | 主要自动化证据 |
|---|---|
| I1 OAuth 流程 | `OAuthLoginIntegrationTest`、`auth-view.spec.ts`、`auth-routing.spec.ts` |
| I2 首次登录 | `IdentitySchemaIntegrationTest`、`IdentityBindingServiceIntegrationTest` |
| I3 重复与并发登录 | `IdentityBindingServiceIntegrationTest`、数据库唯一约束测试 |
| I4 claims 规则 | `GitHubClaimsNormalizerTest`、`OAuthLoginIntegrationTest` |
| I5 Session 持久化 | `SessionPolicyIntegrationTest` |
| I6 Cookie profile | `IdentitySecurityBaselineTest`、`SessionPolicyIntegrationTest` |
| I7 当前用户 | `AuthControllerIntegrationTest`、`auth-schema.spec.ts` |
| I8 CSRF | `AuthControllerIntegrationTest`、`auth-client.spec.ts` |
| I9 退出 | `SessionPolicyIntegrationTest`、`AuthControllerIntegrationTest`、`auth-view.spec.ts` |
| I10 管理员权限 | `AdminGithubIdPolicyTest`、`AuthorizationIntegrationTest`、`router.spec.ts` |
| I11 禁用账号 | `IdentityBindingServiceIntegrationTest`、`AuthorizationIntegrationTest` |
| I12 Vue 认证体验 | auth Vitest、路由测试、`auth-routing.spec.ts` |
| I13 契约与秘密 | `AuthOpenApiIntegrationTest`、contract/config policy、`SensitiveAuthenticationDataPolicyTest` |

聚合验证入口：

```powershell
Set-Location server
.\mvnw.cmd verify

Set-Location ..\web
pnpm lint
pnpm typecheck
pnpm test
pnpm contract:check
pnpm test:e2e
```

详细测试数量和 Compose smoke 证据以 `specs/PROGRESS.md` 为准，避免维护易过时的计数副本。

## 13. Risks And Compatibility

- GitHub claims 变化必须先更新 normalizer 测试和产品契约，不能在 user service 增加第二套解析。
- 修改认证 DTO 必须同步 Controller、OpenAPI、生成类型、Zod schema、client 和测试。
- 修改管理员 ID 规则必须检查 prod 启动失败、动态角色和 legacy 格式兼容。
- 修改 Session schema 或 principal 字段会影响已持久化 Session，必须先定义失效或兼容策略。
- 真实 GitHub OAuth App 联调和生产秘密不属于仓库自动化验收，需在部署环境验证。

## 14. Completion Gate

P1 实现与自动化证据已经完成并合并，规格状态为 `awaiting-user-acceptance`。内容域功能必须进入 P2 独立规格。
