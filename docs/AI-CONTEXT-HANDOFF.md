---
project: StudyStack
purpose: AI 会话上下文衔接
updated: 2026-07-15
current_phase: P0
current_status: awaiting-user-acceptance
---

# StudyStack AI 上下文衔接

## 1. 使用方式

新 AI 会话开始后按以下顺序读取：

1. `C:\Users\6666\.codex\AGENTS.md`：本机全局规则，优先级最高。
2. 本文件：获取当前状态、工作边界和高频命令。
3. `specs/PROGRESS.md`：获取最近一次验证证据和未运行项。
4. 当前阶段的 `specs/features/<FEATURE-ID>/PRODUCT.md`、`TECH.md` 和 `IMPLEMENTATION_PLAN.md`。
5. 只有需要追溯总体路线时才读取 `docs/AI-PROJECT-DELIVERY-PLAN.md` 和 `C:\softWare\project\workDoc\java-personal-website-design.md`。

不要只凭本文件修改行为契约。本文件用于定位权威来源，PRODUCT 的稳定行为编号和当前获批任务仍是实施依据。

## 2. 项目与当前状态

StudyStack 是一个 Java 21 + Vue 3 的个人网站项目。运行时采用单体后端、独立 SPA 和唯一 PostgreSQL 数据库，不是微服务项目。

当前最后完成的工作是 `P0-FOUNDATION-001` Task 12 聚合验收：

- B1-B12 已在 2026-07-15 本地验证通过。
- 后端 Maven `verify`：23 个测试通过。
- 前端 Vitest：6 个测试文件、26 个测试通过。
- Playwright：3 个路由 E2E 通过。
- PostgreSQL、应用和 Caddy 的 Compose 健康检查通过，测试容器、网络和数据卷已清理。
- P0 状态是 `awaiting-user-acceptance`，不是已授权进入 P1。
- 下一步必须先获得用户对 P0 的明确验收，再为 P1 创建或批准独立 PRODUCT、TECH 和实施计划。

最新证据和修复记录以 `specs/PROGRESS.md` 为准。

## 3. 不可突破的边界

- 未经用户明确批准，不得开始 P1，不得实现登录、文章、作品、评论、上传或后台业务。
- 不得引入 Redis、Elasticsearch、消息队列、MinIO、微服务、服务注册中心或 Kubernetes。
- 后端只能使用 OpenAPI 3 新版注解，来源为 `io.swagger.v3.oas.annotations`；不得引入旧版 Swagger 注解。
- PostgreSQL 是唯一关系数据库，Flyway 是唯一 schema 变更入口，Hibernate 只能使用 `validate`。
- 前端 API 数据进入 union 或 enum 前必须经过运行时校验或明确的 filter/map，不得用类型断言掩盖未经验证的数据。
- 示例配置不得包含真实密钥，服务端秘密不得通过 `VITE_` 变量进入前端。
- 不得提交或推送，除非用户在当前会话明确要求。
- 工作区可能包含用户改动。只修改当前任务相关文件，不回滚、不格式化无关文件。

## 4. 工程结构与所有权

```text
studystack/
├── server/                         Java 21、Spring Boot 3.5.16、单 Maven 工程
├── web/                            Vue 3、TypeScript、Vite、pnpm 11.9.0
├── deploy/                         Docker Compose 与 Caddy
├── .github/workflows/ci.yml        只读 CI 质量门禁
├── specs/features/                 按功能编号管理的 PRODUCT/TECH/计划
├── specs/PROGRESS.md               最近验证证据
└── docs/                           总体计划、实施提示与本衔接文档
```

Spring Modulith 有且只有七个直接模块：

| 模块 | P0 状态 | 后续所有权 |
|---|---|---|
| `shared` | 基础配置、OpenAPI 配置、生产环境保护 | 稳定公共约定 |
| `identity` | 仅包边界 | 登录、用户、角色与访问控制 |
| `content` | 仅包边界 | 文章、分类、标签与 SEO |
| `portfolio` | 仅包边界 | 简介、经历、技能与作品 |
| `comment` | 仅包边界 | 评论与审核 |
| `media` | 仅包边界 | 媒体元数据和存储抽象 |
| `admin` | 仅包边界 | 后台用例编排 |

新增模块、跨模块引用、业务表、Controller、Entity、Repository 或应用服务前，必须先由对应阶段规格定义所有权和依赖方向。

## 5. 已建立的技术基线

### 后端

- Java 21，Spring Boot 3.5.16，单 Maven POM，Maven Wrapper。
- Spring Modulith、Spring MVC、Actuator、Data JPA、Flyway、PostgreSQL、springdoc-openapi。
- PostgreSQL 17.7 Testcontainers 覆盖正常连接、连接失败和 Flyway checksum 失败。
- `dev`、`test` 提供 `/v3/api-docs` 与 Swagger UI；`prod` 在应用层关闭这些路径。
- Actuator 只暴露 `health`、`info`，readiness 包含数据库状态。

### 前端

- Vue 3、TypeScript strict、Vite、Vue Router、TanStack Query for Vue、Pinia、Zod。
- Vitest 只收集 `src/**/*.spec.ts`；Playwright 单独收集 `web/e2e/`。
- 路由仅有 `/`、`/foundation` 和前端未找到页面。
- OpenAPI 类型生成到 `web/src/shared/api/generated/openapi.d.ts`。

### 运行与 CI

- Compose 只包含 `postgres`、`app`、`caddy`。
- Caddy 提供 Vue 静态文件和 SPA fallback，代理 `/api`、`/oauth2`、`/login/oauth2`，拒绝 `/actuator`。
- CI 只包含 `backend`、`frontend`、`contract`、`compose`、`e2e` 五个验证 job，根权限为 `contents: read`。
- CI 不部署、不登录镜像仓库、不推送镜像、不创建 Release 或远程资源。

## 6. 本机环境事实

- 所有 PowerShell 命令必须通过 PowerShell 7 的 `pwsh` 执行。
- 8080 当前曾被其他本地 Java 项目占用。需要启动 StudyStack 完整拓扑时，优先使用 18080，除非已确认 8080 可用且不会影响其他项目。
- Docker Desktop 拉取镜像已在本机验证成功。当前手动代理曾配置为 `http://127.0.0.1:7890`，容器代理使用与宿主机相同的代理。
- Docker 的 `192.168.65.0/24` 是内部网络地址段，不是代理 IP，不要为代理问题修改该地址段。
- StudyStack 根目录当前存在 `.git` 元数据，但最近的 P0 实施没有提交或推送。
- 只读参考项目是 `C:\softWare\project\latest\skillhub`。禁止在该目录修改、创建、删除、格式化、构建或生成文件；最近状态是 `## codex/new-feature-development` 且无文件状态行。

## 7. 标准验证入口

从项目根目录执行，按改动范围选择最小命令；只有阶段聚合验收才运行全套。

### 后端全量

```powershell
pwsh -NoLogo -NoProfile -Command "Set-Location server; .\mvnw.cmd verify"
```

### 前端质量门禁

```powershell
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm install --frozen-lockfile"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm lint"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm typecheck"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm test"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm contract:check"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm build"
```

### Compose 与 E2E（本机 18080）

```powershell
pwsh -NoLogo -NoProfile -Command '$env:CADDY_HTTP_PORT="18080"; docker compose --env-file .env.example -f deploy/compose.yml up -d --build --wait'
pwsh -NoLogo -NoProfile -Command '$env:E2E_BASE_URL="http://127.0.0.1:18080"; Set-Location web; pnpm test:e2e -- foundation-routing.spec.ts'
pwsh -NoLogo -NoProfile -Command '$env:CADDY_HTTP_PORT="18080"; docker compose --env-file .env.example -f deploy/compose.yml down --volumes'
```

启动拓扑后必须清理。E2E 失败时也要执行 `down --volumes`，但不得停止或修改占用 8080 的其他项目。

## 8. 高效执行约定

- 先读当前任务原文和涉及文件，再实施；不要重复读取整仓库或重复检查 Git。
- RED 只证明目标失败路径，确认一次即可。不要重复运行已得到相同结论的慢命令。
- 修复后先跑最小相关测试，再按任务要求跑一次聚合测试。
- 互不依赖的只读检查可以并行；构建、契约生成和会写相同目录的命令需要按依赖顺序执行。
- 已安装依赖且 lockfile 未变化时，不要重复安装；Docker 镜像存在时优先复用缓存。
- 网络或 Docker 命令失败时先读取一次明确错误，定位代理、DNS、端口或守护进程归属；不要在同一无效路径上循环重试。
- 不要把 Docker 内部子网、Windows 系统代理、Docker Desktop 代理和容器出站代理混为同一配置。
- 用户要求单个 Task 时，完成该 Task 的验证和清理后立即停止，不提前执行后续 Task。

## 9. 后续会话交接格式

每次完成一个获批阶段或出现会影响后续工作的环境变化时，更新本文件中的以下内容：

1. `updated`、`current_phase` 和 `current_status`。
2. 最后完成的规格编号与任务。
3. 新增或改变的架构边界、迁移版本和契约入口。
4. 最近一次全量验证结果及未运行项。
5. 仍存在的环境约束、失败路径和用户待确认事项。

详细测试数字继续记录在 `specs/PROGRESS.md`；本文件保持为可快速读取的上下文索引，避免演变成第二份完整规格。
