---
project: StudyStack
purpose: AI 会话上下文衔接
updated: 2026-07-17
current_phase: P2
current_status: awaiting-user-acceptance
---

# StudyStack AI 上下文衔接

## 1. 使用方式

新 AI 会话开始后按以下顺序读取：

1. `C:\Users\6666\.codex\AGENTS.md`：本机全局规则，优先级最高。
2. 本文件：获取当前状态、工作边界和高频命令。
3. `specs/PROGRESS.md`：获取最近一次聚合验证证据和未运行项。
4. 当前阶段的 `specs/features/<FEATURE-ID>/IMPLEMENTATION_PLAN.md` 和最新 `SESSION_HANDOFF_AFTER_TASK*.md`；如果目录存在 PRODUCT/TECH，再一并读取。
5. P2 当前最新交接为 `specs/features/P2-CONTENT-PORTFOLIO-001/SESSION_HANDOFF_AFTER_TASK15.md`，不要再以 Task 5 交接作为当前状态。
6. 只有需要追溯总体路线时才读取 `docs/AI-PROJECT-DELIVERY-PLAN.md` 和 `C:\softWare\project\workDoc\java-personal-website-design.md`。

不要只凭本文件修改行为契约。本文件用于定位权威来源，用户当前授权与实施计划仍是执行依据。

## 2. 项目与当前状态

StudyStack 是一个 Java 21 + Vue 3 的模块化单体个人网站项目，运行时包含单体后端、独立 SPA 和唯一 PostgreSQL 数据库。

当前最后完成的工作是 `P2-CONTENT-PORTFOLIO-001` Task 15 聚合验收：

- Task 1-15 已于 2026-07-17 在本地完成 RED、GREEN、REFACTOR 和验证。
- 后端 Maven `verify`：234 个测试通过，JAR 构建成功。
- 前端 Vitest：20 个测试文件、149 个测试通过；lint、typecheck、contract 和 215 模块 build 通过。
- Playwright：11 个 E2E 通过；空数据库下首页、About、博客和项目页状态另经浏览器确认。
- Compose 三个服务 healthy，验收后容器、网络和测试卷已清理。
- P2 状态是 `awaiting-user-acceptance`，不是已授权进入 P3。
- 未执行真实 GitHub OAuth App 联调、GitHub 托管 CI 或公网部署。

详细 C1-C15 证据、命令结果和未运行项以 `specs/PROGRESS.md` 为准。

## 3. 不可突破的边界

- 未经用户明确验收和批准，不得开始 P3，不得创建管理写 API、后台 CRUD 或编辑表单。
- 不得实现 P4 评论、P5 媒体上传、后续运维脚本或部署逻辑。
- 不得引入 Redis、Elasticsearch、消息队列、MinIO、微服务、服务注册中心或 Kubernetes。
- PostgreSQL 是唯一关系数据库，Flyway 是唯一 schema 变更入口，Hibernate 只能使用 `validate`。
- 后端只能使用 `io.swagger.v3.oas.annotations`；不得引入旧版 Swagger 注解。
- 前端 API 对象必须经过 Zod strict schema 与显式 mapping；不得用类型断言信任未校验数据。
- 原始 Markdown 不得进入公开响应；`v-html` 只能存在于统一的 `SafeMarkdownView`。
- 草稿、归档、未来发布与不存在资源在匿名 API、页面和 sitemap 中不得泄露。
- 服务端秘密不得通过 `VITE_`、前端产物、URL、日志、Session 或 E2E 诊断暴露。
- 不得提交或推送，除非用户在当前会话明确要求。
- 工作区包含 Task 1-15 未提交改动及用户已有 P3 规格；不得回滚、清理或格式化无关内容。

## 4. 工程结构与所有权

Spring Modulith 有且只有七个直接模块：

| 模块 | 当前状态 | 所有权 |
|---|---|---|
| `shared` | P0/P2 公共能力已实现 | 配置、OpenAPI、slug、Markdown、SEO、ProblemDetail |
| `identity` | P1 已实现并回归通过 | 用户、GitHub 身份、Session、登录、角色与访问控制 |
| `content` | P2 已实现公开读 | 文章、分类、标签、公开查询与 sitemap 贡献 |
| `portfolio` | P2 已实现公开读 | 简介、项目、技能、经历、公开查询与 sitemap 贡献 |
| `comment` | 仅包边界 | 评论与审核，P4 所有 |
| `media` | 仅包边界 | 媒体元数据和存储抽象，P5 所有 |
| `admin` | 仅包边界 | 后台用例编排，P3 所有 |

P2 只开放匿名 GET。新增写 Controller、管理服务、上传或评论类型前，必须先取得相应阶段授权。

## 5. P2 技术基线

### 后端内容与作品集

- Slug 统一为 3-120 位小写 ASCII，以单个短横线分隔；只做 trim 与小写，发布后不可变。
- 文章与项目状态为 `DRAFT`、`PUBLISHED`、`ARCHIVED`；匿名可见性固定为已发布且 `publishedAt <= now`。
- V3 拥有文章、分类、标签及关联表；V4 拥有简介、项目、技能和经历表。
- Repository 固定公开可见性与排序，不接受调用方任意 `Sort`，不向 web 返回 Entity。
- Markdown 使用 CommonMark 0.28.0 与 OWASP Java HTML Sanitizer 20260313.1；原始 HTML、图片和危险 URL 被拒绝或清理。
- 公开 API 只返回批准 DTO；400/404 使用稳定 `application/problem+json`，隐藏资源与不存在资源统一 404。
- `PublicSiteProperties` 规范化公开 origin，prod 要求 HTTPS；sitemap 和 robots 由后端生成。

### 前端公开站点

- `web/src/features/content` 与 `web/src/features/portfolio` 拥有 strict Zod schema、同源 client 和 TanStack Query options。
- 公开路由为 `/`、`/about`、`/blog`、`/blog/:slug`、`/projects`、`/projects/:slug`。
- 页面覆盖加载、空、错误和 404；统一 `StatusView`、`PaginationNav` 与 `SafeMarkdownView`。
- `usePageSeo` 管理 title、description、canonical 和 Open Graph，并在路由切换/卸载时清理。
- OpenAPI 生成类型位于 `web/src/shared/api/generated/openapi.d.ts`，必须通过 `contract:generate` 生成，不得手工编辑。

### 运行与代理

- Compose 只包含 `postgres`、`app`、`caddy`。秘密与 `STUDYSTACK_PUBLIC_BASE_URL` 只传 app，web build args 只有 `VITE_API_BASE_URL`。
- Caddy 拒绝 `/actuator`，代理 `/api`、`/oauth2`、`/login/oauth2`、`/sitemap.xml` 和 `/robots.txt`，其余路径使用 SPA fallback。
- 数据库为空时不插入演示数据；公开页面必须保持稳定可用状态。

## 6. 本机环境事实

- 所有 PowerShell 操作使用 PowerShell 7 的 `pwsh`；Maven `-D...` 参数通过 `--%`。
- 8080 当前被其他 Java 进程占用。完整拓扑使用 18080，不得停止或修改该外部进程。
- Docker Desktop 与 PostgreSQL 17.7 Testcontainers 已验证可用。
- 当前 Git 分支为 `codex/p2-content-portfolio`，Task 1-15 改动未暂存、未提交、未推送。
- `specs/features/P3-ADMIN-001/` 是用户已有未跟踪内容，不得将其当作 P2 产物清理。
- 只读参考项目 `C:\softWare\project\latest\skillhub` 有外部未提交改动；只能读取，不得修改。

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
pwsh -NoLogo -NoProfile -Command '$env:E2E_BASE_URL="http://127.0.0.1:18080"; Set-Location web; pnpm test:e2e'
pwsh -NoLogo -NoProfile -Command '$env:CADDY_HTTP_PORT="18080"; docker compose --env-file .env.example -f deploy/compose.yml down --volumes --remove-orphans'
```

启动拓扑后必须清理。失败时也要执行 `down --volumes --remove-orphans`，不得影响占用 8080 的其他项目。

## 8. 高效执行约定

- 用户指定 Task 后直接读取对应实施计划，不重复生成计划或设计文档。
- RED 只确认一次目标失败路径；修复后先跑最小相关测试，再跑一次聚合门禁。
- 互不依赖的只读检查可以并行；会写 `.contract` 或共享构建目录的命令按依赖顺序执行。
- 已安装依赖且 lockfile 未变化时不重复安装；Docker 镜像存在时复用缓存。
- 命令失败后先读取完整根因，只按新增信息修正一次，不重复执行相同无效命令。
- 用户要求单个 Task 时，完成验证和清理后立即停止，不自动进入下一阶段。

## 9. 下一步

1. 用户审阅 `specs/PROGRESS.md`、P2 页面/API、安全结论和自动化证据。
2. 新会话以 `specs/features/P2-CONTENT-PORTFOLIO-001/SESSION_HANDOFF_AFTER_TASK15.md` 恢复上下文。
3. 用户明确确认 P2 是否验收通过。
4. 用户单独授权后，才可处理暂存、提交、推送或 PR。
5. P3 必须另行读取并批准其规格与实施计划；当前状态不授权开始 P3。
