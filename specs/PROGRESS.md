---
phase: P2
feature: P2-CONTENT-PORTFOLIO-001
status: awaiting-user-acceptance
verified: 2026-07-17
---

# StudyStack 实施进度

## 当前状态

P2 Content And Portfolio 的 Task 1-15 已在本地完成 RED、GREEN、REFACTOR 和聚合验证，当前状态为 `P2 / awaiting-user-acceptance`。该状态只表示等待用户验收 P2，不代表已批准 P3，也不授权创建管理写接口、暂存、提交、推送或创建 PR。

验证环境为 Windows、PowerShell 7、Java 21.0.10、Node 24、pnpm 11.9.0、Docker Desktop 和 PostgreSQL 17.7。本机 8080 端口由既有 Java 进程占用，Compose 与 Playwright 使用 18080；未停止或修改该进程。

## C1-C15 验证证据

| 行为 | 主要实现与自动化证据 | 2026-07-17 结果 |
|---|---|---|
| C1 Slug | `SlugPolicyTest`、内容/作品 Repository 与 schema 测试 | 3-120 位小写 ASCII、统一归一化、唯一性及发布后不可变通过 |
| C2 Markdown | `SafeMarkdownRendererTest`、`safe-html.spec.ts`、`SafeMarkdownView` | GFM 表格/删除线通过；脚本、原始 HTML、危险 URL、图片和事件属性不可执行 |
| C3 文章可见性 | 内容 schema、Repository、query 集成测试 | 仅当前已发布文章可见；草稿、归档和未来发布时间均隐藏 |
| C4 文章分页筛选 | `PublicArticleQueryIntegrationTest`、API/client/view 测试 | 0 基分页、1-50 size、分类+标签组合筛选及稳定排序通过 |
| C5 文章详情 SEO | 内容 API、页面 SEO 与详情视图测试 | 详情、404、title、description 和 canonical 路径通过 |
| C6 分类标签 | taxonomy query/API 测试 | 只返回含当前公开文章的分类/标签，计数与排序稳定 |
| C7 简介 | portfolio query/API/view 测试及空数据库验收 | 公开简介读取、404 与首页/About 稳定状态通过 |
| C8 项目 | V4、Repository、query/API/view 测试 | 发布可见性、精选、分页、详情、404 和稳定排序通过 |
| C9 技能经历 | portfolio query/API/view 测试 | 技能和经历稳定排序、日期约束、安全摘要与空状态通过 |
| C10 API 契约 | Controller、ProblemDetail、OpenAPI 集成测试 | P2 GET API、分页/slug 400、隐藏资源 404 和响应字段约束通过 |
| C11 前端数据层 | content/portfolio Zod、client、query Vitest | strict runtime schema、同源 client、缓存 key 与 ProblemDetail 映射通过 |
| C12 页面体验 | view Vitest、Playwright、空数据库浏览器验收 | 首页、About、博客、项目及详情的内容、加载、空、错误和 404 状态通过 |
| C13 Meta SEO | `use-page-seo.spec.ts` 与浏览器验收 | title、description、canonical、Open Graph 更新和卸载清理通过 |
| C14 sitemap/robots | `SeoEndpointIntegrationTest`、Caddy 测试与 E2E | 只含公开 URL；robots 禁止敏感前缀；两端点不落入 SPA fallback |
| C15 聚合门禁 | Modulith、P2 scope、契约、Compose 与全量验证 | 七模块边界保持，P3/P4/P5 越界类型及 P2 写接口均被拒绝 |

## 聚合命令结果

| 命令 | 最终结果 |
|---|---|
| `server/.\mvnw.cmd verify` | 状态 0，234 个后端测试通过，JAR 构建成功 |
| `pnpm install --frozen-lockfile` | 状态 0，lockfile 未改写 |
| `pnpm lint` | 状态 0，无 warning |
| `pnpm typecheck` | 状态 0 |
| `pnpm test` | 状态 0，20 个测试文件、149 个 Vitest 测试通过 |
| `pnpm contract:check` | 状态 0，生成 TypeScript 与 P2 OpenAPI 一致 |
| `pnpm build` | 状态 0，215 个模块完成生产构建 |
| `docker compose ... config --quiet` | 状态 0 |
| `node --test deploy/tests/compose-config.test.mjs` | 状态 0，6 个 Compose/Caddy 配置测试通过 |
| `docker compose ... up -d --build --wait` | 状态 0，PostgreSQL、app、Caddy 全部 healthy |
| `pnpm test:e2e` | 状态 0，11 个 Playwright 测试通过 |
| `docker compose ... down --volumes --remove-orphans` | 状态 0，容器、网络和 `studystack_postgres-data` 已删除 |

首次聚合 RED 暴露两个旧启动测试未适配 P2 上下文：生产环境策略探针实例化了无关的 P2 查询 bean，Session 的 prod profile 缺少 HTTPS 公开基址。分别通过延迟初始化探针上下文和显式传入 `STUDYSTACK_PUBLIC_BASE_URL=https://example.com` 修复；定向测试通过后完整 `verify` 转绿。

## 数据与安全结论

- Flyway 当前包含 V1 基线、V2 identity/session、`V3__content_schema.sql` 和 `V4__portfolio_schema.sql`。V3/V4 的状态、长度、唯一 slug、关系、排序索引、乐观锁和外键约束由 PostgreSQL Testcontainers 验证。
- Hibernate 保持 `validate`，数据库 schema 继续只归 Flyway 所有；P2 不插入演示数据。
- 原始 Markdown 只保存在数据库，匿名 API 只返回经过 CommonMark 与 OWASP Sanitizer 处理的 HTML；前端 `v-html` 仅存在于 `SafeMarkdownView`。
- 匿名 API 不返回 Entity、原始 Markdown、status、version、sortOrder 或数据库外键；Zod strict schema 拒绝未知字段和危险 HTML。
- sitemap 只包含静态公开页、当前公开文章和项目；robots 禁止 `/admin`、`/api`、`/oauth2` 与 `/login/oauth2`。
- `STUDYSTACK_PUBLIC_BASE_URL` 仅注入 app；prod 要求 HTTPS。GitHub secret 和管理员配置不进入 web 构建参数或浏览器产物。

## 空数据库与页面结论

- 首页保留 StudyStack 站点身份，并显示简介、最近文章和精选项目的稳定空状态。
- `/blog` 与 `/projects` 显示稳定空状态；`/about` 对不存在简介显示固定不可用状态，对技能和经历显示空状态。
- 四个公开页面均保留页面级 `h1`、正确 document title，1280 像素验收无横向溢出。
- route mock E2E 另行覆盖六个公开页面的完整内容、错误与 404 状态，不依赖演示数据。

## 未实现与未运行项

- P3 管理写接口、后台 CRUD 和编辑表单未实现；P4 评论、P5 媒体上传及后续运维/部署功能也未开始。
- 未使用真实 GitHub OAuth App，也未执行公网 GitHub 登录联调；认证仍使用本地 provider stub 与浏览器 route mock。
- 未触发 GitHub 托管 CI。本次未提交、未推送，因此没有远端 workflow 运行。
- 未执行公网域名、TLS、部署、备份恢复、生产监控或自动回滚。

## 仓库与只读参考项目

P2 工作位于 `codex/p2-content-portfolio`，Task 1-15 改动保留在工作区，未暂存、未提交、未推送。用户已有的 `specs/features/P3-ADMIN-001/` 未被本阶段修改或清理。

只读参考项目 `C:\softWare\project\latest\skillhub` 状态保持为：

```text
## codex/fix-rerelease-skill-identity
 M server/skillhub-app/src/main/java/com/skillhub/domain/skill/service/SkillPublishService.java
 M server/skillhub-app/src/test/java/com/skillhub/domain/skill/service/SkillPublishServiceTest.java
?? specs/features/P5-BE-RERELEASE-IDENTITY-001/
```

本阶段只读取该状态，未在参考项目中修改、创建、删除、格式化、构建或生成文件。

## 下一阶段前置条件

新会话首先读取 `specs/features/P2-CONTENT-PORTFOLIO-001/SESSION_HANDOFF_AFTER_TASK15.md`，不得再以 Task 5 交接作为当前检查点。

1. 用户审阅 P2 的 API、页面、安全结论与本文件，并明确确认 P2 是否验收通过。
2. 只有用户明确要求后，才可暂存、提交、推送或创建 PR。
3. P3 必须单独读取并批准其规格与实施计划，不能由 `awaiting-user-acceptance` 自动授权。
4. 在上述条件完成前，P3 保持未授权。
