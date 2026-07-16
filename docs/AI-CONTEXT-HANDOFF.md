---
project: StudyStack
purpose: AI 会话上下文衔接
updated: 2026-07-16
current_phase: P1
current_status: awaiting-user-acceptance
---

# StudyStack AI 上下文衔接

## 1. 使用方式

新 AI 会话开始后按以下顺序读取：

1. `C:\Users\6666\.codex\AGENTS.md`：本机全局规则，优先级最高。
2. 本文件：获取当前状态、工作边界和高频命令。
3. `specs/PROGRESS.md`：获取最近一次验证证据和未运行项。
4. 当前阶段的 `specs/features/<FEATURE-ID>/IMPLEMENTATION_PLAN.md`；如果目录存在 PRODUCT/TECH，再一并读取。
5. 只有需要追溯总体路线时才读取 `docs/AI-PROJECT-DELIVERY-PLAN.md` 和 `C:\softWare\project\workDoc\java-personal-website-design.md`。

不要只凭本文件修改行为契约。本文件用于定位权威来源，当前获批任务和实施计划仍是执行依据。

## 2. 项目与当前状态

StudyStack 是一个 Java 21 + Vue 3 的模块化单体个人网站项目，运行时包含单体后端、独立 SPA 和唯一 PostgreSQL 数据库。

当前最后完成的工作是 `P1-IDENTITY-001` Task 13 聚合验收：

- Task 1-13 已于 2026-07-16 在本地完成并验证。
- 后端 Maven `verify`：117 个测试通过。
- 前端 Vitest：11 个测试文件、85 个测试通过。
- Playwright：8 个 E2E 通过。
- Compose 三个服务 healthy，验收后容器、网络和测试卷已清理。
- P1 状态是 `awaiting-user-acceptance`，不是已授权进入 P2。
- 未执行真实 GitHub OAuth App 联调，也未触发 GitHub 托管 CI。

详细 I1-I13 证据、命令结果和未运行项以 `specs/PROGRESS.md` 为准。

## 3. 不可突破的边界

- 未经用户明确验收和批准，不得开始 P2，不得实现内容、作品、评论、媒体或后台 CRUD。
- P2 必须从当时最新 `origin/main` 创建新的阶段分支，不得直接从 `codex/p1-identity` 派生。
- 不得引入 Redis、Elasticsearch、消息队列、MinIO、微服务、服务注册中心或 Kubernetes。
- PostgreSQL 是唯一关系数据库，Flyway 是唯一 schema 变更入口，Hibernate 只能使用 `validate`。
- 后端只能使用 `io.swagger.v3.oas.annotations`；不得引入旧版 Swagger 注解。
- 前端 API 数据进入 union 或 enum 前必须经过 Zod 或明确的 runtime filter/map，不得用类型断言掩盖未经验证的数据。
- 服务端秘密不得通过 `VITE_` 变量、前端产物、URL、日志或 Session 暴露。
- 路由守卫不能替代服务端授权；`/api/v1/admin/**` 必须继续由服务端 ADMIN 规则保护。
- 不得提交或推送，除非用户在当前会话明确要求。
- 工作区包含 Task 1-13 未提交改动。只修改当前任务相关文件，不回滚、不格式化无关内容。

## 4. 工程结构与所有权

Spring Modulith 有且只有七个直接模块：

| 模块 | 当前状态 | 所有权 |
|---|---|---|
| `shared` | 基础配置、OpenAPI、生产环境保护 | 稳定公共约定 |
| `identity` | P1 已实现 | 用户、GitHub 身份、Session、登录、角色与访问控制 |
| `content` | 仅包边界 | 文章、分类、标签与 SEO |
| `portfolio` | 仅包边界 | 简介、经历、技能与作品 |
| `comment` | 仅包边界 | 评论与审核 |
| `media` | 仅包边界 | 媒体元数据和存储抽象 |
| `admin` | 仅包边界 | 后台用例编排 |

新增模块、跨模块引用、业务表、Controller、Entity、Repository 或应用服务前，必须先由对应阶段规格定义所有权和依赖方向。

## 5. P1 技术基线

### 后端身份

- Spring Security OAuth2 Client 负责 GitHub authorization code、state、callback、Session fixation 和 CSRF。
- 用户与外部身份通过 `IdentityBindingService` 绑定；GitHub claims 只由 `GitHubClaimsNormalizer` 解析。
- Flyway V2 拥有 identity 与 Spring Session JDBC 表；`spring.session.jdbc.initialize-schema=never`。
- Session 最大空闲 24 小时，每 10 分钟清理；Cookie 为 `STUDYSTACK_SESSION`、HttpOnly、SameSite=Lax、Path=/，prod 强制 Secure。
- `GET /api/v1/auth/me`、`GET /api/v1/auth/csrf`、`POST /api/v1/auth/logout` 是固定认证 API。
- 所有用户拥有 USER；ADMIN 每次由 `STUDYSTACK_ADMIN_GITHUB_IDS` 计算；禁用账号使已有 Session 失效。
- SecurityContext 只保存最小 `StudyStackPrincipal`，authorized client 登录后删除。

### 前端身份

- `web/src/features/auth` 拥有严格 Zod schema、同源 fetch client、CSRF 内存缓存、TanStack Query 和安全 returnTo。
- Pinia 不保存用户、角色、token 或 Session 副本。
- 路由包含 `/login`、`/admin`、`/forbidden`；游客、USER、ADMIN 体验由可注入守卫测试。
- OAuth 入口固定为 `/oauth2/authorization/github`；失败页只显示批准的固定错误文案。
- P1 OpenAPI 生成类型位于 `web/src/shared/api/generated/openapi.d.ts`。

### 运行与 CI

- Compose 只包含 `postgres`、`app`、`caddy`。GitHub client 和管理员变量只传 app，web build args 只有 `VITE_API_BASE_URL`。
- Caddy 代理 `/api`、`/oauth2`、`/login/oauth2`，拒绝 `/actuator`，其他路径使用 SPA fallback。
- CI 保持 backend、frontend、contract、compose、e2e 五个只读 job；E2E job 运行全部 Playwright。
- CI 不部署、不登录镜像仓库、不推送镜像、不创建 Release 或远程资源。

## 6. 本机环境事实

- 所有 PowerShell 操作使用 PowerShell 7 的 `pwsh`；Maven `-D...` 参数通过 `--%`。
- 8080 当前被其他 Java 进程占用。完整拓扑使用 18080，不得停止或修改该外部进程。
- Docker Desktop 与 PostgreSQL 17.7 Testcontainers 已验证可用。
- 当前 Git 分支为 `codex/p1-identity`，Task 1-13 改动未暂存、未提交、未推送。
- 只读参考项目 `C:\softWare\project\latest\skillhub` 当前有外部未提交改动；只能读取，不得修改。

## 7. 标准验证入口

按改动范围先跑最小命令；只有阶段聚合验收才运行全套。不要并行运行会同时创建或删除 `web/.contract` 的 Vitest、contract 和 lint 命令。

### 后端

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd verify'
```

### 前端

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location web; pnpm install --frozen-lockfile'
pwsh -NoLogo -NoProfile -Command 'Set-Location web; pnpm lint'
pwsh -NoLogo -NoProfile -Command 'Set-Location web; pnpm typecheck'
pwsh -NoLogo -NoProfile -Command 'Set-Location web; pnpm test'
pwsh -NoLogo -NoProfile -Command 'Set-Location web; pnpm contract:check'
pwsh -NoLogo -NoProfile -Command 'Set-Location web; pnpm build'
```

### Compose 与 E2E

```powershell
pwsh -NoLogo -NoProfile -Command '$env:CADDY_HTTP_PORT="18080"; docker compose --env-file .env.example -f deploy/compose.yml up -d --build --wait'
pwsh -NoLogo -NoProfile -Command '$env:CADDY_HTTP_PORT="18080"; Set-Location web; pnpm test:e2e'
pwsh -NoLogo -NoProfile -Command '$env:CADDY_HTTP_PORT="18080"; docker compose --env-file .env.example -f deploy/compose.yml down --volumes'
```

启动拓扑后必须清理。失败时也要执行 `down --volumes`，不得影响占用 8080 的其他项目。

## 8. 高效执行约定

- 用户指定 Task 后直接读取实施计划并执行，不重复生成计划或新文档。
- RED 只确认一次目标失败路径；修复后先跑最小相关测试，再跑一次聚合门禁。
- 互不依赖的只读检查可以并行；会写相同目录的生成、测试、lint 和契约命令按依赖顺序执行。
- 已安装依赖且 lockfile 未变化时，不重复安装；Docker 镜像存在时复用缓存。
- 命令失败后先读取明确根因，只按新增信息修正一次，不重复执行同一无效命令。
- 用户要求单个 Task 时，完成验证和清理后立即停止，不提前进入下一阶段。

## 9. 下一步

1. 用户审阅 `specs/PROGRESS.md`、P1 页面/API 和自动化证据。
2. 用户明确确认 P1 是否验收通过。
3. 只有在 P1 已验收且 P2 规格获批后，才从当时最新 `origin/main` 创建新的 P2 阶段分支。
4. 未经明确指令，不暂存、不提交、不推送，也不创建 PR。
