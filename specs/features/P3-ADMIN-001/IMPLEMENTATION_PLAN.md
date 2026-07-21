---
task_id: P3-ADMIN-001
title: StudyStack 管理后台
phase: P3
status: implemented-awaiting-user-acceptance
created: 2026-07-17
updated: 2026-07-21
design_refs:
  - C:/softWare/project/workDoc/java-personal-website-design.md
  - docs/AI-PROJECT-DELIVERY-PLAN.md
  - specs/features/P2-CONTENT-PORTFOLIO-001/IMPLEMENTATION_PLAN.md
---

# StudyStack P3 Admin Implementation Plan

> **For agentic workers:** 使用 `superpowers:executing-plans` 按 Task 执行。用户每次只授权一个 Task；当前 Task 验证完成后必须停止，不得自动进入下一 Task。

**Goal:** 为已通过 P2 建立的文章、分类、标签和作品集模型提供安全、可审计、可处理并发冲突的管理 API 与 Vue 管理后台，同时保持公开读取契约不变。

**Architecture:** `content` 和 `portfolio` 继续拥有领域状态、数据和写命令；`admin` 只通过两个模块公开的 named interface 编排管理员用例、记录审计并暴露 `/api/v1/admin/**`。服务端 Spring Security 是授权最终边界，PostgreSQL `@Version` 是并发最终边界；Vue 只改善交互，所有接口响应仍经 Zod 运行时校验。

**Tech Stack:** Java 21、Spring Boot 3.5.16、Spring Modulith、Spring Security、Spring Data JPA、PostgreSQL、Flyway、Bean Validation、OpenAPI 3、Vue 3、TypeScript、TanStack Query、Pinia、Zod、Vitest、Playwright。

---

## 0. 使用方式与执行门禁

执行某个任务时向 AI 发送：

```text
请读取 C:\softWare\project\studystack\specs\features\P3-ADMIN-001\IMPLEMENTATION_PLAN.md。
本次只执行 Task N。严格完成该 Task 的 RED、GREEN、REFACTOR 和验证，完成后停止，不提交、不推送。
```

- Codex 自动加载全局 `AGENTS.md`，任务提示不重复其中的通用规则。
- P2 的 15 个 Task 必须已完成聚合验证并经用户明确验收，才能执行 P3 Task 1；当前 P2 未完成或工作区仍有未处理改动时必须停止并报告，不得混入、回滚或代替用户处理。
- 每次只执行一个 Task，只修改该 Task 的 Files 列表；发现前置 Task 缺口时停止并指出对应 Task，不越界补做。
- RED 预期失败不是阻塞；意外通过时先说明已有行为，再继续完成当前 Task 的 GREEN 和 REFACTOR。
- 未经用户单独授权，不执行 commit、push、PR、部署、真实生产数据写入或真实 GitHub OAuth 配置。
- `C:\softWare\project\latest\skillhub` 始终只读，操作前后只读取状态，不得清理其中已有改动。
- 不实现评论审核、媒体上传、图片 Markdown、富文本编辑器、自动保存、内容修订历史、批量操作、多人协作、通知或生产运维。
- P3 不改变 P2 公开 GET 路径和响应字段；后台写入后通过同一数据库自然反映到公开查询，不引入 Redis、消息队列或服务端业务缓存。

## 1. 固定产品与接口契约

### 1.1 权限、CSRF 与模块边界

- `/api/v1/admin/**` 全部要求已登录且拥有服务端动态计算的 `ADMIN` 角色；匿名返回 401，普通用户返回 403。
- 所有 POST、PUT、DELETE 管理请求必须携带 P1 返回的有效 CSRF 请求头；缺失或错误 Token 返回 403。
- 前端 `requiresAdmin` 路由守卫只负责导航体验，不能代替服务端授权，也不能从浏览器本地状态推导管理员身份。
- `admin` 不直接访问 `content`、`portfolio` 的 Entity 或 Repository，只依赖各模块 `application.admin` named interface。
- `content` 拥有文章、分类、标签写规则；`portfolio` 拥有简介、项目、技能、经历写规则；`admin` 拥有管理 HTTP、用例编排和审计记录。
- 管理接口不得返回 GitHub ID、Session ID、CSRF Token、OAuth Token、数据库外键对象或完整安全异常。

### 1.2 状态、删除与发布时间

- 文章和项目状态沿用 P2：`DRAFT`、`PUBLISHED`、`ARCHIVED`。
- 唯一允许的状态流转是 `DRAFT -> PUBLISHED -> ARCHIVED`；P3 不提供撤回、恢复草稿或重新发布归档内容。
- DRAFT 可修改 slug、内容并硬删除；PUBLISHED 可修改除 slug 外的内容并归档；ARCHIVED 只读，不能修改、删除或重新发布。
- 发布请求可省略 `publishAt`，省略时使用服务端当前 UTC 时间；提供时必须是合法 ISO-8601 `Instant`，允许未来时间。
- 未来发布资源状态是 PUBLISHED，但在到达 `publishAt` 前继续被 P2 匿名 API、页面和 sitemap 隐藏。
- 文章最多关联一个分类和 10 个互不重复标签；引用中的分类或标签不得删除，返回 409 `taxonomy_in_use`。
- 简介是单例；技能和经历没有发布状态，以 P2 的 `visible` 字段控制公开可见性。

### 1.3 乐观并发与错误契约

- 所有管理详情响应和可变资源响应必须包含非负 `version`；列表摘要也包含 version，便于进入编辑页前识别变化。
- PUT 与状态命令的 JSON 请求体必须包含客户端最后读取的 `version`；DELETE 使用必填查询参数 `version`，不使用 DELETE 请求体。
- JPA `@Version` 是最终并发判断。版本不匹配不得覆盖数据，返回 409 `stale_version`；响应不返回服务器最新正文。
- 前端收到 `stale_version` 后保留当前表单，只提供“重新加载最新数据”和“取消”选择，不自动重试写请求或静默覆盖。
- 固定错误码：`validation_failed`、`not_found`、`duplicate_slug`、`stale_version`、`invalid_state_transition`、`taxonomy_in_use`、`draft_delete_only`、`forbidden`、`csrf_failed`。
- 错误继续使用 P2 的 `application/problem+json` 字段：`type`、`title`、`status`、`detail`、`instance`、`code`；校验错误可增加按字段组织的 `fieldErrors`，不得包含堆栈、SQL、Markdown 正文或安全对象。
- slug、Markdown、URL、枚举、日期、长度和标签数量由 Bean Validation 做入口校验，并由领域规则和数据库约束再次保证；数据库异常只映射已确认的约束，不使用模糊字符串匹配吞掉未知异常。

### 1.4 管理 API

文章：

- `GET /api/v1/admin/articles?page=0&size=20&status=<optional>&query=<optional>`
- `POST /api/v1/admin/articles`
- `GET /api/v1/admin/articles/{id}`
- `PUT /api/v1/admin/articles/{id}`
- `DELETE /api/v1/admin/articles/{id}?version=<version>`
- `POST /api/v1/admin/articles/{id}/publish`
- `POST /api/v1/admin/articles/{id}/archive`
- `POST /api/v1/admin/articles/preview`

分类与标签：

- `GET|POST /api/v1/admin/categories`
- `PUT /api/v1/admin/categories/{id}`
- `DELETE /api/v1/admin/categories/{id}?version=<version>`
- `GET|POST /api/v1/admin/tags`
- `PUT /api/v1/admin/tags/{id}`
- `DELETE /api/v1/admin/tags/{id}?version=<version>`

作品集：

- `GET /api/v1/admin/portfolio/profile`
- `PUT /api/v1/admin/portfolio/profile`
- `GET|POST /api/v1/admin/portfolio/projects`
- `GET|PUT /api/v1/admin/portfolio/projects/{id}`
- `DELETE /api/v1/admin/portfolio/projects/{id}?version=<version>`
- `POST /api/v1/admin/portfolio/projects/{id}/publish`
- `POST /api/v1/admin/portfolio/projects/{id}/archive`
- `POST /api/v1/admin/portfolio/projects/preview`
- `GET|POST /api/v1/admin/portfolio/skills`
- `PUT /api/v1/admin/portfolio/skills/{id}`
- `DELETE /api/v1/admin/portfolio/skills/{id}?version=<version>`
- `GET|POST /api/v1/admin/portfolio/experiences`
- `PUT /api/v1/admin/portfolio/experiences/{id}`
- `DELETE /api/v1/admin/portfolio/experiences/{id}?version=<version>`

统一 HTTP 语义：创建返回 201 和 `Location`，读取/更新/状态命令返回 200，成功删除返回 204。管理分页从 0 开始，默认 20、最小 1、最大 100；固定排序 `updatedAt DESC, id DESC`，不接受任意客户端 sort。`status` 只接受精确枚举；`query` trim 后最多 100 字符，只匹配标题和 slug。

### 1.5 管理请求与响应字段

- 文章创建/更新：`slug`、`title`、`summary`、`bodyMarkdown`、可空 `categoryId`、`tagIds`、可空 `seoTitle`、可空 `seoDescription`；更新额外包含 `version`。
- 文章详情额外返回 `id`、`status`、可空 `publishedAt`、`createdAt`、`updatedAt`、`version`；列表不返回 `bodyMarkdown`。
- 分类和标签写入 `name`、`slug`，更新额外包含 `version`；响应包含 `id`、引用数量、时间和 `version`。
- 简介写入 `displayName`、`headline`、`bioMarkdown`、可空 `seoDescription`；首次创建 `version=null`，已有简介更新必须提交非负 version。
- 项目创建/更新：`slug`、`title`、`summary`、`descriptionMarkdown`、可空 HTTPS `projectUrl`、可空 HTTPS `repositoryUrl`、`featured`、非负 `sortOrder`；更新额外包含 `version`。
- 技能写入 `name`、`category`、可空 `summary`、非负 `sortOrder`、`visible`；经历写入 `organization`、`role`、`startDate`、可空 `endDate`、`summaryMarkdown`、非负 `sortOrder`、`visible`。
- preview 请求只包含 Markdown 原文，响应只包含 P2 同一个服务端渲染器产生的 `html`；preview 不持久化、不写审计、不允许前端本地渲染 Markdown。
- 前端生成类型只作为静态提示，所有管理 JSON 仍必须通过 Zod strict schema 和显式 map/filter 后进入表单或 union 类型，禁止使用 `as` 信任接口值。

### 1.6 审计契约

- 每个成功的数据变更在同一个数据库事务写入一条 append-only 审计记录；业务变更或审计任一失败时整体回滚。
- 审计字段固定为：UUID、管理员本地用户 UUID、动作、资源类型、资源 UUID、变更后的可空 version、UTC 时间。
- 审计不保存标题、slug、Markdown、URL、表单快照、GitHub ID、Session ID、Token、IP 或 User-Agent；P3 不开放审计查询 API 和页面。
- 失败校验、未授权请求、CSRF 失败、预览和并发冲突不写“成功变更”审计。
- 数据库拒绝 UPDATE 和 DELETE 审计行；测试清理使用事务回滚或 TRUNCATE，不削弱生产 append-only 约束。

### 1.7 前端路由与交互

- `/admin`：管理概览和各资源入口，不显示虚构统计。
- `/admin/articles`、`/admin/articles/new`、`/admin/articles/:id`
- `/admin/taxonomy`
- `/admin/projects`、`/admin/projects/new`、`/admin/projects/:id`
- `/admin/profile`、`/admin/skills`、`/admin/experiences`
- 管理页面沿用安静、工作型界面：稳定侧栏/顶部导航、紧凑表格或列表、明确主操作、移动端无重叠和横向溢出；不使用营销式 hero、嵌套卡片或纯装饰背景。
- 每个页面覆盖加载、空、错误、401、403、保存中和操作失败；按钮防重复提交，校验失败聚焦首个字段。
- Markdown 编辑器使用“编辑/预览”分段控制；编辑为普通 textarea，预览调用服务端 preview 并只经 `SafeMarkdownView` 渲染。
- TanStack Query 管理服务端数据；Pinia 只保存当前浏览器会话中的未提交编辑草稿，不复制用户、角色或服务器资源列表。
- 离开脏表单前确认；成功保存或明确丢弃后清理草稿。草稿不得包含 Token，并使用带版本的 key，过期或资源版本变化时不得自动覆盖服务器数据。

## 2. 稳定行为编号

- A1：P3 范围门禁、named interface 与 Modulith 边界。
- A2：所有管理 API 的 ADMIN、Session 和 CSRF 服务端保护。
- A3：成功变更的同事务 append-only 审计。
- A4：version 乐观并发、固定冲突和 ProblemDetail 契约。
- A5：文章管理列表、创建、详情、更新和草稿删除。
- A6：文章发布、未来发布、归档和非法状态流转。
- A7：文章 Markdown 服务端安全预览。
- A8：分类与标签 CRUD、唯一 slug 和引用删除保护。
- A9：项目管理列表、创建、详情、更新和草稿删除。
- A10：项目发布、归档、预览、链接和排序规则。
- A11：个人简介单例创建与乐观更新。
- A12：技能和经历 CRUD、日期、排序与可见性。
- A13：Bean Validation、OpenAPI 3、generated types 和错误映射一致。
- A14：前端 strict Zod、Session/CSRF 写客户端和 Query 缓存。
- A15：管理员路由、后台外壳、权限导航和通用状态。
- A16：文章、Markdown 编辑和分类标签管理体验。
- A17：项目、简介、技能和经历管理体验。
- A18：浏览器、契约、安全、公开读取回归和阶段聚合验收。

## 3. 文件责任图

| 区域 | 责任 | 禁止内容 |
|---|---|---|
| `content/application/admin` | 文章、分类、标签命令接口和领域实现入口 | HTTP、管理员角色判断、审计表写入 |
| `portfolio/application/admin` | 简介、项目、技能、经历命令接口和领域实现入口 | HTTP、评论、媒体上传 |
| `admin/application` | 管理用例编排、actor 解析、同事务审计 | 直接访问其他模块 Repository/Entity |
| `admin/domain`、`admin/infrastructure` | append-only 审计模型和存储 | Markdown 正文或表单快照 |
| `admin/web` | 管理 Controller、DTO、校验、OpenAPI 与错误适配 | 领域状态复制、前端权限判断 |
| `web/src/features/admin` | Zod、Session/CSRF client、Query、mutation 与草稿 | 未校验响应、角色副本、客户端 Markdown 渲染 |
| `web/src/views/admin` | 管理路由和页面交互 | 服务端授权替代、媒体与评论功能 |

## Task 1: P3 模块边界与范围门禁

**覆盖行为：** A1

**Files:**

- Modify: `server/src/main/java/com/studystack/admin/package-info.java`
- Modify: `server/src/main/java/com/studystack/content/package-info.java`
- Modify: `server/src/main/java/com/studystack/portfolio/package-info.java`
- Create: `server/src/main/java/com/studystack/content/application/admin/package-info.java`
- Create: `server/src/main/java/com/studystack/portfolio/application/admin/package-info.java`
- Modify: `server/src/test/java/com/studystack/foundation/StudyStackModulesTest.java`
- Modify: `server/src/test/java/com/studystack/foundation/P2ScopeContractTest.java`
- Create: `server/src/test/java/com/studystack/foundation/P3ScopeContractTest.java`

### RED

- [ ] 将模块测试从“admin 为空”调整为允许 P3 进入实现，但继续要求 `comment`、`media` 为空。
- [ ] 写 P3 范围测试：拒绝评论、上传、富文本、批量、修订历史、审计查询 API，以及 admin 直接引用 content/portfolio 的 domain、repository 或 infrastructure。
- [ ] 写 named interface 依赖测试，要求 admin 只能依赖 `content :: admin`、`portfolio :: admin` 和已批准 shared 接口。
- [ ] 运行：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=StudyStackModulesTest,P2ScopeContractTest,P3ScopeContractTest test'
```

Expected: P3 测试因 admin 仍为空、named interface 不存在或旧 P2 门禁仍拒绝 P3 文件而失败。

### GREEN

- [ ] 为 content、portfolio 创建仅暴露管理命令 DTO/接口的 `application.admin` named interface；内部实现、Entity 和 Repository 保持不可跨模块访问。
- [ ] 收紧 admin 的 `allowedDependencies`，移除 comment、media 和整个业务模块的宽泛依赖。
- [ ] 将 P2 范围测试限定为保护 P2 公开边界，而不是永久禁止后续阶段；不得删除 P2 的 GET-only 回归断言。
- [ ] 重跑定向测试。

Expected: 七个模块仍存在、admin 只经过 named interface 编译、P3 越界类型被准确报告。

### REFACTOR

- [ ] 错误信息输出越界源文件和依赖目标，不使用会掩盖未来新增文件的目录通配白名单。
- [ ] 运行全部 Modulith 与 P0-P2 scope 测试。

## Task 2: 审计数据库迁移 V5

**覆盖行为：** A3

**Files:**

- Create: `server/src/main/resources/db/migration/V5__admin_audit_log.sql`
- Create: `server/src/test/java/com/studystack/admin/infrastructure/AdminAuditSchemaIntegrationTest.java`

### RED

- [ ] 使用 PostgreSQL Testcontainers 测试 `admin_audit_log` 字段、管理员本地用户外键、动作/资源约束、时间索引和资源索引。
- [ ] 测试数据库拒绝审计行 UPDATE、DELETE、未知动作、空 actor、负 version 和不存在用户。
- [ ] 运行：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=AdminAuditSchemaIntegrationTest test'
```

Expected: V5 或目标表不存在。

### GREEN

- [ ] 创建 V5，字段仅包含 `id`、`actor_user_id`、`action`、`resource_type`、`resource_id`、可空 `resource_version`、`occurred_at`。
- [ ] 为动作和资源类型建立明确 CHECK；创建 PostgreSQL trigger 阻止 UPDATE/DELETE，不保存 JSON 快照。
- [ ] 迁移只增加审计表，不修改 V3/V4 历史迁移或业务表。
- [ ] 重跑测试。

Expected: V1-V5 按序执行，约束、索引、外键和 append-only 行为通过。

### REFACTOR

- [ ] 增加事务回滚和 TRUNCATE 测试清理路径，不能为了测试禁用生产 trigger。
- [ ] 运行 P0-P2 全部迁移测试和 Hibernate validate 验证。

## Task 3: 审计模型、actor 与同事务写入

**覆盖行为：** A2、A3、A4

**Files:**

- Create: `server/src/main/java/com/studystack/admin/domain/AdminAuditAction.java`
- Create: `server/src/main/java/com/studystack/admin/domain/AdminResourceType.java`
- Create: `server/src/main/java/com/studystack/admin/domain/AdminAuditEntry.java`
- Create: `server/src/main/java/com/studystack/admin/domain/AdminAuditRepository.java`
- Create: `server/src/main/java/com/studystack/admin/application/AdminActor.java`
- Create: `server/src/main/java/com/studystack/admin/application/AdminActorResolver.java`
- Create: `server/src/main/java/com/studystack/admin/application/AdminAuditService.java`
- Create: `server/src/test/java/com/studystack/admin/application/AdminAuditServiceIntegrationTest.java`

### RED

- [ ] 测试从已认证 principal 名称解析本地 UUID；拒绝匿名、非 UUID 名称和无 ADMIN authority，异常不得回显 principal。
- [ ] 测试每种允许动作写入最小审计字段；业务事务抛错时审计回滚，审计写入失败时业务模拟变更也回滚。
- [ ] 测试日志、异常和 Entity `toString` 不包含 Markdown、Session、OAuth 或 provider subject。
- [ ] 运行定向测试。

Expected: 审计类型和 actor resolver 不存在。

### GREEN

- [ ] 精确映射 V5，Repository 只暴露 append，不提供 save-update、delete 或管理查询。
- [ ] actor resolver 只读取 Spring Security 已认证名称和 authority，输出本地 UUID；不导入 identity infrastructure principal。
- [ ] audit service 接收明确 action/resource/version，不接受任意 metadata map 或正文。
- [ ] 重跑测试。

Expected: actor、安全字段、事务回滚和 append 路径通过。

### REFACTOR

- [ ] 用一个枚举映射维护 action/resource 合法组合，不在各 Controller 写字符串。
- [ ] 运行 schema、审计与 Modulith 测试。

## Task 4: 文章写命令与领域状态

**覆盖行为：** A4、A5、A6

**Files:**

- Modify: `server/src/main/java/com/studystack/content/domain/Article.java`
- Modify: `server/src/main/java/com/studystack/content/domain/ArticleRepository.java`
- Modify: `server/src/main/java/com/studystack/content/domain/CategoryRepository.java`
- Modify: `server/src/main/java/com/studystack/content/domain/TagRepository.java`
- Create: `server/src/main/java/com/studystack/content/application/admin/ArticleAdminCommand.java`
- Create: `server/src/main/java/com/studystack/content/application/admin/ArticleAdminView.java`
- Create: `server/src/main/java/com/studystack/content/application/admin/ArticleAdminPage.java`
- Create: `server/src/main/java/com/studystack/content/application/admin/ArticleAdminService.java`
- Create: `server/src/test/java/com/studystack/content/application/admin/ArticleAdminServiceIntegrationTest.java`

### RED

- [ ] 测试管理分页、状态/标题/slug 搜索、创建、按 UUID 详情、全量更新和 version 返回。
- [ ] 测试 DRAFT slug 可变且可删除；PUBLISHED slug 锁定且不可删除；ARCHIVED 只读。
- [ ] 测试立即/未来发布、发布后修改内容、归档，以及重复发布、草稿归档、归档更新/发布的固定失败。
- [ ] 测试分类存在性、最多 10 个唯一标签、重复 slug、旧 version 和并发更新只成功一次。
- [ ] 运行定向测试。

Expected: 管理命令接口和服务不存在，测试编译失败。

### GREEN

- [ ] 在 content 内实现写服务，使用现有 Slug、字段规则、Repository 和 `@Version`；不复制 P2 可见性规则。
- [ ] 所有写请求使用完整替换语义，标签集合先验证再原子替换；失败不能留下部分关系。
- [ ] 将 JPA 乐观锁、唯一约束、资源不存在和领域状态错误转换为 content named interface 的稳定结果/异常。
- [ ] 重跑测试。

Expected: CRUD、状态、标签、未来发布和并发路径通过，P2 公开查询同步反映合法变更。

### REFACTOR

- [ ] 状态转换只存在于 Article；Service 只协调查询、验证关系和事务。
- [ ] 运行文章公开查询、Repository、管理命令和 Modulith 测试。

## Task 5: 文章管理 API 与审计编排

**覆盖行为：** A2、A3、A4、A5、A6、A13

**Files:**

- Create: `server/src/main/java/com/studystack/admin/application/AdminArticleUseCase.java`
- Create: `server/src/main/java/com/studystack/admin/web/article/AdminArticleController.java`
- Create: `server/src/main/java/com/studystack/admin/web/article/AdminArticleRequest.java`
- Create: `server/src/main/java/com/studystack/admin/web/article/AdminArticleResponse.java`
- Create: `server/src/main/java/com/studystack/admin/web/article/AdminArticlePageResponse.java`
- Create: `server/src/main/java/com/studystack/admin/web/article/AdminVersionRequest.java`
- Create: `server/src/main/java/com/studystack/admin/web/article/AdminPublishRequest.java`
- Create: `server/src/test/java/com/studystack/admin/web/article/AdminArticleApiIntegrationTest.java`

### RED

- [ ] 用 MockMvc 覆盖固定文章 API、201/Location、200、204、分页默认值与边界、非法 UUID/枚举/时间、未知分类标签和 404。
- [ ] 覆盖重复 slug 409、旧 version 409、非法状态 409、非草稿删除 409，并断言固定 code 和无正文泄漏。
- [ ] 每个成功 POST/PUT/DELETE/状态命令断言一条正确审计；失败和 GET 不写审计。
- [ ] 运行定向测试。

Expected: 管理 Controller 和 use case 不存在。

### GREEN

- [ ] Controller 只负责 DTO、Bean Validation、OpenAPI 和状态码；use case 在一个事务中调用 content command 与 audit service。
- [ ] 返回管理 DTO，不返回 content Entity、Repository 类型或原始异常。
- [ ] 所有更新和命令回传新 version；DELETE version 使用查询参数。
- [ ] 重跑测试。

Expected: HTTP、审计、事务和错误契约通过。

### REFACTOR

- [ ] 共用 version、分页和字段错误 DTO；不把文章业务规则搬进 admin。
- [ ] 运行管理 API、content command、公开文章和 auth 授权回归测试。

## Task 6: 分类与标签命令、API 和引用保护

**覆盖行为：** A2、A3、A4、A8、A13

**Files:**

- Modify: `server/src/main/java/com/studystack/content/domain/Category.java`
- Modify: `server/src/main/java/com/studystack/content/domain/Tag.java`
- Modify: `server/src/main/java/com/studystack/content/domain/CategoryRepository.java`
- Modify: `server/src/main/java/com/studystack/content/domain/TagRepository.java`
- Create: `server/src/main/resources/db/migration/V6__taxonomy_name_uniqueness.sql`
- Create: `server/src/main/java/com/studystack/content/application/admin/TaxonomyAdminCommand.java`
- Create: `server/src/main/java/com/studystack/content/application/admin/TaxonomyAdminView.java`
- Create: `server/src/main/java/com/studystack/content/application/admin/TaxonomyAdminService.java`
- Create: `server/src/main/java/com/studystack/admin/application/AdminTaxonomyUseCase.java`
- Create: `server/src/main/java/com/studystack/admin/web/taxonomy/AdminCategoryController.java`
- Create: `server/src/main/java/com/studystack/admin/web/taxonomy/AdminTagController.java`
- Create: `server/src/main/java/com/studystack/admin/web/taxonomy/AdminTaxonomyRequest.java`
- Create: `server/src/main/java/com/studystack/admin/web/taxonomy/AdminTaxonomyResponse.java`
- Create: `server/src/test/java/com/studystack/content/application/admin/TaxonomyAdminServiceIntegrationTest.java`
- Create: `server/src/test/java/com/studystack/admin/web/taxonomy/AdminTaxonomyApiIntegrationTest.java`
- Modify: `server/src/test/java/com/studystack/content/infrastructure/ContentSchemaIntegrationTest.java`

### RED

- [ ] 测试分类/标签稳定排序、创建、更新、version、引用数量和删除。
- [ ] 测试重复 name/slug、非法 slug、旧 version；任何状态文章仍引用时删除返回 `taxonomy_in_use`，不能级联移除关系。
- [ ] 测试每次成功变更恰好一条审计，冲突和校验失败无审计。
- [ ] 运行定向测试。

Expected: taxonomy 管理命令和 API 不存在。

### GREEN

- [ ] 在 content 模块实现引用计数和删除保护；唯一约束映射必须区分 name 与 slug，但对外统一稳定安全信息。
- [ ] 在 admin 中分别暴露 category/tag 固定路由并同事务审计。
- [ ] 重跑测试。

Expected: CRUD、引用保护、并发和审计通过，P2 taxonomy 公开计数无回归。

### REFACTOR

- [ ] category/tag 复用最小通用结构，不用资源类型字符串分支破坏静态类型。
- [ ] 运行 taxonomy 管理、文章关系、公开 taxonomy 和迁移测试。

## Task 7: 项目写命令与领域状态

**覆盖行为：** A4、A9、A10

**Files:**

- Modify: `server/src/main/java/com/studystack/portfolio/domain/Project.java`
- Modify: `server/src/main/java/com/studystack/portfolio/domain/ProjectRepository.java`
- Create: `server/src/main/java/com/studystack/portfolio/application/admin/ProjectAdminCommand.java`
- Create: `server/src/main/java/com/studystack/portfolio/application/admin/ProjectAdminView.java`
- Create: `server/src/main/java/com/studystack/portfolio/application/admin/ProjectAdminPage.java`
- Create: `server/src/main/java/com/studystack/portfolio/application/admin/ProjectAdminService.java`
- Create: `server/src/test/java/com/studystack/portfolio/application/admin/ProjectAdminServiceIntegrationTest.java`

### RED

- [ ] 测试管理分页/搜索/状态筛选、创建、详情、全量更新、精选、sortOrder、HTTPS/空 URL 和 version。
- [ ] 测试 DRAFT 删除与 slug 修改、立即/未来发布、发布后 slug 锁定、归档和 ARCHIVED 只读。
- [ ] 测试重复 slug、非法 URL、负 sortOrder、旧 version 和并发更新只成功一次。
- [ ] 运行定向测试。

Expected: 项目管理命令不存在。

### GREEN

- [ ] 在 portfolio 内实现 Project 写规则与事务，复用 P2 URL、Slug、状态和字段规则。
- [ ] 固定列表排序 `updatedAt DESC, id DESC`；公开列表继续使用 P2 发布排序，不因管理查询改变。
- [ ] 重跑测试。

Expected: 项目 CRUD、状态、URL、排序和并发通过。

### REFACTOR

- [ ] Project 拥有状态转换，Service 不复制 Article 代码形成跨模块继承；只共享已有稳定值对象。
- [ ] 运行 portfolio Repository、公开查询、管理命令和 Modulith 测试。

## Task 8: 项目管理 API 与审计编排

**覆盖行为：** A2、A3、A4、A9、A10、A13

**Files:**

- Create: `server/src/main/java/com/studystack/admin/application/AdminProjectUseCase.java`
- Create: `server/src/main/java/com/studystack/admin/web/portfolio/AdminProjectController.java`
- Create: `server/src/main/java/com/studystack/admin/web/portfolio/AdminProjectRequest.java`
- Create: `server/src/main/java/com/studystack/admin/web/portfolio/AdminProjectResponse.java`
- Create: `server/src/main/java/com/studystack/admin/web/portfolio/AdminProjectPageResponse.java`
- Create: `server/src/test/java/com/studystack/admin/web/portfolio/AdminProjectApiIntegrationTest.java`

### RED

- [ ] 覆盖固定项目路由、状态码、Location、分页、非法输入、404、duplicate_slug、stale_version、invalid_state_transition 和 draft_delete_only。
- [ ] 覆盖每种成功项目变更的审计动作和失败不审计。
- [ ] 断言响应不暴露内部 Entity、Repository、审计或未批准字段。
- [ ] 运行定向测试。

Expected: 项目 Controller 和 use case 不存在。

### GREEN

- [ ] 使用 portfolio named interface 和 admin audit 完成同事务编排；复用 Task 5 的 version/publish DTO，不复制冲突映射。
- [ ] 每次成功写入返回最新 version；删除返回 204。
- [ ] 重跑测试。

Expected: 项目 HTTP、审计、并发和错误契约通过。

### REFACTOR

- [ ] 文章与项目共享的只限 admin HTTP 基础类型，领域命令保持分离。
- [ ] 运行文章、项目、公开作品和授权回归测试。

## Task 9: 个人简介、技能与经历管理后端

**覆盖行为：** A2、A3、A4、A11、A12、A13

**Files:**

- Modify: `server/src/main/java/com/studystack/portfolio/domain/PortfolioProfile.java`
- Modify: `server/src/main/java/com/studystack/portfolio/domain/Skill.java`
- Modify: `server/src/main/java/com/studystack/portfolio/domain/Experience.java`
- Modify: `server/src/main/java/com/studystack/portfolio/domain/PortfolioProfileRepository.java`
- Modify: `server/src/main/java/com/studystack/portfolio/domain/SkillRepository.java`
- Modify: `server/src/main/java/com/studystack/portfolio/domain/ExperienceRepository.java`
- Create: `server/src/main/java/com/studystack/portfolio/application/admin/ProfileAdminService.java`
- Create: `server/src/main/java/com/studystack/portfolio/application/admin/SkillAdminService.java`
- Create: `server/src/main/java/com/studystack/portfolio/application/admin/ExperienceAdminService.java`
- Create: `server/src/main/java/com/studystack/portfolio/application/admin/PortfolioAdminViews.java`
- Create: `server/src/main/java/com/studystack/admin/application/AdminPortfolioUseCase.java`
- Create: `server/src/main/java/com/studystack/admin/web/portfolio/AdminProfileController.java`
- Create: `server/src/main/java/com/studystack/admin/web/portfolio/AdminSkillController.java`
- Create: `server/src/main/java/com/studystack/admin/web/portfolio/AdminExperienceController.java`
- Create: `server/src/test/java/com/studystack/portfolio/application/admin/PortfolioAdminServiceIntegrationTest.java`
- Create: `server/src/test/java/com/studystack/admin/web/portfolio/AdminPortfolioApiIntegrationTest.java`

### RED

- [ ] profile 测试不存在时 GET 404、`version=null` 首次 PUT 创建、带 version 更新、并发首次创建和旧 version 冲突。
- [ ] skill 测试列表、创建、更新、删除、visible、非负排序、重复业务值策略和并发。
- [ ] experience 测试列表、创建、更新、删除、visible、日期顺序、Markdown 长度、排序和并发。
- [ ] 所有成功写操作断言审计；404、校验和冲突不审计。
- [ ] 运行定向测试。

Expected: 管理服务和 API 不存在。

### GREEN

- [ ] 实现 profile upsert 的显式 create/update 分支；数据库单例冲突映射为 stale_version，不重试覆盖。
- [ ] skill/experience 使用 UUID 和 @Version，列表按 `sortOrder ASC, id ASC`；公开可见性仍由 P2 查询决定。
- [ ] admin use case 同事务写业务与审计，Controller 使用 Bean Validation 和管理 DTO。
- [ ] 重跑测试。

Expected: 单例、CRUD、日期、可见性、并发和审计通过。

### REFACTOR

- [ ] 集中非负排序和日期规则，不在 Controller、Entity、Zod 三处写不一致的枚举/映射。
- [ ] 运行 portfolio 管理、公开查询和 Modulith 测试。

## Task 10: Markdown 服务端预览

**覆盖行为：** A2、A7、A10、A13

**Files:**

- Create: `server/src/main/java/com/studystack/admin/application/AdminMarkdownPreview.java`
- Create: `server/src/main/java/com/studystack/admin/web/preview/AdminMarkdownPreviewController.java`
- Create: `server/src/main/java/com/studystack/admin/web/preview/AdminMarkdownPreviewRequest.java`
- Create: `server/src/main/java/com/studystack/admin/web/preview/AdminMarkdownPreviewResponse.java`
- Create: `server/src/test/java/com/studystack/admin/web/preview/AdminMarkdownPreviewApiIntegrationTest.java`

### RED

- [ ] 测试文章和项目两个固定 preview 路径、空 Markdown、最大长度边界、超长 400、ADMIN/CSRF 和响应 content-type。
- [ ] 使用 P2 XSS 载荷测试 script、事件属性、javascript/data URL、原始 HTML、图片和非法链接均不能形成可执行 HTML。
- [ ] 断言 preview 不写任何业务表和审计表。
- [ ] 运行定向测试。

Expected: preview API 不存在。

### GREEN

- [ ] 两个路由调用同一个 P2 `SafeMarkdownRenderer`，根据资源分别执行 200000/100000 字符上限；不创建第二个 Markdown parser 或 sanitizer。
- [ ] 响应只返回清洗后的 html，不回显 Markdown，不缓存预览内容。
- [ ] 重跑测试。

Expected: 安全预览、长度、权限和无副作用通过。

### REFACTOR

- [ ] preview controller 共享内部 handler，但 OpenAPI 仍显示两个明确业务路径。
- [ ] 运行 P2 Markdown 单测、公开内容 API 和 preview 集成测试。

## Task 11: 管理安全、错误与 OpenAPI 聚合契约

**覆盖行为：** A2、A4、A13

**Files:**

- Modify: `server/src/main/java/com/studystack/identity/infrastructure/security/SecurityConfiguration.java`
- Modify: `server/src/main/java/com/studystack/shared/openapi/OpenApiConfiguration.java`
- Modify: `server/src/main/java/com/studystack/shared/web/PublicApiExceptionHandler.java`
- Create: `server/src/main/java/com/studystack/admin/web/AdminApiExceptionHandler.java`
- Create: `server/src/test/java/com/studystack/admin/web/AdminAuthorizationIntegrationTest.java`
- Create: `server/src/test/java/com/studystack/admin/web/AdminErrorContractIntegrationTest.java`
- Create: `server/src/test/java/com/studystack/admin/web/AdminOpenApiIntegrationTest.java`

### RED

- [ ] 参数化全部管理路径：匿名 401、普通用户 403、管理员允许；每个写方法缺失/错误 CSRF 为 403，GET 不要求 CSRF。
- [ ] 对九个固定错误码测试 status、content-type、字段和信息清洗；非法枚举必须是 validation_failed，不能成为 500。
- [ ] OpenAPI 测试固定路径、方法、DTO、分页/version、201/204/400/401/403/404/409、sessionCookie security scheme 和写操作 CSRF header。
- [ ] 运行定向测试。

Expected: 至少错误、security response 或 OpenAPI 契约不完整。

### GREEN

- [ ] 保持 `/api/v1/admin/**` 单一服务端授权规则，配置 JSON 401/403，CSRF 失败使用安全 ProblemDetail；不为测试关闭 CSRF。
- [ ] 管理异常 handler 只处理已知管理/业务异常，未知异常交给全局 500 清洗，不解析数据库消息猜错误。
- [ ] 所有 Controller 使用 `io.swagger.v3.oas.annotations`，禁止 Swagger 2 `@Api`、`@ApiOperation`。
- [ ] 重跑测试。

Expected: 权限矩阵、CSRF、错误和 OpenAPI 一致。

### REFACTOR

- [ ] 消除 P2 与 P3 ProblemDetail 重复映射，保持公开 API 原有状态和字段不变。
- [ ] 运行 P1 auth、P2 public、P3 admin、OpenAPI prod 关闭和敏感数据策略测试。

## Task 12: 前端管理 runtime schema、Session 写客户端与 Query

**覆盖行为：** A4、A13、A14

**Files:**

- Modify: `web/src/features/auth/auth-client.ts`
- Modify: `web/src/features/auth/auth-client.spec.ts`
- Create: `web/src/shared/api/session-client.ts`
- Create: `web/src/shared/api/session-client.spec.ts`
- Create: `web/src/features/admin/admin-schema.ts`
- Create: `web/src/features/admin/admin-schema.spec.ts`
- Create: `web/src/features/admin/admin-client.ts`
- Create: `web/src/features/admin/admin-client.spec.ts`
- Create: `web/src/features/admin/admin-query.ts`
- Create: `web/src/features/admin/admin-query.spec.ts`

### RED

- [ ] session client 测试 CSRF 获取/内存缓存、credentials same-origin、JSON、204、401/403/409、Token 失效清理和一次安全重取；并发 mutation 不重复请求 Token。
- [ ] Zod 测试所有管理 DTO、精确枚举、非负 version/order、ISO 时间、UUID、HTTPS URL、分页、fieldErrors 和未知字段拒绝。
- [ ] client 测试每个固定路径/方法、query 编码、DELETE version、Location、preview 和错误 code；禁止绝对 API host。
- [ ] Query 测试列表/详情 key、mutation 后精确失效和 stale_version 不自动重试；服务端状态不进入 Pinia。
- [ ] 运行：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location web; pnpm test -- src/shared/api src/features/auth src/features/admin'
```

Expected: session/admin 数据层不存在。

### GREEN

- [ ] 从 P1 auth client 抽出通用同源 Session/CSRF 写请求，logout 复用且行为不变；不得把 Token 放入 localStorage、Pinia 或日志。
- [ ] 实现 strict Zod schema 和显式 map/filter，不使用 `as` 将未知状态转为 union。
- [ ] 创建 admin client、query options 和 mutations；409 返回可判别错误，mutation 默认 retry=0。
- [ ] 重跑测试。

Expected: CSRF、解析、路径、错误和缓存规则通过。

### REFACTOR

- [ ] 共享 page/problem 基础 schema，但文章、taxonomy、portfolio 保持独立业务映射。
- [ ] 运行 lint、typecheck、auth 与 admin 定向测试。

## Task 13: 管理路由、后台外壳与通用状态

**覆盖行为：** A2、A15

**Files:**

- Modify: `web/src/App.vue`
- Modify: `web/src/router/index.ts`
- Modify: `web/src/router/router.spec.ts`
- Modify: `web/src/views/AdminView.vue`
- Create: `web/src/layouts/AdminLayout.vue`
- Create: `web/src/views/admin/AdminDashboardView.vue`
- Create: `web/src/views/admin/AdminNotFoundView.vue`
- Create: `web/src/features/admin/components/AdminPageState.vue`
- Create: `web/src/features/admin/admin-routing.spec.ts`
- Create: `web/src/features/admin/admin-layout.spec.ts`

### RED

- [ ] 测试 9 组管理路由及嵌套路由直接访问；匿名记住 returnTo 后去登录，普通用户去 forbidden，ADMIN 进入目标页。
- [ ] 测试后台导航、当前项、退出入口、404、加载/空/错误/401/403；401 刷新 auth 后去登录，403 去 forbidden。
- [ ] 响应式结构测试长标题、窄屏导航和操作区不重叠、不横向溢出。
- [ ] 运行定向测试。

Expected: 嵌套管理路由和布局不存在。

### GREEN

- [ ] 将 P1 AdminView 升级为 AdminLayout 和子路由，复用既有 requiresAdmin 守卫，不创建第二套角色状态。
- [ ] 管理概览只展示资源入口和真实可取得信息，不生成虚构计数；通用状态组件不使用嵌套卡片。
- [ ] 重跑测试。

Expected: 权限导航、布局、状态和移动端结构通过。

### REFACTOR

- [ ] 路由元数据集中维护 breadcrumb/title，不在每页复制权限逻辑。
- [ ] 运行 router、auth view、admin layout、lint 和 typecheck。

## Task 14: 文章列表、编辑、草稿与安全预览页面

**覆盖行为：** A4、A5、A6、A7、A16

**Files:**

- Create: `web/src/features/admin/article/article-form-schema.ts`
- Create: `web/src/features/admin/article/article-form-schema.spec.ts`
- Create: `web/src/features/admin/article/article-draft-store.ts`
- Create: `web/src/features/admin/article/article-draft-store.spec.ts`
- Create: `web/src/features/admin/components/MarkdownEditor.vue`
- Create: `web/src/views/admin/AdminArticleListView.vue`
- Create: `web/src/views/admin/AdminArticleEditView.vue`
- Create: `web/src/features/admin/article/article-views.spec.ts`

### RED

- [ ] 表单 Zod 测试 slug、标题、摘要、Markdown、SEO、分类、最多 10 个唯一 tag、version 和非法枚举；不只做非空判断。
- [ ] 页面测试分页/筛选/搜索、空/错误、创建/更新/删除、立即/未来发布、归档、重复提交和字段错误聚焦。
- [ ] Markdown 编辑/预览切换调用服务端；危险响应必须被 safe HTML schema 拒绝，只有 `SafeMarkdownView` 使用 `v-html`。
- [ ] stale_version 测试保留输入并显示 reload；离开脏表单确认；草稿按资源和服务器 version 隔离、保存/丢弃后清理。
- [ ] 运行定向测试。

Expected: 文章管理页面、编辑器和草稿 store 不存在。

### GREEN

- [ ] 实现紧凑列表和全量编辑表单，状态相关按钮按服务器状态显示；最终失败仍由 API 决定。
- [ ] Pinia 只保存本会话未提交字段，禁止持久化 Token、用户或列表；服务器 version 变化时提示而不自动覆盖。
- [ ] preview 只调用 Task 10 API，并通过 P2 SafeMarkdownView 渲染。
- [ ] 重跑测试。

Expected: 完整文章管理、保存状态、并发提示和安全预览通过。

### REFACTOR

- [ ] 抽取通用 MarkdownEditor 和 mutation 状态，不把文章字段泛化成无法校验的动态表单。
- [ ] 运行 article admin、public content、lint、typecheck 和 Vitest。

## Task 15: 分类与标签管理页面

**覆盖行为：** A4、A8、A16

**Files:**

- Create: `web/src/features/admin/taxonomy/taxonomy-form-schema.ts`
- Create: `web/src/features/admin/taxonomy/taxonomy-form-schema.spec.ts`
- Create: `web/src/views/admin/AdminTaxonomyView.vue`
- Create: `web/src/features/admin/taxonomy/taxonomy-view.spec.ts`

### RED

- [ ] 测试 category/tag 两个分区的加载、空、创建、行内编辑、取消、保存中、删除确认和错误状态。
- [ ] Zod 测试 name、slug、version 和接口历史非法值；禁止通过 `as` 进入表单。
- [ ] 测试 duplicate_slug、stale_version、taxonomy_in_use：保留编辑值、显示准确操作，不误删或自动重试。
- [ ] 运行定向测试。

Expected: taxonomy 管理页面不存在。

### GREEN

- [ ] 使用稳定表格/列表和明确图标操作，删除前展示引用数量；服务端仍做最终引用检查。
- [ ] mutation 只失效对应 taxonomy 与文章表单选项 query，不清空无关缓存。
- [ ] 重跑测试。

Expected: category/tag CRUD、冲突和状态体验通过。

### REFACTOR

- [ ] category/tag 复用 typed row editor，不使用一个含任意字段的通用对象。
- [ ] 运行 taxonomy、article form、lint、typecheck 和 Vitest。

## Task 16: 项目列表、编辑与预览页面

**覆盖行为：** A4、A9、A10、A17

**Files:**

- Create: `web/src/features/admin/project/project-form-schema.ts`
- Create: `web/src/features/admin/project/project-form-schema.spec.ts`
- Create: `web/src/features/admin/project/project-draft-store.ts`
- Create: `web/src/features/admin/project/project-draft-store.spec.ts`
- Create: `web/src/views/admin/AdminProjectListView.vue`
- Create: `web/src/views/admin/AdminProjectEditView.vue`
- Create: `web/src/features/admin/project/project-views.spec.ts`

### RED

- [ ] 表单测试 slug、标题、摘要、Markdown、HTTPS URL、featured、非负 sortOrder、状态和 version。
- [ ] 页面测试列表/筛选/搜索、创建/更新/删除、发布/未来发布/归档、预览、加载/空/错误和重复提交。
- [ ] 测试 duplicate_slug、stale_version、非法状态、草稿隔离、离开确认和移动端长 URL/标题不溢出。
- [ ] 运行定向测试。

Expected: 项目管理页面不存在。

### GREEN

- [ ] 复用 MarkdownEditor、SafeMarkdownView、通用状态与冲突组件；项目表单保持独立 Zod 规则。
- [ ] Query 精确失效管理项目和相关 P2 public project/home query；不做全局 cache clear。
- [ ] 重跑测试。

Expected: 项目 CRUD、状态、并发、URL 和预览体验通过。

### REFACTOR

- [ ] 文章/项目共享行为通过小组件/composable 复用，不建立包含大量条件分支的万能编辑器。
- [ ] 运行 project admin、public portfolio、lint、typecheck 和 Vitest。

## Task 17: 简介、技能与经历管理页面

**覆盖行为：** A4、A11、A12、A17

**Files:**

- Create: `web/src/features/admin/portfolio/portfolio-form-schema.ts`
- Create: `web/src/features/admin/portfolio/portfolio-form-schema.spec.ts`
- Create: `web/src/views/admin/AdminProfileView.vue`
- Create: `web/src/views/admin/AdminSkillsView.vue`
- Create: `web/src/views/admin/AdminExperiencesView.vue`
- Create: `web/src/features/admin/portfolio/portfolio-views.spec.ts`

### RED

- [ ] profile 测试不存在时创建、已有时更新、Markdown 预览、脏表单、首次创建竞争和 stale_version。
- [ ] skills 测试列表、空、创建、编辑、删除、visible、sortOrder、错误和并发。
- [ ] experiences 测试日期区间、Markdown 预览、visible、排序、CRUD、错误和并发。
- [ ] Zod 拒绝非法日期、负排序、未知字段/枚举和未校验历史值；窄屏长组织/技能名不溢出。
- [ ] 运行定向测试。

Expected: 三组管理页面不存在。

### GREEN

- [ ] profile 使用明确创建/更新状态；skills/experiences 使用紧凑列表和独立表单，不把 sortOrder 隐藏成不可控客户端顺序。
- [ ] 复用 MarkdownEditor 和 mutation error 组件；成功写入精确失效 P2 about/home 相关 query。
- [ ] 重跑测试。

Expected: profile、skill、experience 全部管理路径和状态通过。

### REFACTOR

- [ ] 共享基础字段组件但保留三种业务 schema；不增加拖拽排序，P3 使用显式数字顺序。
- [ ] 运行 portfolio admin/public、lint、typecheck 和 Vitest。

## Task 18: 契约同步、浏览器验收与 P3 聚合

**覆盖行为：** A1-A18

**Files:**

- Modify: `web/src/shared/api/generated/openapi.d.ts`
- Modify: `deploy/tests/compose-config.test.mjs`
- Create: `web/e2e/admin-content.spec.ts`
- Create: `web/e2e/admin-portfolio.spec.ts`
- Create: `web/e2e/admin-security.spec.ts`
- Modify: `specs/PROGRESS.md`
- Modify: `docs/AI-CONTEXT-HANDOFF.md`
- Create: `specs/features/P3-ADMIN-001/SESSION_HANDOFF_AFTER_TASK18.md`

### RED

- [ ] 先生成/检查契约，确认 generated TypeScript 在同步前因新增管理 API 产生差异。
- [ ] Playwright 覆盖匿名/普通用户/管理员路由、文章完整流程、taxonomy 引用冲突、项目完整流程、profile/skill/experience、Markdown XSS、并发 409 和所有通用状态。
- [ ] Compose 集成使用本地测试身份或安全测试支持，不调用真实 GitHub；数据库测试数据只在测试生命周期存在并在结束清理。
- [ ] 先运行聚合命令并记录真实失败，不提前修改进度掩盖差异。

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd verify'
pwsh -NoLogo -NoProfile -Command 'Set-Location web; pnpm install --frozen-lockfile; pnpm lint; pnpm typecheck; pnpm test; pnpm contract:check; pnpm build'
pwsh -NoLogo -NoProfile -Command 'docker compose --env-file .env.example -f deploy/compose.yml config --quiet'
```

Expected: 契约或新增 E2E 在同步/实现前失败；已完成的 P0-P2 测试继续通过。

### GREEN

- [ ] 从运行中的 P3 OpenAPI 生成 TypeScript，不手工修改 generated 内容；重跑 `contract:check`。
- [ ] 运行 Compose、全部 Playwright 和清理流程；验证管理写入立即反映在 P2 公开页面，草稿/归档/未来发布仍不可公开。
- [ ] 更新进度文件，记录 A1-A18、V5、测试数量、授权/CSRF、并发、审计、Markdown 和浏览器证据；状态写 `P3 / awaiting-user-acceptance`，不得直接授权 P4。
- [ ] 交接文档只记录已验证事实、未运行项、当前分支和后续前置条件。

Expected: 后端、前端、契约、Compose、E2E 和清理均状态 0，证据与真实输出一致。

### REFACTOR

- [ ] 扫描旧 Swagger 注解、未受控 `v-html`、admin 直接 Repository/Entity 依赖、未校验强转、非管理阶段类型、硬编码 Token/secret 和宽泛异常吞噬。
- [ ] 执行最终检查：

```powershell
pwsh -NoLogo -NoProfile -Command 'rg -n -e "io\.swagger\.annotations" -e "springfox" -e "@Api\b" -e "@ApiOperation\b" server web specs'
pwsh -NoLogo -NoProfile -Command 'rg -n -e "v-html" web/src'
pwsh -NoLogo -NoProfile -Command 'rg -n -e "content\.domain" -e "content\.infrastructure" -e "portfolio\.domain" -e "portfolio\.infrastructure" server/src/main/java/com/studystack/admin'
pwsh -NoLogo -NoProfile -Command 'git diff --check; git status --short --branch'
```

Expected: 旧 Swagger 注解和跨模块内部依赖零命中；`v-html` 仅在 SafeMarkdownView；Git 只显示阶段相关文件。不得暂存、提交或推送。

## 4. 行为到任务与测试映射

| 行为 | Task | 主要测试 |
|---|---|---|
| A1 模块边界 | 1、18 | `StudyStackModulesTest`、`P3ScopeContractTest` |
| A2 ADMIN/CSRF | 3、5、6、8-11、13、18 | `AdminAuthorizationIntegrationTest`、security E2E |
| A3 审计 | 2、3、5、6、8、9、18 | schema、service、各 API integration |
| A4 并发错误 | 4-9、11、12、14-18 | command/API integration、Zod/View/E2E |
| A5 文章 CRUD | 4、5、12、14、18 | `ArticleAdminServiceIntegrationTest`、article API/View/E2E |
| A6 文章状态 | 4、5、14、18 | article command/API/View/E2E |
| A7 文章预览 | 10、14、18 | preview API、Markdown editor、XSS E2E |
| A8 分类标签 | 6、12、15、18 | taxonomy service/API/View/E2E |
| A9 项目 CRUD | 7、8、12、16、18 | project service/API/View/E2E |
| A10 项目状态预览 | 7、8、10、16、18 | project/preview integration、View/E2E |
| A11 简介 | 9、12、17、18 | portfolio admin API、profile View/E2E |
| A12 技能经历 | 9、12、17、18 | portfolio service/API/View/E2E |
| A13 校验契约 | 5、6、8-12、18 | error/OpenAPI、schema/client、contract check |
| A14 前端数据层 | 12、18 | session/admin schema/client/query Vitest |
| A15 后台路由 | 13、18 | router/layout Vitest、security E2E |
| A16 内容体验 | 14、15、18 | article/taxonomy View 与 E2E |
| A17 作品体验 | 16、17、18 | project/portfolio View 与 E2E |
| A18 聚合验收 | 1、11-18 | verify、pnpm、contract、Compose、Playwright |

## 5. 最终完成定义

P3 只有在以下条件同时满足时完成：

- 18 个 Task 均由用户逐项授权并完成指定验证，计划状态由用户验收决定。
- 匿名和普通用户不能读取或修改任何管理资源；所有写请求均有 CSRF 自动化证据。
- admin 模块不直接访问 content/portfolio 的 Entity、Repository 或 infrastructure。
- 文章、分类、标签、项目、简介、技能和经历管理功能完整，状态和删除规则与固定契约一致。
- 所有可变资源使用 version；并发写不会静默覆盖，前端不自动重试 stale_version。
- 每个成功变更同事务写一条最小 append-only 审计；失败、预览和未授权请求不写成功审计。
- Markdown 预览复用 P2 服务端白名单渲染，前端没有第二个解析器，恶意载荷无法执行。
- Bean Validation、领域规则、数据库约束、OpenAPI、generated TypeScript 和 Zod mapping 一致。
- 管理页面覆盖加载、空、错误、401、403、保存中、冲突、脏表单和移动端布局。
- 管理写入正确影响 P2 公开页面，同时草稿、归档和未来发布继续保持不可见。
- P0-P2 的 OAuth、Session、公开内容、SEO、Caddy、契约和测试无回归。
- `specs/PROGRESS.md` 为 `P3 / awaiting-user-acceptance`；未经用户验收不开始 P4。

## 6. 实施与验证记录

截至 2026-07-21，Task 1-18 已在 `codex/p3-admin` 实现。功能实现提交为 `2bdb613`，CI 回归与资料重复保存修复提交为 `4a24811`；PR #6 已合并到 `main`。P3 状态保持 `implemented-awaiting-user-acceptance`，不自动授权 P4。

### 6.1 CI 验证

PR #6 最新 push 与 pull request 两组 CI 均通过：

- backend：执行 `./mvnw -B -ntp verify`，状态 0。
- frontend：执行 frozen install、lint、typecheck、Vitest 和 production build，状态 0。
- contract：使用 backend 生成的 OpenAPI 执行 `pnpm contract:check`，状态 0。
- compose：Compose 配置检查和策略测试状态 0。
- e2e：构建完整 Compose 拓扑、执行 Playwright 并清理 volumes，状态 0。

本地在用户明确要求下没有运行完整 backend `verify`；该缺口由 GitHub CI 的 backend `verify` 成功结果补足。详细交接见 `SESSION_HANDOFF_AFTER_TASK18.md`。

### 6.2 CI 回归修复范围

- P2 范围断言只保护 P2 公开边界，允许 P3 admin 文件；Modulith 依赖继续限制为批准的 named interface。
- 无数据库 Web/OpenAPI 测试统一提供 admin service mock，避免加载 P3 Controller 后产生无关上下文失败。
- P3 OpenAPI 版本、CSRF ProblemDetail、V6 taxonomy name 唯一约束及测试夹具与新契约保持一致。
- 管理员资料响应进入表单时只映射允许字段，不把 `id`、时间戳等响应字段混入 strict schema，重复保存已由回归测试覆盖。
