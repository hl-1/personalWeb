---
task_id: P2-CONTENT-PORTFOLIO-001
title: StudyStack 公开内容与作品集产品规格
phase: P2
status: awaiting-user-acceptance
created: 2026-07-17
updated: 2026-07-17
design_refs:
  - C:/softWare/project/workDoc/java-personal-website-design.md
  - docs/AI-PROJECT-DELIVERY-PLAN.md
  - specs/features/P1-IDENTITY-001/PRODUCT.md
---

# P2 Content And Portfolio Product Specification

## 1. Summary

P2 为 StudyStack 建立公开内容与作品集体验：访客可以浏览已发布文章、分类、标签、个人简介、项目、技能和经历，并获得稳定的分页、筛选、详情、SEO、站点地图以及移动端页面体验。

本阶段只提供匿名只读能力。内容编辑、后台管理、评论、媒体上传和生产部署属于后续阶段；数据库为空时，公开站点仍必须保持可访问并显示明确空状态。

## 2. Actors And Goals

| 角色 | 目标 |
|---|---|
| 访客 | 浏览当前可公开的文章、项目与个人资料，不接触草稿、归档或未来发布内容 |
| 内容读者 | 通过分类和标签筛选文章，读取安全渲染的正文并使用稳定 URL 分享内容 |
| 作品访问者 | 查看简介、技能、经历、精选项目与项目详情 |
| 搜索引擎 | 获取准确的 title、description、canonical、Open Graph、sitemap 和 robots 指令 |
| 内容维护者 | 在后续 P3 中复用稳定领域模型和数据库约束，不需要重写 P2 公开契约 |
| 运维人员 | 通过公开基址配置生成绝对站点 URL，不向浏览器暴露服务端秘密 |

## 3. Stable Behavior Contracts

### C1 Slug

- 文章、分类、标签和项目 slug 统一为 3 至 120 位小写 ASCII，格式为 `[a-z0-9]+(?:-[a-z0-9]+)*`。
- 输入只做 trim 和小写归一化；空格、中文、路径字符和其他特殊字符不会被自动转写。
- slug 在各自资源类型内唯一；文章和项目首次发布后 slug 不可变，标题变化不改变公开 URL。

### C2 Safe Markdown

- 数据库存储原始 Markdown；匿名 API 只返回白名单清洗后的 HTML，不返回原始 Markdown。
- 支持标题、列表、引用、代码、表格、删除线和安全链接；不允许原始 HTML 透传。
- `script`、事件属性、iframe、图片、表单、内联样式、`javascript:` 和 `data:` URL 不得形成可执行公开内容。
- 外部链接只允许 HTTPS，并包含 `nofollow noopener noreferrer`；站内相对路径允许保留。

### C3 Article Visibility

- 文章状态只有 `DRAFT`、`PUBLISHED`、`ARCHIVED`。
- 匿名请求只看见 `PUBLISHED` 且 `publishedAt <= now` 的文章。
- 草稿、归档、未来发布时间和不存在的文章在详情 API 中统一返回 404，不泄露资源是否存在。
- 公开文章固定按 `publishedAt DESC, id DESC` 排序。

### C4 Article Listing And Filters

- `GET /api/v1/articles` 使用 0 基分页，默认 size 为 10，允许范围为 1 至 50。
- 列表支持可选的 category slug 与 tag slug 组合筛选，不接受客户端排序字段。
- 响应固定包含 `items`、`page`、`size`、`totalElements` 和 `totalPages`。
- 非法分页或非法筛选值返回 400 `application/problem+json`。

### C5 Article Detail And SEO

- `GET /api/v1/articles/{slug}` 返回公开摘要字段、清洗后的 `contentHtml`、SEO 字段和 `canonicalPath`。
- 不符合公开 slug 格式的文章或项目详情路由显示稳定 404，前端不发送无效 API 请求。
- canonical path 固定为 `/blog/{slug}`。
- API 不返回文章状态、version、原始 Markdown、内部外键或数据库实体。

### C6 Public Taxonomies

- `GET /api/v1/categories` 和 `GET /api/v1/tags` 只返回至少关联一篇当前公开文章的分类或标签。
- `publishedArticleCount` 只统计当前可公开文章。
- 分类和标签结果使用稳定名称与 slug 排序，不因数据库读取顺序变化。

### C7 Public Profile

- `GET /api/v1/portfolio/profile` 返回 displayName、headline、安全 `bioHtml` 和可空 seoDescription。
- 个人简介不存在时 API 返回 404；首页和 About 页面显示稳定不可用状态，其他公开区域继续工作。

### C8 Public Projects

- 项目状态与文章一致，只公开已经到达发布时间的 `PUBLISHED` 项目。
- `GET /api/v1/portfolio/projects` 支持 0 基分页和可选 featured 筛选，固定按 `publishedAt DESC, id DESC` 排序。
- `GET /api/v1/portfolio/projects/{slug}` 返回安全 `descriptionHtml`、可空 HTTPS 项目链接、仓库链接和 `/projects/{slug}` canonical path。
- 草稿、归档、未来发布时间和不存在的项目统一返回 404。

### C9 Skills And Experiences

- `GET /api/v1/portfolio/skills` 只返回 visible 技能，按 sortOrder 与 id 稳定排序，但不公开 sortOrder。
- `GET /api/v1/portfolio/experiences` 只返回 visible 经历，结束日期不得早于开始日期。
- 经历摘要通过与文章相同的安全 Markdown 管线输出；API 不公开 visible、version 或内部排序字段。

### C10 Public API Contract

- P2 只增加匿名 GET API，不增加 POST、PUT、PATCH 或 DELETE 路由。
- 400/404 使用 `application/problem+json`，包含 `type`、`title`、`status`、`detail`、`instance` 和稳定 `code`。
- 错误响应不得包含堆栈、SQL、实体内容、原始异常或 unpublished 资源信息。
- Controller、OpenAPI、生成 TypeScript 和前端 runtime schema 必须保持一致。

### C11 Frontend Runtime Boundary

- 所有 content 与 portfolio 响应先通过 strict Zod schema，再进入 Vue 组件和 TypeScript union。
- 未知字段、非法 UUID、非法 ISO 时间、非法 HTTPS URL、非法 slug 和危险 HTML 均被拒绝。
- API client 只访问同源相对路径；TanStack Query key 包含分页、筛选和详情 slug。
- Pinia 不复制公开服务端数据，未知 API 值不得通过 `as` 强制进入业务类型。

### C12 Public Page Experience

- 公开路由固定为 `/`、`/about`、`/blog`、`/blog/:slug`、`/projects`、`/projects/:slug`。
- 页面覆盖 loading、empty、error 和 404 状态；一个区域失败不得使无关区域不可访问。
- 空数据库时首页保留 StudyStack 站点身份，博客、项目、简介、技能和经历显示稳定空或不可用状态。
- 桌面和移动端不得出现内容重叠或横向溢出。

### C13 Client SEO

- 页面按内容更新 document title、description、canonical、`og:title`、`og:description` 和 `og:url`。
- 页面卸载时清理 P2 动态 head 元素，避免路由之间残留 SEO 数据。
- canonical URL 由浏览器 origin 与服务端提供的站内 canonical path 组合，不接受任意外部 canonical host。

### C14 Sitemap And Robots

- `GET /sitemap.xml` 包含静态公开页、当前公开文章和当前公开项目，不包含草稿、归档、未来发布或管理页面。
- `GET /robots.txt` 指向 sitemap，并禁止 `/admin`、`/api`、`/oauth2` 和 `/login/oauth2`。
- sitemap 使用配置的公开 HTTPS origin；超出单次 URL 上限时明确失败，不静默截断。
- Caddy 必须将 sitemap 和 robots 代理到后端，不能落入 SPA fallback；`/actuator` 保持拒绝访问。

### C15 Scope And Aggregate Gate

- `content` 拥有文章、分类和标签；`portfolio` 拥有简介、项目、技能和经历。
- 两个模块只复用 `shared` 的 slug、Markdown、SEO、OpenAPI 和公开错误能力，不依赖 admin、comment 或 media。
- P2 不插入演示数据，不创建管理写接口，不引入评论、媒体、搜索服务或额外基础设施。
- P2 完成必须通过后端、前端、OpenAPI、Compose、Playwright、模块边界和范围扫描。

## 4. Failure Contract

| 场景 | 对外行为 |
|---|---|
| page 为负数、size 不在 1 至 50、featured 类型非法 | 400，`code=invalid_request` |
| category 或 tag 查询 slug 非法 | 400，不回显内部解析异常 |
| 文章或项目详情 path slug 非法 | 页面显示稳定 404，不发送 API 请求 |
| 文章或项目不存在、未发布、已归档或发布时间未到 | 404，统一不可用信息 |
| 个人简介不存在 | API 返回 404；页面显示稳定不可用状态 |
| content/portfolio 响应不符合 strict schema | 前端拒绝该响应并进入 error 状态 |
| Markdown HTML 含危险片段 | 前端拒绝渲染，不绕过 `SafeMarkdownView` |
| 公开数据为空 | 返回空分页或空数组；页面显示 empty 状态，不视为服务器错误 |
| sitemap 超过固定上限或公开基址非法 | 服务端明确失败，不截断 URL，也不降级为不安全 origin |

## 5. Acceptance Criteria

- C1-C15 均有自动化测试证据，映射见 `TECH.md`。
- Flyway V3/V4、JPA validate、唯一键、状态、日期、URL、排序、外键和乐观锁约束通过 PostgreSQL 集成测试。
- 文章与项目公开可见性、分页、筛选、详情和隐藏资源 404 通过集成测试。
- 安全 Markdown 对支持语法和危险载荷的服务端与前端测试通过。
- 公开 API、ProblemDetail、OpenAPI、生成类型和 strict Zod mapping 保持同步。
- 六个公开路由的完整、空、错误、404、SEO 与响应式体验通过 Vitest 和 Playwright。
- sitemap、robots、Caddy 代理、Compose topology 和空数据库体验通过聚合验证。
- P0/P1 的工程、身份、Session、安全和契约测试无回归。

## 6. Non-Goals

- 不提供文章、分类、标签、项目、简介、技能或经历的创建、编辑、发布和删除 API。
- 不实现后台管理页面、草稿编辑器、服务端预览或审计日志；这些属于 P3。
- 不实现评论、互动、全文搜索、订阅、访问统计或推荐系统。
- 不实现图片和附件上传；Markdown 图片在 P2 中被拒绝，媒体能力属于 P5。
- 不增加 Redis、Elasticsearch、MinIO 或其他基础设施。
- 不执行公网域名、TLS、备份、监控或正式生产部署。

## 7. Scope Gate

P2 Task 1-15 已完成聚合验证，当前状态为 `awaiting-user-acceptance`。P3 规格可以独立存在，但未经用户明确授权不得开始管理写功能；P2 PR 不包含 P3 规格或后续阶段实现。
