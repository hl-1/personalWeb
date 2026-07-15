# StudyStack AI Project Delivery Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 使用 AI 分阶段完成 StudyStack 个人网站，使每个阶段都有已批准规格、可运行增量、自动化测试和明确交接证据。

**Architecture:** 前端使用 Vue 3 SPA，由 Caddy 托管静态资源并反向代理 API；后端使用 Java 21、Spring Boot 和 Spring Modulith 构建模块化单体；PostgreSQL 作为主数据库。项目按纵向业务能力分阶段交付，不一次性生成全部代码，不提前引入微服务和重型中间件。

**Tech Stack:** Java 21、Spring Boot 3.x、Spring Modulith、Spring Security、Spring Data JPA、PostgreSQL、Flyway、OpenAPI 3、Vue 3、TypeScript、Vite、TanStack Query、Pinia、Zod、Vitest、Playwright、Docker Compose、Caddy、GitHub Actions

---

## 1. 执行原则

1. 每个阶段先创建 `PRODUCT.md`、`TECH.md` 和 `IMPLEMENTATION_PLAN.md`，用户批准后才实现。
2. 一个 AI 任务只执行一个计划任务，不跨阶段、不顺便重构。
3. 每个行为使用稳定编号，并建立“行为 -> 代码 -> 测试 -> 验收证据”追踪。
4. 实现严格遵循 RED、GREEN、REFACTOR；测试必须先因缺少目标行为而失败。
5. 只读取当前阶段相关模块，避免每轮把整个仓库装入上下文。
6. 设计文档和已批准规格是事实来源；遇到冲突先更新规格并重新确认。
7. `C:\softWare\project\latest\skillhub` 始终只读，只抽取通用模式，不复制业务实现。
8. 没有新鲜命令输出时，不得声称编译、测试、构建或功能通过。
9. 提交和推送只在用户明确要求时执行，并只包含当前任务相关文件。

## 2. 项目事实来源

每个新任务按以下顺序读取：

1. `C:\Users\6666\.codex\AGENTS.md`
2. `C:\softWare\project\workDoc\java-personal-website-design.md`
3. 当前阶段的 `specs/features/<阶段 ID>/PRODUCT.md`
4. 当前阶段的 `specs/features/<阶段 ID>/TECH.md`
5. 当前阶段的 `specs/features/<阶段 ID>/IMPLEMENTATION_PLAN.md`
6. `specs/PROGRESS.md`
7. 当前任务直接相关的代码和测试

归档文档、旧方案和参考项目只能用于补充上下文，不能覆盖已批准事实。

## 3. 阶段总览

| 阶段 | 规格目录 | 可运行交付物 | 主要依赖 |
|---|---|---|---|
| P0 | `P0-FOUNDATION-001` | 前后端骨架、数据库、OpenAPI、测试与 CI | 无 |
| P1 | `P1-IDENTITY-001` | GitHub OAuth、用户、Session、角色 | P0 |
| P2 | `P2-CONTENT-PORTFOLIO-001` | 文章、分类、标签、作品及公开页面 | P1 |
| P3 | `P3-ADMIN-001` | 文章与作品管理后台、Markdown 预览 | P2 |
| P4 | `P4-COMMENT-001` | 评论提交、审核、权限与反滥用 | P1、P2、P3 |
| P5 | `P5-MEDIA-001` | 安全上传、本地存储和 S3 兼容抽象 | P0、P3 |
| P6 | `P6-HARDENING-001` | 安全、可观测性、备份、契约与性能基线 | P1-P5 |
| P7 | `P7-DEPLOYMENT-001` | Docker、Caddy、HTTPS、CI/CD、回滚和上线 | P6 |

每个阶段必须独立验收。后续阶段不得以“以后补测试”为理由修复前一阶段的质量缺口。

## 4. P0 工程底座

**目标：** 建立可重复构建、测试和运行的最小工程，不实现业务。

**规格：**

- `specs/features/P0-FOUNDATION-001/PRODUCT.md`
- `specs/features/P0-FOUNDATION-001/TECH.md`
- `specs/features/P0-FOUNDATION-001/IMPLEMENTATION_PLAN.md`

**任务：**

- [ ] 创建后端 Maven Wrapper、Java 21 和 Spring Boot 工程。
- [ ] 建立 `content`、`portfolio`、`identity`、`comment`、`media`、`shared` 包边界。
- [ ] 先写 Spring Modulith 边界测试，再创建最小模块声明。
- [ ] 先写 PostgreSQL Testcontainers 失败测试，再配置数据源和 Flyway baseline。
- [ ] 先写 Actuator 与 OpenAPI 契约测试，再配置健康检查和 springdoc-openapi。
- [ ] 创建 Vue 3、TypeScript、Vite 和 pnpm 工程。
- [ ] 配置 Vue Router、TanStack Query、Pinia 和 Zod 基础入口。
- [ ] 先写首页骨架和深层路由失败测试，再实现最小页面与 SPA fallback。
- [ ] 创建开发 Docker Compose、Caddyfile 和 `.env.example`。
- [ ] 创建前端、后端和契约检查的 GitHub Actions 工作流。
- [ ] 创建 `specs/PROGRESS.md`，记录阶段状态和下一任务。

**验收命令：**

```powershell
pwsh -NoLogo -NoProfile -Command "Set-Location server; .\mvnw.cmd verify"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm typecheck; pnpm test; pnpm build"
pwsh -NoLogo -NoProfile -Command "docker compose -f deploy/compose.yml config"
```

**停止门禁：** 后端测试、前端测试、构建、OpenAPI生成和 Compose 解析全部通过后，才能进入 P1。

## 5. P1 身份与权限

**目标：** 用户能够通过 GitHub 登录，系统能够区分普通用户和管理员。

**规格目录：** `specs/features/P1-IDENTITY-001`

**核心行为：**

- GitHub首次登录自动创建本地用户。
- 使用 GitHub 不可变数字用户 ID 绑定身份。
- Session Cookie 使用 `HttpOnly`、`Secure` 和合适的 `SameSite`。
- Vue通过 `/api/v1/auth/me` 获取当前用户。
- 修改请求携带有效 CSRF Token。
- 管理员白名单在服务端判断；前端守卫不能替代服务端授权。
- 禁用、未知或信息不完整的账号进入明确失败路径。

**任务边界：** 复用参考项目的 GitHub claims、identity binding 和安全测试模式，但移除 namespace、SAML、Device Code、API Token、Redis OAuth 状态和复杂 RBAC。

**验收：** OAuth成功、拒绝、重复登录、账号禁用、非管理员越权、CSRF失败和 Session过期均有自动化测试。

## 6. P2 内容与作品

**目标：** 匿名访客能够浏览文章和作品，管理员拥有后续管理所需的稳定领域模型。

**规格目录：** `specs/features/P2-CONTENT-PORTFOLIO-001`

**核心能力：**

- 文章草稿、发布、归档状态。
- 分类、标签和稳定 slug。
- 作品、技能、经历和项目链接。
- Markdown存储与白名单渲染。
- 公开列表、详情、分页和不存在状态。
- 标题、description、canonical、Open Graph、`sitemap.xml` 和 `robots.txt`。

**边界：** P2只实现公开读取和领域规则；后台编辑交互进入 P3，文件上传进入 P5。

**验收：** 发布状态可见性、slug冲突、Markdown XSS、分页边界、404和 SEO输出均有测试。

## 7. P3 管理后台

**目标：** 管理员能够安全管理文章、分类、标签和作品。

**规格目录：** `specs/features/P3-ADMIN-001`

**核心能力：**

- 管理路由和服务端管理员授权。
- 文章与作品的创建、修改、发布、归档和预览。
- Markdown编辑与安全预览。
- 表单 Zod校验和服务端 Bean Validation。
- 乐观并发控制或明确的更新冲突处理。
- 加载、空、错误、未授权和保存中状态。

**验收：** 普通用户无法调用管理 API；非法枚举、重复 slug、并发修改和恶意 Markdown均被明确处理。

## 8. P4 评论与审核

**目标：** 已登录访客能够发表评论，管理员能够审核，未审核内容不公开。

**规格目录：** `specs/features/P4-COMMENT-001`

**核心能力：**

- 评论状态 `PENDING`、`APPROVED`、`REJECTED`。
- 评论作者只能管理自己的允许操作。
- 管理员审核、拒绝和删除。
- 内容长度、链接数量、重复提交和频率限制。
- HTML/Markdown白名单处理和审计记录。

**验收：** 未登录、越权、重复请求、恶意内容、限流、审核前不可见和审核后可见路径全部覆盖。

## 9. P5 媒体存储

**目标：** 管理员能够上传文章和作品图片，存储实现可从本地磁盘迁移到 S3兼容服务。

**规格目录：** `specs/features/P5-MEDIA-001`

**核心能力：**

- 统一 `MediaStorage` 接口。
- 本地持久化目录实现。
- 文件大小、扩展名、真实 MIME和图片尺寸验证。
- 不可预测的对象键和路径穿越防护。
- 数据库元数据与物理文件的一致性补偿。
- 删除引用检查和失败重试。

**验收：** 伪造扩展名、超大文件、路径穿越、重复文件、存储失败和数据库回滚路径均有测试。

## 10. P6 安全与运维加固

**目标：** 在部署前建立可观测、安全、可备份和可恢复的生产基线。

**规格目录：** `specs/features/P6-HARDENING-001`

**任务：**

- [ ] 统一错误结构、请求 ID 和敏感日志清洗。
- [ ] 校验 CSP、HSTS、Referrer-Policy 和上传安全头。
- [ ] 检查 OpenAPI注解、DTO、实际响应和前端类型一致性。
- [ ] 运行依赖、密钥和容器镜像安全检查。
- [ ] 建立 PostgreSQL 与媒体目录备份脚本和恢复演练。
- [ ] 建立健康检查、日志轮转、磁盘和备份结果监控。
- [ ] 建立首页、文章列表和后台关键操作的性能基线。

**验收：** 必须展示一次独立环境恢复成功的证据，不能只证明备份文件存在。

## 11. P7 部署与上线

**目标：** 将已验收版本部署到单台服务器，通过域名和 HTTPS稳定访问并可回滚。

**规格目录：** `specs/features/P7-DEPLOYMENT-001`

**核心能力：**

- Caddy托管 Vue静态文件并反向代理 Spring Boot。
- Spring Boot、PostgreSQL 和 Caddy 使用 Docker Compose运行。
- 镜像使用版本号或提交哈希，不使用浮动 `latest`。
- Caddy自动申请证书并强制 HTTPS。
- GitHub OAuth线上回调使用正式域名。
- 生产环境关闭匿名 Swagger UI和 OpenAPI端点。
- 发布执行迁移、启动、健康检查和关键流程 smoke test。
- 失败时能够恢复上一镜像和兼容数据库版本。

**上线验收：**

- HTTP跳转 HTTPS。
- 首页、文章详情和作品详情可访问。
- GitHub登录与回调成功。
- 普通用户无法访问后台。
- 管理员能够发布文章并审核评论。
- 数据和媒体在重启后保持。
- 备份、监控、日志和回滚流程有验证记录。

## 12. AI 单任务执行模板

每次只把当前任务交给 AI：

```text
请读取全局 AGENTS.md、总体设计、当前阶段 PRODUCT/TECH/IMPLEMENTATION_PLAN、specs/PROGRESS.md，以及本任务直接相关代码。

只执行 IMPLEMENTATION_PLAN 中的 Task N，不处理后续任务。
先写测试并确认因缺少目标行为而失败，再写最小实现。
运行任务指定的全部验证，更新规格和 PROGRESS。
不要修改无关文件，不要提交或推送，完成后汇报命令结果、变更文件和剩余风险。
```

## 13. AI 效率控制

- 每个任务控制在一个清晰行为或一个基础设施边界内。
- 使用 `rg` 定位文件，不递归读取整个仓库内容。
- 每轮只加载当前 PRODUCT、TECH、计划任务和相关代码。
- 将稳定决策写回规格，不依赖对话历史保存事实。
- 先运行定向测试；共享契约、数据库或安全边界变化后再扩大测试范围。
- 失败时先定位根因，不通过重复重写文件碰运气。
- UI功能必须通过浏览器截图和关键交互验证，不能只依赖组件测试。
- 每个阶段结束后清理无用依赖、占位代码和临时配置，但不做无关重构。

## 14. 阶段完成报告模板

每阶段结束时，AI必须提供：

```text
阶段：P<N>
已实现行为：B1、B2、...
变更范围：<模块和文件>
验证命令：<逐条命令>
验证结果：<通过数量或失败详情>
未运行检查：<原因>
数据迁移：<无或迁移与回滚方式>
安全结论：<权限、输入和密钥检查>
Git状态：<当前分支与任务相关文件>
剩余风险：<具体风险>
下一阶段前置条件：<明确条件>
```

阶段报告缺少验证证据时，该阶段不得标记完成。
