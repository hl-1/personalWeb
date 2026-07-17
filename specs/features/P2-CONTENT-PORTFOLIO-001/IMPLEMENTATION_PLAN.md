---
task_id: P2-CONTENT-PORTFOLIO-001
title: StudyStack 公开内容与作品集
phase: P2
status: awaiting-user-acceptance
created: 2026-07-16
updated: 2026-07-17
design_refs:
  - C:/softWare/project/workDoc/java-personal-website-design.md
  - docs/AI-PROJECT-DELIVERY-PLAN.md
  - specs/features/P1-IDENTITY-001/IMPLEMENTATION_PLAN.md
---

# StudyStack P2 Content And Portfolio Implementation Plan

> **For agentic workers:** 使用 `superpowers:executing-plans` 按 Task 执行。用户每次只授权一个 Task；当前 Task验证完成后必须停止，不得自动进入下一 Task。

**Goal:** 建立文章、分类、标签、个人简介、项目、技能和经历的稳定领域模型与公开读取体验，并提供安全 Markdown、SEO、站点地图和响应式 Vue页面。

**Architecture:** `content` 拥有文章与分类标签，`portfolio` 拥有个人简介、项目、技能和经历；两者只依赖 `shared` 中稳定的 slug、Markdown和 SEO贡献接口。P2只开放匿名 GET，不提供管理写接口；P3直接复用本阶段领域模型和数据库约束实现后台编辑。

**Tech Stack:** Java 21、Spring Boot 3.5.16、Spring Modulith、Spring Data JPA、PostgreSQL、Flyway、CommonMark 0.28.0、OWASP Java HTML Sanitizer 20260313.1、OpenAPI 3、Vue 3、TypeScript、TanStack Query、Zod、Vitest、Playwright。

---

## 0. 使用方式与执行门禁

执行某个任务时向 AI发送：

```text
请读取 C:\softWare\project\studystack\specs\features\P2-CONTENT-PORTFOLIO-001\IMPLEMENTATION_PLAN.md。
本次只执行 Task N。严格完成该 Task的 RED、GREEN、REFACTOR和验证，完成后停止，不提交、不推送。
```

- Codex自动加载全局 `AGENTS.md`，任务提示不重复其中的通用规则。
- P1完成聚合验证并经用户验收后才能执行 Task 1；P1未提交改动必须先由用户决定如何处理，P2不得混入或回滚。
- 每次只执行一个 Task，只修改该 Task的 Files列表。
- RED预期失败不是阻塞；意外通过时先解释已有行为。
- 未经用户单独授权，不执行 commit、push、PR、部署或真实生产数据写入。
- `C:\softWare\project\latest\skillhub` 始终只读，操作前后记录状态但不得清理其中已有用户改动。
- 不创建 P3管理写接口、P4评论、P5媒体上传、P6运维脚本或 P7部署逻辑。
- P2不插入演示数据。数据库为空时，公开页面必须显示稳定空状态。

## 1. 固定产品与接口契约

### 1.1 Slug与发布状态

- 文章、分类、标签和项目 slug统一为 3至120字符的小写 ASCII：`[a-z0-9]+(?:-[a-z0-9]+)*`。
- 输入只做 trim和小写归一化；不自动把空格、中文或特殊字符转成 slug，非法值明确拒绝。
- slug在各自资源类型内唯一；文章和项目首次发布后 slug不可变，修改标题不改变 URL。
- 文章与项目状态只有 `DRAFT`、`PUBLISHED`、`ARCHIVED`。
- 匿名请求只看见 `PUBLISHED` 且 `publishedAt <= now` 的资源；草稿、归档、未来发布时间和不存在资源统一返回404。
- 时间使用 UTC `Instant` 和 ISO-8601；列表固定排序 `publishedAt DESC, id DESC`。

### 1.2 Markdown安全

- 数据库存原始 Markdown，公开 API只返回白名单清洗后的 `contentHtml`，不返回原始 Markdown。
- 使用 CommonMark 0.28.0；启用 GFM tables和 strikethrough，不允许原始 HTML透传。
- HTML使用 OWASP Java HTML Sanitizer 20260313.1二次清洗。
- 允许标签：`p`、`h1`至`h6`、`ul`、`ol`、`li`、`blockquote`、`pre`、`code`、`em`、`strong`、`a`、`hr`、`br`、`table`、`thead`、`tbody`、`tr`、`th`、`td`、`del`。
- 链接只允许 HTTPS和站内相对路径，外链添加 `nofollow noopener noreferrer`。
- 禁止 `script`、`style`、`iframe`、`object`、`embed`、`form`、`input`、图片、内联样式、事件属性、`javascript:`和 `data:` URL。图片进入 P5。
- Vue只允许统一 `SafeMarkdownView` 组件使用 `v-html`，其输入先经过 Zod危险片段检查。

### 1.3 公开 API

- `GET /api/v1/articles?page=0&size=10&category=<slug>&tag=<slug>`
- `GET /api/v1/articles/{slug}`
- `GET /api/v1/categories`
- `GET /api/v1/tags`
- `GET /api/v1/portfolio/profile`
- `GET /api/v1/portfolio/projects?page=0&size=10`
- `GET /api/v1/portfolio/projects/{slug}`
- `GET /api/v1/portfolio/skills`
- `GET /api/v1/portfolio/experiences`
- `GET /sitemap.xml`
- `GET /robots.txt`

分页从0开始；默认10，最小1，最大50。响应固定包含 `items`、`page`、`size`、`totalElements`、`totalPages`。不接受客户端排序字段。

### 1.4 前端路由

- `/`：个人简介摘要、最近文章和精选项目。
- `/about`：个人简介、技能和经历。
- `/blog`：文章分页及分类/标签筛选。
- `/blog/:slug`：文章详情。
- `/projects`：公开项目列表。
- `/projects/:slug`：项目详情。

所有页面必须覆盖加载、空、错误和404状态；桌面与移动端不重叠、不横向溢出。

### 1.5 公开响应字段

- `ArticleSummaryResponse`：`id`、`slug`、`title`、`summary`、可空 `category`、`tags`、`publishedAt`、`updatedAt`。
- `ArticleDetailResponse`：摘要全部字段，加 `contentHtml`、可空 `seoTitle`、可空 `seoDescription`、`canonicalPath`。
- `TaxonomyResponse`：`name`、`slug`、`publishedArticleCount`；数量只统计当前可公开文章。
- `PortfolioProfileResponse`：`displayName`、`headline`、`bioHtml`、`seoDescription`；不存在时404。
- `ProjectSummaryResponse`：`id`、`slug`、`title`、`summary`、`featured`、`publishedAt`、`updatedAt`。
- `ProjectDetailResponse`：摘要全部字段，加 `descriptionHtml`、可空 `projectUrl`、可空 `repositoryUrl`、`canonicalPath`。
- `SkillResponse`：`id`、`name`、`category`、可空 `summary`；不向前端暴露内部 sortOrder。
- `ExperienceResponse`：`id`、`organization`、`role`、`startDate`、可空 `endDate`、`summaryHtml`。
- 所有 JSON对象由 Zod strict schema校验；不得返回 Entity、version、status、原始 Markdown、内部 sortOrder或数据库外键。
- 400/404使用 `application/problem+json`，固定包含 `type`、`title`、`status`、`detail`、`instance`、`code`，不得包含堆栈或 unpublished资源信息。

## 2. 稳定行为编号

- C1：统一 slug校验、唯一性和发布后不可变。
- C2：Markdown渲染和 XSS白名单。
- C3：文章状态、发布时间和匿名可见性。
- C4：文章分页、分类与标签筛选。
- C5：文章详情、SEO字段和404。
- C6：分类标签公开列表只统计已发布文章。
- C7：个人简介公开读取与空状态。
- C8：项目状态、精选标记、分页、详情和404。
- C9：技能和经历稳定排序与可见性。
- C10：公开 API校验、ProblemDetail与 OpenAPI契约。
- C11：前端 runtime schema、API client与 Query缓存。
- C12：首页、关于、博客和项目页面体验。
- C13：标题、description、canonical和 Open Graph。
- C14：sitemap、robots和 Caddy代理。
- C15：契约同步、范围门禁与阶段聚合验收。

## 3. 文件责任图

| 区域 | 责任 | 禁止内容 |
|---|---|---|
| `shared/slug` | 两个模块共用的 slug值与规则 | 业务资源查询、自动中文转写 |
| `shared/markdown` | Markdown解析、HTML白名单与安全输出 | 保存文章、媒体上传 |
| `shared/seo` | 公共站点配置、贡献接口、sitemap和 robots聚合 | 直接依赖 content/portfolio实体 |
| `content` | 文章、分类、标签、公开查询与 DTO | 管理写 API、作品数据 |
| `portfolio` | 简介、项目、技能、经历与公开查询 | 管理写 API、文章数据 |
| `web/src/features/content` | 内容 schema、client和 Query | 未校验 API对象、编辑表单 |
| `web/src/features/portfolio` | 作品集 schema、client和 Query | 上传、管理状态 |

## Task 1: P2模块边界与范围门禁

**覆盖行为：** C15

**Files:**

- Modify: `server/src/test/java/com/studystack/foundation/StudyStackModulesTest.java`
- Create: `server/src/test/java/com/studystack/foundation/P2ScopeContractTest.java`

### RED

- [ ] 修改模块测试，使 `content`、`portfolio` 可进入实现阶段，仍要求 `admin`、`comment`、`media` 只有 `package-info.java`。
- [ ] 创建范围测试，拒绝 P2出现 POST/PUT/PATCH/DELETE Controller、上传类型、评论类型和后台 CRUD页面。
- [ ] 在业务实现尚为空时运行：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=StudyStackModulesTest,P2ScopeContractTest test'
```

Expected: 模块验证通过；范围测试因尚未建立 P2允许路径清单或实现责任图而失败。

### GREEN

- [ ] 将空模块集合精确收敛为 `admin`、`comment`、`media`；为 shared新增 `slug`、`markdown`、`seo`允许路径，但不要求文件提前存在。
- [ ] P2范围测试只允许 content、portfolio和批准的 shared基础类型，拒绝写路由及后续阶段包名。
- [ ] 重跑测试。

Expected: 七模块不变、依赖验证通过、P2范围明确。

### REFACTOR

- [ ] 让错误信息列出越界文件和 HTTP方法；不得用宽泛目录白名单掩盖未来文件。
- [ ] 重跑 P0/P1模块与范围相关测试。

## Task 2: 共享 Slug值与规则

**覆盖行为：** C1

**Files:**

- Create: `server/src/main/java/com/studystack/shared/slug/Slug.java`
- Create: `server/src/main/java/com/studystack/shared/slug/SlugPolicy.java`
- Create: `server/src/test/java/com/studystack/shared/slug/SlugPolicyTest.java`

### RED

- [ ] 参数化测试合法边界、trim、小写、连续短横线、前后短横线、空白、中文、路径字符、控制字符和121字符。
- [ ] 测试已发布资源请求改变 slug时得到固定冲突结果。
- [ ] 运行：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=SlugPolicyTest test'
```

Expected: 编译因 Slug类型和策略不存在失败。

### GREEN

- [ ] 实现不可变 `Slug` 和集中 `SlugPolicy`；只做 trim、小写和格式校验，不自动转写。
- [ ] 提供创建校验与 published slug不可变校验两个明确入口。
- [ ] 重跑测试。

Expected: 全部映射、拒绝和不可变路径通过。

### REFACTOR

- [ ] 确保 content和 portfolio后续只能复用该策略，不复制正则或归一化。
- [ ] 运行 slug与 Modulith测试。

## Task 3: 安全 Markdown渲染器

**覆盖行为：** C2

**Files:**

- Modify: `server/pom.xml`
- Create: `server/src/main/java/com/studystack/shared/markdown/RenderedMarkdown.java`
- Create: `server/src/main/java/com/studystack/shared/markdown/MarkdownRenderer.java`
- Create: `server/src/main/java/com/studystack/shared/markdown/SafeMarkdownRenderer.java`
- Create: `server/src/test/java/com/studystack/shared/markdown/SafeMarkdownRendererTest.java`

### RED

- [ ] 测试标题、列表、代码块、表格、删除线和链接；测试 script、raw HTML、事件属性、javascript/data URL、iframe、图片和畸形标签被移除。
- [ ] 测试相同输入产生确定输出，空输入和超大输入得到明确结果。
- [ ] 运行：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=SafeMarkdownRendererTest test'
```

Expected: 渲染器和依赖不存在，测试失败。

### GREEN

- [ ] 固定 `commonmark`、GFM tables、GFM strikethrough为0.28.0，OWASP sanitizer为20260313.1。
- [ ] CommonMark禁止原始 HTML并清理 URL；OWASP按固定标签/属性二次清洗，外链增加安全 rel。
- [ ] 返回不可变 `RenderedMarkdown`，只包含清洗后的 HTML。
- [ ] 重跑测试。

Expected: 支持语法正确，恶意载荷全部不可执行。

### REFACTOR

- [ ] Parser、renderer和 PolicyFactory配置一次并安全复用；不按请求重建策略。
- [ ] 执行依赖分析与定向测试。

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd dependency:analyze; .\mvnw.cmd --% -Dtest=SafeMarkdownRendererTest test'
```

## Task 4: 内容数据库迁移 V3

**覆盖行为：** C1、C3、C4、C6

**Files:**

- Create: `server/src/main/resources/db/migration/V3__content_schema.sql`
- Create: `server/src/test/java/com/studystack/content/infrastructure/ContentSchemaIntegrationTest.java`

### RED

- [ ] Testcontainers测试文章、分类、标签、文章标签表、外键、状态 CHECK、slug唯一键和查询索引。
- [ ] 覆盖重复 slug、未知状态、孤立关系和重复 article-tag被拒绝。
- [ ] 运行：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=ContentSchemaIntegrationTest test'
```

Expected: V3和表不存在。

### GREEN

- [ ] 创建 `content_article`、`content_category`、`content_tag`、`content_article_tag`。
- [ ] 文章包含 UUID、slug、title、summary、body_markdown、status、category_id、SEO title/description、published_at、created_at、updated_at和 version。
- [ ] 标题最大180、摘要500、正文200000、SEO title70、SEO description160；数据库 CHECK与应用规则一致。
- [ ] 为公开状态+发布时间排序、分类和标签筛选建立索引。
- [ ] 重跑测试。

Expected: V1-V3顺序通过且约束完整。

### REFACTOR

- [ ] 验证 migration checksum失败仍由 P0测试覆盖，Hibernate保持 `validate`。
- [ ] 运行内容 schema与 P0/P1迁移测试。

## Task 5: 内容领域模型与 Repository

**覆盖行为：** C1、C3、C4、C6

**Files:**

- Create: `server/src/main/java/com/studystack/content/domain/ArticleStatus.java`
- Create: `server/src/main/java/com/studystack/content/domain/Article.java`
- Create: `server/src/main/java/com/studystack/content/domain/Category.java`
- Create: `server/src/main/java/com/studystack/content/domain/Tag.java`
- Create: `server/src/main/java/com/studystack/content/domain/ArticleRepository.java`
- Create: `server/src/main/java/com/studystack/content/domain/CategoryRepository.java`
- Create: `server/src/main/java/com/studystack/content/domain/TagRepository.java`
- Create: `server/src/test/java/com/studystack/content/infrastructure/ContentRepositoryIntegrationTest.java`

### RED

- [ ] 测试 JPA映射、UTC时间、乐观锁、分类可空、标签去重、稳定排序和已发布 slug不可变。
- [ ] 测试公开 Repository永不返回草稿、归档和未来发布记录。
- [ ] 运行定向测试。

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=ContentRepositoryIntegrationTest test'
```

Expected: 领域和仓储类型缺失。

### GREEN

- [ ] 精确映射 V3并使用 shared Slug；定义 draft、publish、archive领域行为，首次 publish后锁定 slug。
- [ ] Repository提供固定公开查询，不开放任意 sort或直接 Entity分页到 web层。
- [ ] 重跑测试。

Expected: 状态、关系、排序和可见性通过。

### REFACTOR

- [ ] 避免 Entity public setter集合；分类标签变更维护双向关系一致性并拒绝重复。
- [ ] 运行 Task 4、5和 Modulith测试。

## Task 6: 文章公开查询服务

**覆盖行为：** C3、C4、C5、C6

**Files:**

- Create: `server/src/main/java/com/studystack/content/application/ArticleSummary.java`
- Create: `server/src/main/java/com/studystack/content/application/ArticleDetail.java`
- Create: `server/src/main/java/com/studystack/content/application/TaxonomySummary.java`
- Create: `server/src/main/java/com/studystack/content/application/PublicArticleQuery.java`
- Create: `server/src/main/java/com/studystack/content/application/ArticleNotFoundException.java`
- Create: `server/src/test/java/com/studystack/content/application/PublicArticleQueryIntegrationTest.java`

### RED

- [ ] 测试分页0/10、最大50、负数和超限；分类标签组合筛选；同时间按 UUID稳定排序。
- [ ] 测试详情只返回已发布到期文章，Markdown输出清洗 HTML；草稿、归档、未来、非法和未知 slug均404。
- [ ] 测试分类标签只列出至少一个已发布文章的项并返回发布文章数量。
- [ ] 运行：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=PublicArticleQueryIntegrationTest test'
```

Expected: 查询服务不存在。

### GREEN

- [ ] 实现只读事务查询，固定排序和上限；在 application层映射摘要/详情，不泄露 Markdown原文或状态字段。
- [ ] 详情调用唯一 MarkdownRenderer；canonical path固定 `/blog/{slug}`。
- [ ] 重跑测试。

Expected: 正常、空、筛选、边界和404全部通过。

### REFACTOR

- [ ] 防止 N+1：使用明确 fetch/query projection，一页查询数有测试上限。
- [ ] 重跑查询与 Repository测试。

## Task 7: 文章、分类与标签公开 API

**覆盖行为：** C4、C5、C6、C10

**Files:**

- Modify: `server/src/main/java/com/studystack/identity/infrastructure/security/SecurityConfiguration.java`
- Modify: `server/src/main/java/com/studystack/shared/openapi/OpenApiConfiguration.java`
- Create: `server/src/main/java/com/studystack/shared/web/PublicApiExceptionHandler.java`
- Create: `server/src/main/java/com/studystack/content/web/ArticleController.java`
- Create: `server/src/main/java/com/studystack/content/web/TaxonomyController.java`
- Create: `server/src/main/java/com/studystack/content/web/ArticlePageResponse.java`
- Create: `server/src/main/java/com/studystack/content/web/ArticleSummaryResponse.java`
- Create: `server/src/main/java/com/studystack/content/web/ArticleDetailResponse.java`
- Create: `server/src/main/java/com/studystack/content/web/TaxonomyResponse.java`
- Create: `server/src/test/java/com/studystack/content/web/PublicContentApiIntegrationTest.java`
- Create: `server/src/test/java/com/studystack/content/web/PublicContentOpenApiTest.java`

### RED

- [ ] MockMvc测试所有 content GET、分页参数400、未知404、匿名200、JSON字段白名单和 `application/problem+json`。
- [ ] OpenAPI测试路径、参数范围、分页 schema、400/404响应和新版注解；公开 GET不得声明 Session必需。
- [ ] 运行定向测试。

Expected: Controller和契约不存在。

### GREEN

- [ ] 实现只读 Controller、DTO和 ProblemDetail handler；错误包含稳定 `code`，不暴露 SQL、Entity或 unpublished存在性。
- [ ] Security显式 permit公开 GET并保持 admin/auth规则顺序。
- [ ] OpenAPI版本更新为 P2，继续禁止旧 Swagger注解。
- [ ] 重跑测试。

Expected: API与 OpenAPI契约通过，P1认证接口不回归。

### REFACTOR

- [ ] 集中 page mapping和错误 mapping；Controller不含 Markdown、slug或可见性业务规则。
- [ ] 运行 content API、auth API、OpenAPI生产关闭测试。

## Task 8: 作品集数据库迁移 V4

**覆盖行为：** C7、C8、C9

**Files:**

- Create: `server/src/main/resources/db/migration/V4__portfolio_schema.sql`
- Create: `server/src/test/java/com/studystack/portfolio/infrastructure/PortfolioSchemaIntegrationTest.java`

### RED

- [ ] 测试 profile单例、project、skill、experience表，状态/日期/URL/排序约束和索引。
- [ ] 拒绝第二个 profile、重复 project slug、非法状态、结束日期早于开始日期和负 sort_order。
- [ ] 运行：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=PortfolioSchemaIntegrationTest test'
```

Expected: V4不存在。

### GREEN

- [ ] profile使用固定主键1，包含 display_name、headline、bio_markdown和 SEO description。
- [ ] project包含 UUID、slug、title、summary、description_markdown、HTTPS project/repository URL、status、featured、sort_order、published_at、时间和 version。
- [ ] skill包含 UUID、name、category、summary、sort_order、visible；experience包含组织、role、start/end date、summary_markdown、sort_order、visible。
- [ ] 项目标题180、摘要500、描述100000；profile bio50000；经历摘要20000。
- [ ] 重跑测试。

Expected: V1-V4和所有约束通过。

### REFACTOR

- [ ] 为公开项目状态排序、visible技能/经历排序建立精确索引；不创建媒体外键。
- [ ] 运行全部迁移定向测试。

## Task 9: 作品集领域模型与 Repository

**覆盖行为：** C1、C7、C8、C9

**Files:**

- Create: `server/src/main/java/com/studystack/portfolio/domain/PortfolioProfile.java`
- Create: `server/src/main/java/com/studystack/portfolio/domain/Project.java`
- Create: `server/src/main/java/com/studystack/portfolio/domain/ProjectStatus.java`
- Create: `server/src/main/java/com/studystack/portfolio/domain/Skill.java`
- Create: `server/src/main/java/com/studystack/portfolio/domain/Experience.java`
- Create: `server/src/main/java/com/studystack/portfolio/domain/PortfolioProfileRepository.java`
- Create: `server/src/main/java/com/studystack/portfolio/domain/ProjectRepository.java`
- Create: `server/src/main/java/com/studystack/portfolio/domain/SkillRepository.java`
- Create: `server/src/main/java/com/studystack/portfolio/domain/ExperienceRepository.java`
- Create: `server/src/test/java/com/studystack/portfolio/infrastructure/PortfolioRepositoryIntegrationTest.java`

### RED

- [ ] 测试 profile单例、项目发布/归档/slug锁定、HTTPS URL、精选与排序、技能经历 visible和日期规则。
- [ ] 公开项目查询排除草稿、归档和未来发布。
- [ ] 运行定向测试。

Expected: 领域和 Repository不存在。

### GREEN

- [ ] 精确映射 V4，复用 shared Slug；URL统一在领域层解析和校验，不在多个 Entity复制规则。
- [ ] Repository仅提供 P2公开查询和 P3未来需要的按 ID查找基础，不提供任意 SQL暴露。
- [ ] 重跑测试。

Expected: 单例、状态、URL、排序和可见性通过。

### REFACTOR

- [ ] 集中 HTTPS URL规范化并保持各 Repository职责单一；外部模块不得访问内部 Entity或 Repository。
- [ ] 运行 portfolio schema、Repository和 Modulith测试。

## Task 10: 作品集公开查询与 API

**覆盖行为：** C7、C8、C9、C10

**Files:**

- Create: `server/src/main/java/com/studystack/portfolio/application/PublicPortfolioQuery.java`
- Create: `server/src/main/java/com/studystack/portfolio/application/PortfolioProfileView.java`
- Create: `server/src/main/java/com/studystack/portfolio/application/ProjectSummary.java`
- Create: `server/src/main/java/com/studystack/portfolio/application/ProjectDetail.java`
- Create: `server/src/main/java/com/studystack/portfolio/application/SkillView.java`
- Create: `server/src/main/java/com/studystack/portfolio/application/ExperienceView.java`
- Create: `server/src/main/java/com/studystack/portfolio/application/PortfolioNotFoundException.java`
- Create: `server/src/main/java/com/studystack/portfolio/web/PortfolioController.java`
- Create: `server/src/main/java/com/studystack/portfolio/web/PortfolioProfileResponse.java`
- Create: `server/src/main/java/com/studystack/portfolio/web/ProjectPageResponse.java`
- Create: `server/src/main/java/com/studystack/portfolio/web/ProjectSummaryResponse.java`
- Create: `server/src/main/java/com/studystack/portfolio/web/ProjectDetailResponse.java`
- Create: `server/src/main/java/com/studystack/portfolio/web/SkillResponse.java`
- Create: `server/src/main/java/com/studystack/portfolio/web/ExperienceResponse.java`
- Create: `server/src/test/java/com/studystack/portfolio/application/PublicPortfolioQueryIntegrationTest.java`
- Create: `server/src/test/java/com/studystack/portfolio/web/PublicPortfolioApiIntegrationTest.java`
- Create: `server/src/test/java/com/studystack/portfolio/web/PublicPortfolioOpenApiTest.java`

### RED

- [ ] 测试 profile存在/不存在、项目分页/详情/精选与404、技能经历 visible和稳定 sort_order。
- [ ] 验证 Markdown只返回安全 HTML，原文和隐藏状态不进入 API。
- [ ] OpenAPI覆盖所有 portfolio GET、分页、200/400/404和 DTO枚举。
- [ ] 运行：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=PublicPortfolioQueryIntegrationTest,PublicPortfolioApiIntegrationTest,PublicPortfolioOpenApiTest test'
```

Expected: 查询与 API不存在。

### GREEN

- [ ] 实现只读 query、DTO和 Controller；profile缺失返回404，技能经历为空返回200空数组。
- [ ] 项目 canonical path固定 `/projects/{slug}`，公开响应不含 version、status、原始 Markdown和内部排序字段。
- [ ] 重跑测试。

Expected: 正常、空、分页、可见性和契约通过。

### REFACTOR

- [ ] 消除 N+1和重复 Markdown策略；content与 portfolio共享 page/error基础但不共享业务 DTO。
- [ ] 运行 content、portfolio与 Modulith测试。

## Task 11: Sitemap、robots与模块贡献接口

**覆盖行为：** C13、C14

**Files:**

- Modify: `server/src/main/java/com/studystack/identity/infrastructure/security/SecurityConfiguration.java`
- Create: `server/src/main/java/com/studystack/shared/seo/PublicSiteProperties.java`
- Create: `server/src/main/java/com/studystack/shared/seo/SitemapEntry.java`
- Create: `server/src/main/java/com/studystack/shared/seo/SitemapContributor.java`
- Create: `server/src/main/java/com/studystack/shared/seo/SeoController.java`
- Create: `server/src/main/java/com/studystack/content/infrastructure/seo/ContentSitemapContributor.java`
- Create: `server/src/main/java/com/studystack/portfolio/infrastructure/seo/PortfolioSitemapContributor.java`
- Create: `server/src/test/java/com/studystack/shared/seo/SeoEndpointIntegrationTest.java`

### RED

- [ ] 测试 `STUDYSTACK_PUBLIC_BASE_URL`规范化：prod必须 HTTPS且无 path/query/fragment，dev/test可用 localhost HTTP。
- [ ] sitemap只包含公开静态页、已发布文章/项目，排除草稿、未来、admin、auth和 API；URL转义、去重和排序确定。
- [ ] robots声明 Sitemap并禁止抓取 `/admin`、`/api`、`/oauth2`、`/login/oauth2`。
- [ ] 运行：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=SeoEndpointIntegrationTest test'
```

Expected: SEO接口不存在。

### GREEN

- [ ] shared只依赖 `SitemapContributor`接口，通过注入 contributor列表汇总；不得直接 import content/portfolio类型。
- [ ] 两个业务模块各实现 contributor，查询自身公开 URL和更新时间。
- [ ] Security匿名开放 sitemap/robots，响应类型分别为 XML和 text/plain UTF-8。
- [ ] 重跑测试。

Expected: 模块倒置、确定输出和安全路径通过。

### REFACTOR

- [ ] 限制 sitemap单次最大 URL数量并为未来拆分索引预留明确失败；P2数据规模下不分页拆文件。
- [ ] 运行 SEO、Modulith和 security测试。

## Task 12: 前端 runtime schema、API client与 Query

**覆盖行为：** C4、C5、C7、C8、C9、C11

**Files:**

- Create: `web/src/shared/content/safe-html.ts`
- Create: `web/src/shared/content/safe-html.spec.ts`
- Create: `web/src/features/content/content-schema.ts`
- Create: `web/src/features/content/content-schema.spec.ts`
- Create: `web/src/features/content/content-client.ts`
- Create: `web/src/features/content/content-query.ts`
- Create: `web/src/features/content/content-client.spec.ts`
- Create: `web/src/features/portfolio/portfolio-schema.ts`
- Create: `web/src/features/portfolio/portfolio-schema.spec.ts`
- Create: `web/src/features/portfolio/portfolio-client.ts`
- Create: `web/src/features/portfolio/portfolio-query.ts`
- Create: `web/src/features/portfolio/portfolio-client.spec.ts`

### RED

- [ ] Zod测试所有 DTO、状态、分页边界、ISO时间、HTTPS URL、slug、危险 HTML和未知额外字段。
- [ ] client测试 query编码、可选筛选、404与 ProblemDetail；禁止绝对 API域名和客户端任意 sort。
- [ ] Query测试 key包含 page/category/tag，详情按 slug隔离；不在 Pinia复制服务端内容。
- [ ] 运行：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location web; pnpm test -- src/shared/content src/features/content src/features/portfolio'
```

Expected: 前端模块不存在。

### GREEN

- [ ] 实现 strict Zod schema和显式 map/filter，不使用 `as`信任响应。
- [ ] `safe-html`拒绝 script、事件属性、javascript/data URL和禁用标签；只有通过解析的字符串进入渲染组件。
- [ ] 创建同源 client和 TanStack Query options/composables。
- [ ] 重跑测试。

Expected: 正常、畸形、分页和错误响应路径通过。

### REFACTOR

- [ ] 共享通用 page schema工厂，但 content/portfolio保留独立 DTO映射；错误对象不保存完整未知响应。
- [ ] 运行 lint、typecheck和定向 Vitest。

## Task 13: 公开页面与客户端 SEO

**覆盖行为：** C5、C7、C8、C9、C12、C13

**Files:**

- Modify: `web/src/App.vue`
- Modify: `web/src/router/index.ts`
- Modify: `web/src/router/router.spec.ts`
- Create: `web/src/shared/content/SafeMarkdownView.vue`
- Create: `web/src/shared/seo/use-page-seo.ts`
- Create: `web/src/shared/seo/use-page-seo.spec.ts`
- Create: `web/src/views/AboutView.vue`
- Create: `web/src/views/BlogListView.vue`
- Create: `web/src/views/ArticleDetailView.vue`
- Create: `web/src/views/ProjectListView.vue`
- Create: `web/src/views/ProjectDetailView.vue`
- Modify: `web/src/views/HomeView.vue`
- Create: `web/src/features/content/content-views.spec.ts`
- Create: `web/src/features/portfolio/portfolio-views.spec.ts`

### RED

- [ ] 测试六条公开路由、加载/空/错误/404、分页按钮边界、分类标签筛选和直接切换详情。
- [ ] 测试 SafeMarkdownView是唯一 `v-html`位置，危险片段无法渲染。
- [ ] SEO测试 title、description、canonical、og:title、og:description、og:url更新并在路由切换后清理旧值。
- [ ] 运行定向前端测试。

Expected: 路由、页面和 SEO composable不存在。

### GREEN

- [ ] 实现安静、内容优先、响应式页面；首页显示简介、最近3篇文章和精选项目，数据为空时仍展示明确站点身份和空状态。
- [ ] 列表使用普通分区和表意链接，不嵌套卡片；移动端长标题、slug、URL和代码块不得撑破容器。
- [ ] 详情只通过 SafeMarkdownView渲染清洗 HTML。
- [ ] 重跑测试。

Expected: 页面行为、SEO和响应式结构测试通过。

### REFACTOR

- [ ] 抽取分页和状态视图的最小共享组件，避免 content/portfolio复制请求状态逻辑。
- [ ] 运行 lint、typecheck、全量 Vitest和 build。

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location web; pnpm lint; pnpm typecheck; pnpm test; pnpm build'
```

## Task 14: 环境、Caddy、契约同步与 E2E

**覆盖行为：** C10、C12、C13、C14、C15

**Files:**

- Modify: `.env.example`
- Modify: `server/src/main/resources/application.yml`
- Modify: `server/src/main/resources/application-dev.yml`
- Modify: `server/src/main/resources/application-prod.yml`
- Modify: `server/src/test/resources/application-test.yml`
- Modify: `deploy/compose.yml`
- Modify: `deploy/Caddyfile`
- Modify: `deploy/tests/compose-config.test.mjs`
- Modify: `web/src/shared/api/generated/openapi.d.ts`
- Create: `web/e2e/public-content.spec.ts`

### RED

- [ ] 配置测试要求 prod提供 HTTPS `STUDYSTACK_PUBLIC_BASE_URL`，示例配置无秘密。
- [ ] Caddy测试要求 `/sitemap.xml`、`/robots.txt`在 SPA fallback前代理到 app，Actuator拒绝和既有 auth/API代理顺序不变。
- [ ] OpenAPI生成后契约检查必须因新增接口未同步而失败。
- [ ] Playwright通过 route mock覆盖实际页面内容和错误状态，通过 Compose验证 sitemap/robots不是 SPA HTML；不插入演示数据。
- [ ] 运行定向检查。

Expected: 配置、Caddy、契约或 E2E至少一项失败。

### GREEN

- [ ] 添加 public base URL配置并注入 app，不进入 Vite秘密；修改 Caddy matcher。
- [ ] 生成 P2 OpenAPI TypeScript文件，不手工编辑生成内容。

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=OpenApiDevelopmentIntegrationTest test'
pwsh -NoLogo -NoProfile -Command 'Set-Location web; pnpm contract:generate; pnpm contract:check'
pwsh -NoLogo -NoProfile -Command 'node --test deploy/tests/compose-config.test.mjs'
```

- [ ] 启动 Compose执行 public-content和 foundation/auth E2E，随后清理容器与测试卷。

Expected: 配置、代理、契约和 E2E通过，P0/P1路由无回归。

### REFACTOR

- [ ] E2E失败输出包含 URL、HTTP状态、页面状态和 content-type，不打印响应全文或认证数据。
- [ ] 重跑 Task 14全部检查并确认参考项目状态未被本任务改变。

## Task 15: P2聚合验收与进度记录

**覆盖行为：** C1-C15

**Files:**

- Modify: `specs/PROGRESS.md`
- Modify: `docs/AI-CONTEXT-HANDOFF.md`

### RED

- [ ] 先运行聚合命令并记录真实失败，不先修改计划掩盖差异。

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd verify'
pwsh -NoLogo -NoProfile -Command 'Set-Location web; pnpm install --frozen-lockfile; pnpm lint; pnpm typecheck; pnpm test; pnpm contract:check; pnpm build'
pwsh -NoLogo -NoProfile -Command 'docker compose --env-file .env.example -f deploy/compose.yml config --quiet'
```

Expected: 已完成任务通过；失败映射回唯一 Task和 C编号。

### GREEN

- [ ] 只修复聚合验证暴露的 P2缺口，先重跑最小命令，再重跑聚合。
- [ ] Compose运行全部 Playwright并清理；确认数据库为空时首页、博客、项目和关于页均有稳定空状态。
- [ ] 更新进度：记录 C1-C15、Flyway V3/V4、测试数量、Markdown安全结论、SEO结果和未实现 P3写功能。
- [ ] 交接状态写 `P2 / awaiting-user-acceptance`，不得直接授权 P3。

Expected: 后端、前端、契约、Compose和 E2E均状态0，证据与输出一致。

### REFACTOR

- [ ] 扫描旧 Swagger注解、未清洗 `v-html`、重复 slug规则、危险 Markdown、写接口、真实秘密和后续阶段类型。
- [ ] 执行文档与 Git检查：

```powershell
pwsh -NoLogo -NoProfile -Command 'rg -n -e "io\.swagger\.annotations" -e "springfox" -e "@Api\b" -e "@ApiOperation\b" server web specs'
pwsh -NoLogo -NoProfile -Command 'rg -n -e "v-html" web/src'
pwsh -NoLogo -NoProfile -Command 'git diff --check; git status --short --branch'
```

Expected: 旧注解和越界实现零命中；`v-html`仅出现在 SafeMarkdownView；Git只显示阶段相关文件。不得暂存、提交或推送。

## 4. 行为到任务与测试映射

| 行为 | Task | 主要测试 |
|---|---|---|
| C1 Slug | 2、4、5、8、9 | `SlugPolicyTest`、两个 Repository测试 |
| C2 Markdown | 3、6、10、12、13 | `SafeMarkdownRendererTest`、`safe-html.spec.ts` |
| C3 文章可见性 | 4、5、6 | Content schema/repository/query测试 |
| C4 文章分页筛选 | 5、6、7、12 | `PublicArticleQueryIntegrationTest`、API/client测试 |
| C5 文章详情SEO | 6、7、13 | content API和 page SEO测试 |
| C6 分类标签 | 4、5、6、7 | query和 taxonomy API测试 |
| C7 简介 | 8、9、10、13 | portfolio query/API/view测试 |
| C8 项目 | 8、9、10、12、13 | portfolio repository/query/view测试 |
| C9 技能经历 | 8、9、10、13 | portfolio query/API/view测试 |
| C10 API契约 | 7、10、14 | OpenAPI与 ProblemDetail测试 |
| C11 前端数据层 | 12 | schema/client/query Vitest |
| C12 页面体验 | 13、14 | view Vitest与 Playwright |
| C13 Meta SEO | 6、10、11、13 | SEO composable与 endpoint测试 |
| C14 sitemap/robots | 11、14 | `SeoEndpointIntegrationTest`、Caddy/E2E |
| C15 聚合门禁 | 1、14、15 | scope、contract、全量验证 |

## 5. 最终完成定义

P2只有在以下条件同时满足时完成：

- 15个 Task均由用户逐项授权并完成验证。
- 草稿、归档、未来发布和隐藏作品永不出现在匿名 API、页面或 sitemap。
- slug唯一、稳定且发布后不可变。
- Markdown恶意载荷无法形成可执行 HTML，前端只有一个受控 `v-html`入口。
- 文章、分类、标签、简介、项目、技能和经历公开读取完整，分页和404稳定。
- OpenAPI、generated TypeScript、Zod mapping和 Controller一致。
- 首页、关于、博客和项目页面覆盖加载、空、错误和移动端。
- title、description、canonical、Open Graph、sitemap和 robots有自动化证据。
- P0/P1认证、Session、Caddy和 CI契约无回归。
- 进度状态为等待用户验收；未经用户验收不开始 P3。
