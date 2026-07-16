---
phase: P1
feature: P1-IDENTITY-001
status: awaiting-user-acceptance
verified: 2026-07-16
---

# StudyStack 实施进度

## 当前状态

P1 Identity 的 Task 1-13 已在本地完成实施与聚合验证，当前状态为 `awaiting-user-acceptance`。该状态只表示等待用户验收 P1，不代表已批准 P2，也不授权创建 P2 代码、分支或迁移。

验证环境为 Windows、PowerShell 7、Java 21.0.10、Node 24、pnpm 11.9.0 和 Docker Desktop。本机 8080 端口由其他 Java 进程占用，Compose 与 Playwright 聚合验收使用 `CADDY_HTTP_PORT=18080`；容器内部拓扑和 Caddy 路由规则未改变。

## I1-I13 验证证据

| 行为 | 主要实现与证据 | 2026-07-16 结果 |
|---|---|---|
| I1 OAuth 流程 | `OAuthLoginIntegrationTest`、`auth-view.spec.ts`、`auth-routing.spec.ts` | GitHub 入口、state/callback、固定成功/失败跳转和 provider 拒绝路径通过 |
| I2 首次登录 | `IdentitySchemaIntegrationTest`、`IdentityBindingServiceIntegrationTest` | 首次登录创建本地用户与 GitHub 外部身份通过 |
| I3 重复与并发登录 | `IdentityBindingServiceIntegrationTest`、数据库唯一约束 | 重复登录复用账号，并发冲突恢复与回滚路径通过 |
| I4 claims 规则 | `GitHubClaimsNormalizerTest`、`OAuthLoginIntegrationTest` | ID、login、name、avatar 边界和非法 claims 固定错误码通过 |
| I5 Session 持久化 | `SessionPolicyIntegrationTest`、Flyway V2 | PostgreSQL Session、重启恢复、24 小时过期、退出删除通过 |
| I6 Cookie profile | `IdentitySecurityBaselineTest`、`SessionPolicyIntegrationTest` | `STUDYSTACK_SESSION`、HttpOnly、SameSite=Lax、Path=/；prod Secure 通过 |
| I7 当前用户 | `AuthControllerIntegrationTest`、`auth-schema.spec.ts` | 匿名/登录判别响应、最小用户字段和角色顺序通过 |
| I8 CSRF | `AuthControllerIntegrationTest`、`auth-client.spec.ts`、认证 E2E | token 获取、内存缓存、header 校验和 403 清理通过 |
| I9 退出 | `SessionPolicyIntegrationTest`、`AuthControllerIntegrationTest`、`auth-view.spec.ts` | CSRF 保护、204 幂等、Session/Cookie 清理和前端状态更新通过 |
| I10 管理员权限 | `AdminGithubIdPolicyTest`、`AuthorizationIntegrationTest`、`router.spec.ts` | 服务端白名单动态角色、`/api/v1/admin/**` 和前端守卫矩阵通过 |
| I11 禁用账号 | `IdentityBindingServiceIntegrationTest`、`AuthorizationIntegrationTest` | 禁用账号拒绝登录并使已有 Session 失效通过 |
| I12 Vue 认证体验 | auth Zod/client/query Vitest、路由与页面测试、`auth-routing.spec.ts` | 未校验响应不得进入应用状态，登录、退出、returnTo 和错误文案通过 |
| I13 契约与秘密 | OpenAPI 集成测试、`contract:check`、配置策略、`SensitiveAuthenticationDataPolicyTest` | Controller、OpenAPI、生成类型一致；示例配置和产物无真实秘密通过 |

## 聚合命令结果

| 命令 | 结果 |
|---|---|
| `server/.\mvnw.cmd verify` | 状态 0，117 个后端测试通过，JAR 构建成功 |
| `pnpm install --frozen-lockfile` | 状态 0，lockfile 无改写，pnpm 11.9.0 |
| `pnpm lint` | 状态 0，无 warning |
| `pnpm typecheck` | 状态 0 |
| `pnpm test` | 状态 0，11 个测试文件、85 个 Vitest 测试通过 |
| `pnpm contract:check` | 状态 0，生成 TypeScript 与 P1 OpenAPI 一致 |
| `pnpm build` | 状态 0，180 个模块完成生产构建 |
| `docker compose ... config --quiet` | 状态 0 |
| `node --test deploy/tests/compose-config.test.mjs` | 状态 0，5 个 Compose/Caddy 策略测试通过 |
| `docker compose ... up -d --build --wait` | 状态 0，PostgreSQL、app、Caddy 全部 healthy |
| `pnpm test:e2e` | 状态 0，8 个 Playwright 测试通过 |
| `docker compose ... down --volumes` | 状态 0，容器、网络和 `studystack_postgres-data` 已删除 |

## 数据与安全结论

- Flyway 当前包含 V1 基线和 `V2__identity_and_sessions.sql`。V2 创建用户、GitHub 外部身份、Spring Session 与 Session attributes 表，显式约束、索引和级联行为均由 PostgreSQL 17.7 Testcontainers 验证。
- Hibernate 保持 `validate`，Spring Session JDBC 自动建表保持 `never`；schema 继续只归 Flyway 所有。
- SecurityContext 仅保存最小可序列化 principal；authorized client 在成功认证后删除，Session、日志、URL 和前端产物不保存 OAuth code、access token、client secret 或完整 claims。
- `GET /api/v1/auth/me`、`GET /api/v1/auth/csrf` 和 `POST /api/v1/auth/logout` 已进入 P1 OpenAPI；生成 TypeScript、Zod mapping 和实际 Controller 一致。
- Compose 只向 app 传 GitHub client 与管理员配置；web build args 只有 `VITE_API_BASE_URL`。Caddy 仍只代理 `/api`、`/oauth2`、`/login/oauth2` 并拒绝 `/actuator`。
- 管理员角色每次根据服务端白名单计算，不信任 Session 角色快照；前端路由守卫只改善体验，服务端始终是最终授权边界。

## 未运行项

- 未使用真实 GitHub OAuth App，也未执行公网 GitHub 登录联调。OAuth 协议集成使用本地 provider stub，浏览器 E2E 使用路由拦截；没有读取、记录或输出真实 client secret、管理员 ID 或 token。
- 未触发 GitHub 托管 CI。本次未提交、未推送，因此没有远端 workflow 运行。
- 未执行公网部署、域名、TLS、备份恢复、生产监控或自动回滚；这些不属于 P1。
- 未开始 P2 或后续内容、作品、评论、媒体和后台业务。

## 仓库与只读参考项目

P1 工作位于 `codex/p1-identity`，从 `origin/main` 创建。Task 1-13 改动保留在工作区，未暂存、未提交、未推送。

只读参考项目 `C:\softWare\project\latest\skillhub` 在本次结束时观察到：

```text
## codex/fix-rerelease-skill-identity
 M server/skillhub-app/src/main/java/com/skillhub/domain/skill/service/SkillPublishService.java
 M server/skillhub-app/src/test/java/com/skillhub/domain/skill/service/SkillPublishServiceTest.java
?? specs/features/P5-BE-RERELEASE-IDENTITY-001/
```

该状态与 P0 记录不同，属于外部并行工作。本次只读取状态，未在该目录修改、创建、删除、格式化、构建或生成文件。

## 下一阶段前置条件

1. 用户审阅 P1 实现、自动化证据和本文件，并明确确认 P1 验收通过。
2. 只有用户明确要求后，才可暂存、提交、推送或创建 PR。
3. P2 必须先具备获批规格，并按阶段分支策略从当时最新的 `origin/main` 创建新的 P2 分支；不得直接从 P1 分支派生。
4. 在上述条件完成前，P2 保持未授权。
