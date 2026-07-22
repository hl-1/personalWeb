---
task_id: P3-ADMIN-001
title: StudyStack 管理后台技术规格
phase: P3
status: awaiting-user-acceptance
created: 2026-07-21
updated: 2026-07-22
product_ref: specs/features/P3-ADMIN-001/PRODUCT.md
implementation_ref: specs/features/P3-ADMIN-001/IMPLEMENTATION_PLAN.md
migration_ref: specs/features/P3-ADMIN-001/ELEMENT_PLUS_MIGRATION_PLAN.md
design_refs:
  - C:/softWare/project/workDoc/java-personal-website-design.md
  - docs/AI-PROJECT-DELIVERY-PLAN.md
  - specs/features/P2-CONTENT-PORTFOLIO-001/TECH.md
---

# P3 Admin Technical Specification

## 1. Technical Goals And Constraints

- 在 P1 的动态 ADMIN/Session/CSRF 基线和 P2 的 content、portfolio 领域模型上增加安全管理写入能力。
- `admin` 只编排管理用例、HTTP 契约和审计；业务写规则留在 content/portfolio 的 `application.admin` named interface 后方。
- 所有成功变更使用同事务最小审计，所有可变资源使用 JPA `@Version` 防止静默覆盖。
- Markdown 预览复用 P2 服务端 renderer 和前端 `SafeMarkdownView`，不新增浏览器 Markdown parser 或第二套白名单。
- DTO、OpenAPI、生成 TypeScript、strict Zod、表单 mapping 和测试必须同步；未知 API 值不得通过类型断言进入业务 union。
- P3 不改变 P2 公开 API 的可见性、排序、SEO、sitemap 或安全渲染契约。

## 2. Ownership And Boundaries

| 层 | 责任 | 禁止事项 |
|---|---|---|
| `admin.application` | actor 解析、管理用例编排、业务写入与审计事务边界、Markdown 预览入口 | 不直接读取业务 Entity 或 Repository |
| `admin.domain` | 审计动作、资源类型、审计记录和 Repository 端口 | 不保存正文、表单快照或安全凭据 |
| `admin.web` | `/api/v1/admin/**` Controller、DTO、校验和 ProblemDetail 映射 | 不复制 content/portfolio 领域规则 |
| `content.application.admin` | 文章、分类、标签命令、查询、状态和引用规则 | 不依赖 admin web DTO 或审计实现 |
| `portfolio.application.admin` | 项目、简介、技能、经历命令、查询和状态规则 | 不依赖 admin web DTO 或审计实现 |
| `content.domain` / `portfolio.domain` | 实体不变量、集中字段规则、slug/URL 归一化和 Repository 端口 | 不处理认证、CSRF 或页面状态 |
| `identity.infrastructure.security` | Session principal、动态角色和 `/api/v1/admin/**` 授权 | 不承担管理业务校验 |
| `shared.markdown` / `shared.web` | 安全 Markdown 与基础 ProblemDetail 能力 | 不保存业务数据或管理状态 |
| `web/src/features/admin` | runtime schema、显式 mapping、client、Query、表单和临时草稿 | 不复制用户/角色/服务端列表到 Pinia |
| `web/src/views/admin` | 管理页面交互和状态呈现 | 不信任未校验响应，不实现本地 Markdown 渲染 |

`StudyStackModulesTest` 和 `P3ScopeContractTest` 持续验证 admin 只能依赖批准的 named interface，并阻止 comment、media 或其他后续阶段能力进入 P3。

## 3. Data Model And Migrations

### 3.1 V5 Admin Audit Log

`V5__admin_audit_log.sql` 创建 `admin_audit_log`：

- `id UUID` 主键，由应用生成。
- `actor_user_id UUID` 外键指向本地 `identity_user_account`，不保存 GitHub subject。
- `action` 只允许 `CREATE`、`UPDATE`、`DELETE`、`PUBLISH`、`ARCHIVE`。
- `resource_type` 只允许 `ARTICLE`、`CATEGORY`、`TAG`、`PROJECT`、`PROFILE`、`SKILL`、`EXPERIENCE`。
- `resource_id UUID`、可空非负 `resource_version` 和非空 `occurred_at TIMESTAMPTZ`。
- 时间和资源维度建立稳定索引；数据库 trigger 拒绝 UPDATE 与 DELETE，保证 append-only。

测试清理只能使用事务回滚或 TRUNCATE，不得关闭生产 trigger 或增加审计修改 API。

### 3.2 V6 Taxonomy Name Uniqueness

`V6__taxonomy_name_uniqueness.sql` 为 `content_category.name` 和 `content_tag.name` 增加唯一约束，使管理写入的名称规则与 slug 唯一规则同时在数据库收口。

P3 不新建 content/portfolio 业务表；既有 P2 schema、状态 CHECK、URL/日期约束和 `version` 列继续作为数据库防线。Flyway 仍是唯一 schema 所有者，JPA 使用 `ddl-auto=validate`。

## 4. Security And Actor Resolution

- `SecurityConfiguration` 对 `/api/v1/admin/**` 要求 `ROLE_ADMIN`，保留 P1 的动态管理员计算、Session 活性检查和 API 401/403 语义。
- 所有管理写请求保留 Spring Security CSRF；客户端通过 P1 `/api/v1/auth/csrf` 获取 header 名与 token。
- `AdminActorResolver` 从已认证 `StudyStackPrincipal` 取得本地用户 UUID，并拒绝缺少有效管理员身份的调用。
- Controller 不接收 actor ID，避免客户端伪造审计主体。
- 管理响应与错误不得包含 GitHub subject、Session ID、CSRF token、OAuth token、数据库对象图或原始安全异常。

前端 `requiresAdmin` 只处理导航体验。即使绕过 Vue 路由或直接调用 API，服务端仍必须独立执行认证、授权与 CSRF。

## 5. Transaction And Audit Pipeline

```text
Admin HTTP request
  -> Spring Security ADMIN + CSRF
  -> Bean Validation
  -> AdminActorResolver
  -> Admin*UseCase transaction
  -> content/portfolio application.admin command
  -> JPA flush and resulting version
  -> AdminAuditService append-only insert
  -> transaction commit
```

- 管理用例方法定义事务边界，使业务变更与审计 insert 原子提交。
- 审计记录使用变更后的 version；DELETE 的 version 可空。
- 校验、授权、CSRF、预览、not found、stale version 和其他失败在提交前退出，不写成功审计。
- 审计 insert 失败必须回滚业务变更，不能降级为日志告警后继续提交。
- P3 不提供 outbox、异步审计或审计查询；未来扩展不能削弱当前原子性和最小数据原则。

## 6. State, Version And Domain Rules

### 6.1 Article And Project State

- 状态使用 P2 的 `DRAFT`、`PUBLISHED`、`ARCHIVED`，唯一流转为 `DRAFT -> PUBLISHED -> ARCHIVED`。
- DRAFT 可编辑 slug 并硬删除；PUBLISHED 可编辑 slug 之外的内容并归档；ARCHIVED 拒绝修改、删除和再次发布。
- 发布命令可省略 `publishAt`，由服务端使用当前 UTC Instant；显式值允许未来时间。
- 公开可见性仍由 P2 的 `PUBLISHED && publishedAt <= now` 决定。

### 6.2 Optimistic Concurrency

- Entity 的 JPA `@Version` 是最终并发判断，业务命令携带客户端读取的非负 version。
- PUT 和 publish/archive JSON body 必须包含 version；DELETE 使用 `?version=`，不使用 DELETE body。
- `OptimisticLockingFailureException` 及明确的版本不匹配统一映射为 409 `stale_version`。
- 冲突响应不返回最新实体，前端不自动 retry；用户重新加载后才能基于新 version 再提交。

### 6.3 Centralized Field Rules

- content 的字段限制与 slug 归一化集中在 `ContentFieldRules` 和 `ContentSlugConverter`。
- portfolio 的字段、slug 和 HTTPS URL 规则集中在 `PortfolioFieldRules`、`PortfolioSlugConverter` 和 `PortfolioUrlPolicy`。
- taxonomy 引用删除保护由 application service 与 Repository 查询完成；已引用资源返回 `taxonomy_in_use`。
- 文章标签最多 10 个且不得重复；简介使用单例语义；经历日期、技能/经历 sortOrder 与 visible 规则由领域和数据库共同保证。

## 7. Admin API Contract

### 7.1 Articles

| Method | Path | Success |
|---|---|---|
| GET | `/api/v1/admin/articles?page=0&size=20&status=&query=` | 200 page |
| POST | `/api/v1/admin/articles` | 201 + Location |
| GET | `/api/v1/admin/articles/{id}` | 200 detail |
| PUT | `/api/v1/admin/articles/{id}` | 200 detail |
| DELETE | `/api/v1/admin/articles/{id}?version=` | 204 |
| POST | `/api/v1/admin/articles/{id}/publish` | 200 detail |
| POST | `/api/v1/admin/articles/{id}/archive` | 200 detail |
| POST | `/api/v1/admin/articles/preview` | 200 safe HTML |

分页从 0 开始，默认 20，范围 1 至 100；固定按 `updatedAt DESC, id DESC`，不接受客户端 sort。query trim 后最多 100 字符，只匹配标题和 slug；status 必须是精确枚举。

### 7.2 Categories And Tags

| Resource | Collection | Item |
|---|---|---|
| categories | `GET|POST /api/v1/admin/categories` | `PUT|DELETE /api/v1/admin/categories/{id}` |
| tags | `GET|POST /api/v1/admin/tags` | `PUT|DELETE /api/v1/admin/tags/{id}` |

DELETE 使用必填 version 查询参数。响应包含 id、name、slug、articleCount、审计时间和 version。

### 7.3 Portfolio

| Resource | Endpoints |
|---|---|
| profile | `GET|PUT /api/v1/admin/portfolio/profile` |
| projects | collection/detail CRUD、`publish`、`archive`、`preview` under `/api/v1/admin/portfolio/projects` |
| skills | collection GET/POST 与 item PUT/DELETE under `/api/v1/admin/portfolio/skills` |
| experiences | collection GET/POST 与 item PUT/DELETE under `/api/v1/admin/portfolio/experiences` |

profile 首次 PUT 接受 `version=null`，已有单例必须提交非负 version。项目列表分页和排序与文章一致；preview 只接受 Markdown 并返回安全 `html`。

## 8. Request, Response And Error Mapping

- 文章/项目列表 DTO 不包含 Markdown 正文，详情 DTO 包含原始 Markdown 供管理员继续编辑。
- 创建响应返回 201 和 `Location`；读取、更新和状态命令返回 200；删除返回 204。
- Bean Validation 在 web 边界校验长度、格式、枚举、日期、URL、分页和集合数量；application/domain/database 继续验证不变量。
- 文章 Create/Update DTO 的 slug 长度固定为 3 至 120，使短 slug 在 Bean Validation 层产生 `fieldErrors.slug`，同时保留共享 `SlugPolicy` 作为最终防线。
- `AdminApiExceptionHandler` 只映射明确业务 failure，产生 `type`、`title`、`status`、`detail`、`instance` 和 `code`。
- 固定业务码为 `validation_failed`、`not_found`、`duplicate_slug`、`stale_version`、`invalid_state_transition`、`taxonomy_in_use` 和 `draft_delete_only`；安全层另提供 `forbidden` 与 `csrf_failed`。
- `fieldErrors` 使用字段到消息数组的结构；未知数据库或程序错误不伪装成业务冲突。

## 9. Markdown Preview Boundary

```text
Raw admin Markdown
  -> POST admin preview endpoint
  -> shared server MarkdownRenderer
  -> OWASP sanitized HTML
  -> strict safeHtmlSchema
  -> SafeMarkdownView
```

- preview 不保存 Markdown、不写审计，也不改变文章或项目 version。
- 前端 `MarkdownEditor.vue` 只负责编辑/预览模式、请求状态和结果展示。
- `v-html` 继续只由共享 `SafeMarkdownView` 持有；任何新增预览入口必须复用相同 server/client 安全链。

## 10. Frontend Runtime And Data Flow

- Element Plus 通过 Vite resolver 按需导入，主题变量与控件限定于管理体验；公共页面保留原有控件，仅允许共享操作反馈服务调用 `ElMessage`。
- `element-plus-plugins.ts` 统一配置组件与 composable resolver；开发构建生成 `auto-imports.d.ts` 和 `components.d.ts`，测试配置复用 resolver 但不改写类型文件。
- Element Plus 只替换管理 UI 原语，不改变 Zod runtime schema、表单 validation、API client、TanStack Query、Pinia 草稿或路由授权契约。
- `admin-form-rules.ts` 集中维护管理表单共用的 slug、必填文本、长度、HTTPS URL、日期、可选空值和非负整数 schema，避免各页面复制规则。
- `admin-form-validation.ts` 统一维护 touched、submit、Zod issue、后端 `fieldErrors`、字段聚焦和修正后清除行为。
- `AdminFormField.vue` 统一渲染单一且对辅助技术隐藏的必填标记、常驻规则提示、首个字段错误，以及 label、`aria-describedby`、`aria-invalid` 的关联；不得同时启用 Element Plus 的第二个 required marker。
- `admin-schema.ts` 使用 strict Zod 校验 UUID、Instant、日期、状态、HTTPS URL、分页和各资源响应。
- 解析后使用显式 mapper 构造文章、taxonomy、项目、简介、技能、经历和预览业务对象。
- 表单 schema 独立验证可编辑字段及合法枚举/集合；API 响应进入表单时只复制允许字段，不传递 id、时间戳等未知属性。
- `admin-client.ts` 只请求同源 `/api/v1/admin/**`，复用 Session 与 CSRF，集中解析 ProblemDetail。
- `admin-query.ts` 以 `['admin']` 为根 key，为列表、详情和 taxonomy 建立稳定子 key；成功写入后只失效相关查询。
- `operation-feedback.ts` 使用 Pinia 按应用实例维护短时操作记录，并统一调用 Element Plus `ElMessage` 显示可关闭的成功或失败通知；定时清理只影响本地记录，不改变写请求结果。
- `admin-operation-feedback.ts` 集中维护允许的管理写操作键及中文成败文案；页面只能在已校验实体、Location 或 `204` 响应成功后发布成功通知，后端 DTO 和 OpenAPI 不增加展示文案字段。
- `admin-confirmation.ts` 集中封装 `ElMessageBox.confirm`，将结果归一化为 `confirmed | cancelled | closed`；调用方必须为三个结果分别定义行为，关闭不得默认执行取消分支。
- stale version 不自动 retry；401/403、validation、conflict、network 和 invalid response 保持可区分状态。
- 前端校验失败不调用 API；后端字段错误进入同一字段状态，未知字段或非字段问题保留为表单级错误。
- 可选 URL、文本和日期的空字符串在 schema 边界归一化为 `null`；slug 按后端规则 trim、转小写并验证 3 至 120 位格式。
- 管理文章列表通过 `Intl.DateTimeFormat` 将已校验的 `updatedAt` Instant 显示为浏览器本地日期时间，并用 `<time datetime>` 保留原始 UTC 值。
- 文章/项目 Pinia draft store 只保留未提交可编辑字段，使用 new/resource key 和 version 防止过期草稿覆盖新数据。

## 11. Routing And Views

| Route | View Responsibility |
|---|---|
| `/admin` | 管理入口和资源导航，不生成虚构统计 |
| `/admin/articles` | 文章分页、筛选、状态和操作 |
| `/admin/articles/new` | 新建文章与草稿保护 |
| `/admin/articles/:id` | 编辑、预览、发布、归档和冲突处理 |
| `/admin/categories` | 分类 CRUD 与引用冲突 |
| `/admin/tags` | 标签 CRUD 与引用冲突 |
| `/admin/portfolio/projects` | 项目分页和状态操作 |
| `/admin/portfolio/projects/new` | 新建项目与草稿保护 |
| `/admin/portfolio/projects/:id` | 编辑、预览、发布和归档 |
| `/admin/portfolio/profile` | 简介单例创建与重复保存 |
| `/admin/portfolio/skills` | 技能 CRUD、排序和可见性 |
| `/admin/portfolio/experiences` | 经历 CRUD、日期、排序和可见性 |

`AdminLayout.vue` 提供稳定导航和退出入口。路由元数据要求 admin，守卫复用 P1 认证查询；匿名跳转登录并保留安全 return target，非管理员跳转 forbidden。后台 wildcard 使用独立 404，不能落入公开内容详情。

## 12. OpenAPI And Generated Contract

- `OpenApiConfiguration` 聚合 P0-P3 API 和 ProblemDetail schema；dev/test 开启文档，prod 继续关闭。
- Controller 注解、DTO、校验约束和错误响应是 OpenAPI 输入，禁止手工修改生成 TypeScript 以掩盖差异。
- `web/src/shared/api/generated/openapi.d.ts` 由 backend OpenAPI 生成，并由 `pnpm contract:check` 验证。
- 任何管理字段或枚举变更必须同时检查 DTO、OpenAPI、generated types、runtime schema、表单 mapping、测试、`PRODUCT.md` 和 `TECH.md`。

## 13. Behavior-To-Test Traceability

| 行为 | 主要自动化证据 |
|---|---|
| A1 模块边界 | `StudyStackModulesTest`、`P3ScopeContractTest`、`JavaSourceStructureTest` |
| A2 ADMIN/CSRF | `AdminAuthorizationIntegrationTest`、`admin-security.spec.ts` |
| A3 审计 | `AdminAuditSchemaIntegrationTest`、`AdminAuditServiceIntegrationTest`、各管理 API integration test |
| A4 并发与错误 | `AdminErrorContractIntegrationTest`、各 application/API integration test、前端 View/E2E |
| A5-A7 文章与预览 | `ArticleAdminServiceIntegrationTest`、`AdminArticleApiIntegrationTest`、article Vitest、`admin-content.spec.ts` |
| A8 taxonomy | `TaxonomyAdminServiceIntegrationTest`、`AdminTaxonomyApiIntegrationTest`、taxonomy Vitest/E2E |
| A9-A10 项目与预览 | `ProjectAdminServiceIntegrationTest`、`AdminProjectApiIntegrationTest`、project Vitest、`admin-portfolio.spec.ts` |
| A11-A12 portfolio | `PortfolioAdminServiceIntegrationTest`、`AdminPortfolioApiIntegrationTest`、portfolio Vitest/E2E |
| A13 契约 | `AdminOpenApiIntegrationTest`、admin schema/client tests、`contract:check` |
| A14 数据层 | `admin-schema.spec.ts`、`admin-client.spec.ts`、`admin-query.spec.ts`、`admin-form-validation.spec.ts`、draft store tests |
| A15 路由与外壳 | `admin-element-plus.spec.ts`、`admin-routing.spec.ts`、`admin-layout.spec.ts`、router tests、security E2E |
| A16-A17 页面体验 | `admin-confirmation.spec.ts`、operation feedback、`AdminFormField.spec.ts`、article/taxonomy/project/portfolio View tests 与管理 E2E |
| A18 聚合回归 | backend verify、frontend lint/typecheck/test/build、contract、Compose、Playwright |

聚合验证入口：

```powershell
Set-Location server
.\mvnw.cmd verify

Set-Location ..\web
pnpm lint
pnpm typecheck
pnpm test
pnpm contract:check
pnpm build
pnpm test:e2e
```

详细 CI 证据以 `specs/PROGRESS.md` 和实施交接记录为准，避免在本规格维护易过时的测试数量。

## 14. Risks And Compatibility

- 修改状态或 slug 规则会影响 P2 公开 URL、sitemap 和历史数据，必须先定义保留、映射与拒绝策略。
- 修改管理 DTO 必须同步 generated types 和 runtime mapping；严禁以 `as` 绕过历史或未知响应值。
- 修改 version 语义会影响所有 PUT、DELETE、publish/archive、草稿恢复和冲突页面，必须保持旧客户端失败可诊断。
- 修改 taxonomy 唯一约束前必须检查现有数据是否重复；迁移不得静默重命名或删除历史分类/标签。
- 修改审计字段或资源枚举必须保持 append-only、最小数据和事务原子性，不能记录正文或凭据。
- Markdown 规则只能在共享 renderer 与 safe HTML schema 中集中演进，不能在 admin view 增加特例。

## 15. Completion Gate

P3 只有在以下条件同时满足时可交付：

- A1-A18 的后端、前端、OpenAPI、Compose、Playwright、模块边界和安全证据均通过。
- 管理写入与审计原子提交，版本冲突不覆盖数据，失败路径不产生成功审计。
- 公开页面只反映符合 P2 状态、时间与 visible 规则的数据，草稿、归档和未来发布继续隐藏。
- `PRODUCT.md`、`TECH.md`、实施计划和进度文档与当前代码契约一致。
- 状态保持 `awaiting-user-acceptance`，直到用户明确验收；未经授权不得开始 P4。
