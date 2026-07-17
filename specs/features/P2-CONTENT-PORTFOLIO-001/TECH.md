---
task_id: P2-CONTENT-PORTFOLIO-001
title: StudyStack 公开内容与作品集技术规格
phase: P2
status: awaiting-user-acceptance
created: 2026-07-17
updated: 2026-07-17
product_ref: specs/features/P2-CONTENT-PORTFOLIO-001/PRODUCT.md
implementation_ref: specs/features/P2-CONTENT-PORTFOLIO-001/IMPLEMENTATION_PLAN.md
design_refs:
  - C:/softWare/project/workDoc/java-personal-website-design.md
  - docs/AI-PROJECT-DELIVERY-PLAN.md
  - specs/features/P1-IDENTITY-001/TECH.md
---

# P2 Content And Portfolio Technical Specification

## 1. Technical Goals And Constraints

- 在 P1 Spring Boot 3.5.16、Spring Modulith、PostgreSQL、Vue 3 和同源 Session 基线上增加公开内容与作品集读取能力。
- `content` 和 `portfolio` 保持独立业务模块，只通过 `shared` 中稳定的小型接口复用 slug、Markdown、SEO 和错误能力。
- 数据库 schema 继续只由 Flyway 管理，JPA 使用 `ddl-auto=validate`；P2 不插入演示数据。
- P2 Controller 只允许匿名 GET，不得提前实现 P3 管理写 API。
- API DTO、OpenAPI、生成 TypeScript 与 strict Zod runtime mapping 必须同步，实体和原始 Markdown不得跨越公开边界。

## 2. Ownership And Boundaries

| 层 | 责任 | 禁止事项 |
|---|---|---|
| `shared.slug` | slug 值、格式归一化、创建校验和发布后不可变规则 | 不自动转写中文或空格，不包含资源查询 |
| `shared.markdown` | CommonMark 解析、HTML 白名单、链接策略和安全输出 | 不保存业务实体，不处理媒体上传 |
| `shared.seo` | 公开 origin、贡献接口、sitemap 和 robots 聚合 | 不直接依赖 content/portfolio 实体或 Repository |
| `shared.web` | 公共分页参数错误和 400/404 ProblemDetail | 不泄露异常、SQL 或隐藏资源状态 |
| `content.domain` | 文章、分类、标签、发布状态和 Repository 端口 | 不依赖 Web DTO，不提供管理 Controller |
| `content.application` | 公开文章查询、筛选、详情和 taxonomy 统计 | 不返回 Entity、原始 Markdown 或任意排序 |
| `portfolio.domain` | 简介、项目、技能、经历和 Repository 端口 | 不依赖 content 内部类型 |
| `portfolio.application` | 公开作品集查询、分页、可见性和安全映射 | 不暴露 status、visible、sortOrder 或 version |
| `content.web` / `portfolio.web` | 公开 GET Controller、响应 DTO 和 OpenAPI 注解 | 不复制 slug、Markdown 或发布规则 |
| `web/src/features/*` | strict schema、显式 mapper、client 和 Query options | 不通过 `as` 接受未知 API 值，不保存到 Pinia |
| `web/src/shared/content` | 危险 HTML 检查和唯一 `v-html` 组件 | 不实现第二套 Markdown parser |

Spring Modulith 和 `P2ScopeContractTest` 必须持续验证七模块边界，并拒绝 P2 出现后续阶段包、写 Controller、上传类型或评论类型。

## 3. Data Model And Migrations

Flyway V3/V4 使用 PostgreSQL 显式约束和索引，不依赖 Hibernate 自动建表。

### 3.1 V3 Content

`V3__content_schema.sql` 创建：

- `content_category`：UUID 主键、name、唯一 slug、审计时间和非负 version。
- `content_tag`：UUID 主键、name、唯一 slug、审计时间和非负 version。
- `content_article`：UUID、唯一 slug、title、summary、`body_markdown`、status、可空 category、SEO 字段、publishedAt、审计时间和 version。
- `content_article_tag`：文章与标签复合主键关系；文章或标签删除时关系级联删除。

文章状态 CHECK 只允许 `DRAFT`、`PUBLISHED`、`ARCHIVED`；`PUBLISHED` 必须具有 publishedAt。分类删除使用 `ON DELETE SET NULL`，公开排序和分类/标签筛选具有专用索引。

### 3.2 V4 Portfolio

`V4__portfolio_schema.sql` 创建：

- `portfolio_profile`：固定主键 `id=1` 的单例简介、Markdown bio、SEO 描述和 version。
- `portfolio_project`：UUID、唯一 slug、摘要、Markdown 描述、可空 HTTPS URL、状态、featured、非负 sortOrder、publishedAt 和 version。
- `portfolio_skill`：名称、分类、可空摘要、非负 sortOrder、visible 和 version。
- `portfolio_experience`：组织、角色、日期范围、Markdown 摘要、非负 sortOrder、visible 和 version。

项目发布约束与文章一致；项目 URL 只允许包含 host 的 HTTPS URL。经历 endDate 可空，但不得早于 startDate。技能和经历按 visible、sortOrder 和稳定次级字段建立索引。

## 4. Slug Policy

- `SlugPolicy` 是后端唯一业务归一化入口；格式为 3 至 120 位小写 ASCII segment，以单个短横线分隔。
- 创建时执行 trim、小写和格式校验，不进行 transliteration 或字符替换。
- `Slug` 是不可变值，业务实体通过共享策略执行首次发布后的不可变检查。
- V3/V4 CHECK 保留同等数据库防线；前端 `web/src/shared/api/slug-schema.ts` 是唯一 runtime slug 正则来源。
- category、tag、article 和 project 不得复制 Java 或 TypeScript 正则与映射逻辑。

## 5. Markdown Security Pipeline

```text
Raw Markdown in PostgreSQL
  -> CommonMark parser (raw HTML disabled)
  -> GFM tables and strikethrough
  -> OWASP Java HTML Sanitizer policy
  -> RenderedMarkdown
  -> public DTO contentHtml/bioHtml/descriptionHtml/summaryHtml
  -> strict Zod safeHtmlSchema
  -> SafeMarkdownView (only v-html owner)
```

- CommonMark 及 GFM 扩展固定为 0.28.0，OWASP Java HTML Sanitizer 固定为 20260313.1。
- 允许结构标签为段落、标题、列表、引用、代码、强调、链接、分隔线、换行、表格和删除线。
- 原始 HTML、脚本、样式、iframe、object、embed、form、input、图片、事件属性、`javascript:` 和 `data:` URL 被移除。
- 站内相对链接允许保留；外链只允许 HTTPS 并增加 `nofollow noopener noreferrer`。
- Parser、renderer 和 PolicyFactory 配置一次后复用，不能按请求重建。

## 6. Content Query And API

`PublicArticleQuery` 使用只读事务，依赖 Repository 的公开查询，不从 Controller 接收任意 Sort。

- 列表：`GET /api/v1/articles?page=0&size=10&category=<slug>&tag=<slug>`。
- 详情：`GET /api/v1/articles/{slug}`。
- 分类：`GET /api/v1/categories`。
- 标签：`GET /api/v1/tags`。
- 可见性：`status=PUBLISHED` 且 `publishedAt <= now`。
- 排序：`publishedAt DESC, id DESC`。
- 分类/标签计数只统计当前公开文章，并使用明确查询避免 N+1。

application 层映射 `ArticleSummary`、`ArticleDetail` 和 `TaxonomySummary`；web 层再映射响应 DTO。详情调用唯一 MarkdownRenderer，canonical path 固定为 `/blog/{slug}`。

## 7. Portfolio Query And API

`PublicPortfolioQuery` 使用只读事务，集中处理项目发布可见性、分页和简介缺失语义。

- 简介：`GET /api/v1/portfolio/profile`。
- 项目列表：`GET /api/v1/portfolio/projects?page=0&size=10&featured=<boolean>`。
- 项目详情：`GET /api/v1/portfolio/projects/{slug}`。
- 技能：`GET /api/v1/portfolio/skills`。
- 经历：`GET /api/v1/portfolio/experiences`。

项目按 `publishedAt DESC, id DESC`，技能按 `sortOrder ASC, id ASC`，经历按 `sortOrder ASC, startDate DESC, id ASC`。简介、项目和经历的 Markdown 通过共享 renderer；项目 canonical path 固定为 `/projects/{slug}`。

## 8. Public Error And OpenAPI Contract

- `SecurityConfiguration` 显式 permit P2 公开 GET，同时保持 P1 auth/admin 规则顺序不回归。
- page 必须大于等于 0，size 必须在 1 至 50；类型错误和非法参数统一映射为 400 `invalid_request`。
- `ArticleNotFoundException` 和 `PortfolioNotFoundException` 映射为清洗后的 404，不区分不存在、草稿、归档和未来发布。
- ProblemDetail 固定包含 RFC 结构字段、请求 instance 和稳定 code，不携带 exception message 或业务实体。
- OpenAPI 使用 `io.swagger.v3.oas.annotations`；生产 profile 继续关闭 `/v3/api-docs` 和 Swagger UI。
- `web/src/shared/api/generated/openapi.d.ts` 只能由 `pnpm contract:generate` 生成，并由 `pnpm contract:check` 验证。

## 9. SEO And Public Origin

- `PublicSiteProperties` 读取 `STUDYSTACK_PUBLIC_BASE_URL`，要求绝对 HTTP(S) origin 且不含 path、query、fragment 或 user-info；prod 额外要求 HTTPS。
- `SitemapContributor` 是模块边界接口；content 和 portfolio 只贡献当前公开 URL，聚合器不读取两个模块的实体。
- sitemap 包含 `/`、`/about`、`/blog`、`/projects` 与当前公开详情 URL，使用绝对 origin 和稳定排序。
- robots 指向绝对 sitemap，并禁止 admin、API 与 OAuth 敏感路径。
- sitemap URL 数量有固定上限，超限明确失败并为未来 sitemap index 留出边界。

## 10. Frontend Runtime And Query Boundary

- content、portfolio、page、slug 和 safe HTML 均使用 strict Zod schema；未知字段被拒绝。
- schema 后使用显式 mapper 构造业务对象，API 返回值不得直接强转为 union。
- `public-api-client.ts` 只发送同源相对 GET，集中解析 JSON 与 ProblemDetail。
- TanStack Query key 包含 article page/filter、project page/featured、详情 slug 和独立 taxonomy/profile/skill/experience key。
- 公开响应不进入 Pinia；空数据是成功状态，网络、HTTP 和 runtime schema 错误进入统一错误状态。
- `SafeMarkdownView.vue` 是唯一允许 `v-html` 的组件，输入必须先通过 `safeHtmlSchema` 危险片段检查。

## 11. Routing, Views And Client SEO

| 路由 | 页面责任 |
|---|---|
| `/` | 站点身份、简介摘要、最近文章和精选项目 |
| `/about` | 简介、技能和经历 |
| `/blog` | 文章分页、分类和标签筛选 |
| `/blog/:slug` | 文章详情、安全正文和 SEO |
| `/projects` | 项目分页列表 |
| `/projects/:slug` | 项目详情、安全描述和外部链接 |

- 页面复用稳定的 loading、empty、error、404 和 pagination UI，不嵌套装饰性卡片。
- `usePageSeo` 维护 title、description、canonical 和 Open Graph，并在 scope dispose 时清理动态节点。
- 路由详情使用 API 提供的站内 canonicalPath，不接受服务端返回任意外部 canonical URL。
- 响应式布局必须在移动端和桌面端无重叠、无横向溢出，长标题和 URL 可换行。

## 12. Environment And Delivery

- `STUDYSTACK_PUBLIC_BASE_URL` 只注入后端 app；web 构建参数不得包含 GitHub secret、管理员 ID 或数据库凭据。
- Caddy 先拒绝 `/actuator`，再代理 `/api`、OAuth、sitemap 和 robots，最后执行 SPA fallback。
- Compose topology 保持 PostgreSQL、app 和 Caddy 三个服务，不增加后续阶段基础设施。
- dev/test 可使用 HTTP origin；prod 必须使用无额外组件的 HTTPS origin。
- P2 不执行真实 GitHub OAuth、公网域名、TLS、备份恢复、监控或生产发布。

## 13. Security And Privacy Invariants

- 匿名 API 不返回 Entity、status、version、visible、sortOrder、原始 Markdown、数据库外键或未发布资源信息。
- 所有公开 HTML 同时经过服务端 sanitizer 和前端 runtime 检查，只有一个 `v-html` 所有者。
- 公开链接和 canonical origin 不允许 `javascript:`、`data:`、协议相对 URL 或任意外部 host 注入。
- 400/404 不记录或返回原始恶意输入、SQL、堆栈、Session、token、secret 或 OAuth provider 数据。
- P2 不增加写路由，不降低 P1 的 ADMIN、CSRF、Session、Cookie 或生产配置策略。

## 14. Behavior-To-Test Traceability

| 行为 | 主要后端证据 | 主要前端与交付证据 |
|---|---|---|
| C1 | `SlugPolicyTest`、content/portfolio schema 与 Repository tests | `slug-schema.spec.ts` |
| C2 | `SafeMarkdownRendererTest` | `safe-html.spec.ts`、`SafeMarkdownView`、Playwright XSS |
| C3 | `ContentRepositoryIntegrationTest`、`PublicArticleQueryIntegrationTest` | content client/view tests |
| C4 | content query/API integration tests | content schema/client/view tests |
| C5 | `PublicContentApiIntegrationTest`、OpenAPI test | article detail view、SEO tests |
| C6 | content query/API integration tests | blog filter view tests |
| C7 | portfolio query/API integration tests | home/about empty and content tests |
| C8 | portfolio schema/Repository/query/API tests | portfolio schema/client/view tests |
| C9 | portfolio query/API tests | about view tests |
| C10 | public API and OpenAPI integration tests | contract sync and client tests |
| C11 | OpenAPI generation contract | strict Zod schema/client/query tests |
| C12 | API integration tests | router/view Vitest and Playwright |
| C13 | canonical fields in query/API tests | `use-page-seo.spec.ts` and Playwright |
| C14 | `SeoEndpointIntegrationTest` | Compose/Caddy tests and Playwright |
| C15 | `StudyStackModulesTest`、`P2ScopeContractTest`、`mvnw verify` | lint、typecheck、Vitest、build、Compose、Playwright |

## 15. Risks And Compatibility

- 发布可见性依赖应用时钟；所有领域时间使用 UTC `Instant`，测试通过固定 Clock 避免时区漂移。
- sitemap P2 使用单文件固定上限；数据增长到上限前必须在后续阶段引入 sitemap index，不能静默截断。
- 文章与项目首次发布后 slug 锁定，以避免旧链接失效；后续管理 API 必须复用该领域规则。
- 数据库 CHECK 与前端 schema 是边界防线，业务归一化仍必须集中在 shared helper，避免规则漂移。
- generated OpenAPI 类型是派生产物；任何接口字段变更必须同时更新 DTO、OpenAPI、生成类型、Zod mapping、测试和规格。

## 16. Completion Gate

P2 只有在以下条件同时满足时可交付：

- V3/V4 在真实 PostgreSQL Testcontainers 中通过，Hibernate validate 和 Flyway 顺序无回归。
- C1-C15 的后端、前端、契约、SEO、代理、空数据库和范围证据全部通过。
- 后端完整 verify、前端 lint/typecheck/test/contract/build、Compose 配置与拓扑、Playwright 均为状态 0。
- 安全扫描确认无写 Controller、无后续阶段依赖、无未受控 `v-html`、无旧 Swagger 注解和无真实 secret。
- `specs/PROGRESS.md` 与交接文档记录真实验证结果，状态保持 `P2 / awaiting-user-acceptance`，直到用户明确验收。
- 发布时只提交 P2 相关文件，不包含本地 `AGENTS.md`、P3 规格或其他用户改动。
