---
task_id: P3-ADMIN-001
title: StudyStack 管理后台产品规格
phase: P3
status: awaiting-user-acceptance
created: 2026-07-21
updated: 2026-07-22
design_refs:
  - C:/softWare/project/workDoc/java-personal-website-design.md
  - docs/AI-PROJECT-DELIVERY-PLAN.md
  - specs/features/P2-CONTENT-PORTFOLIO-001/PRODUCT.md
implementation_ref: specs/features/P3-ADMIN-001/IMPLEMENTATION_PLAN.md
migration_ref: specs/features/P3-ADMIN-001/ELEMENT_PLUS_MIGRATION_PLAN.md
---

# P3 Admin Product Specification

## 1. Summary

P3 为 StudyStack 提供只面向管理员的内容与作品集管理后台。管理员可以管理文章、分类、标签、项目、个人简介、技能和经历，并通过服务端安全预览 Markdown、发布或归档内容。所有管理写入继续复用 P2 的领域与公开读取规则，因此管理结果会按既有可见性、发布时间和安全渲染规则反映到公开站点。

本阶段强调安全写入、明确状态、并发保护和可追踪性。前端权限守卫只改善导航体验，最终授权、CSRF、输入校验、状态流转、乐观并发和审计均由服务端执行。

## 2. Actors And Goals

| 角色 | 目标 |
|---|---|
| 管理员 | 在统一后台创建、编辑、发布、归档和删除允许操作的内容与作品数据 |
| 内容维护者 | 管理文章、分类和标签，安全预览 Markdown，并控制立即或未来发布 |
| 作品维护者 | 管理项目、简介、技能和经历，控制排序、精选状态和公开可见性 |
| 访客与普通用户 | 不能读取或修改管理资源，公开页面仍只展示符合 P2 可见性规则的数据 |
| 运维与审计人员 | 每次成功变更都有最小、不可篡改的审计记录，且不记录正文或凭据 |
| 后续开发者 | 通过稳定产品行为、接口语义和测试映射继续维护 P3，不破坏 P0-P2 契约 |

## 3. Stable Behavior Contracts

### A1 Module And Scope Boundary

- `admin` 负责管理 HTTP、用例编排和审计，不直接访问 content 或 portfolio 的 Entity、Repository 和 infrastructure。
- content 拥有文章、分类和标签的写规则；portfolio 拥有项目、简介、技能和经历的写规则。
- P3 不改变 P2 公开读取契约，不提前实现评论、媒体、搜索或部署阶段能力。

### A2 Administrator Security

- `/api/v1/admin/**` 只允许服务端动态计算为 `ADMIN` 的已登录用户访问。
- 匿名请求返回 401，普通登录用户返回 403；管理请求不会重定向到 OAuth provider。
- POST、PUT 和 DELETE 必须携带有效 CSRF token；前端路由守卫不能替代服务端授权。

### A3 Transactional Audit

- 每个成功数据变更在同一事务写入一条 append-only 审计记录；业务写入或审计任一失败时整体回滚。
- 审计只记录管理员本地用户、动作、资源类型、资源 ID、变更后 version 和 UTC 时间。
- 校验失败、未授权、CSRF 失败、并发冲突和 Markdown 预览不写成功审计。

### A4 Concurrency And Error Contract

- 所有可变资源响应包含非负 `version`；更新和状态命令提交最后读取的 version，删除通过必填查询参数提交 version。
- 版本不匹配返回 409 `stale_version`，不覆盖服务端数据，也不返回服务端最新正文。
- 前端保留用户输入并允许重新加载或取消，不自动重试并发写入。
- 管理错误使用 `application/problem+json` 和稳定 `code`；字段校验可附带 `fieldErrors`。

### A5 Article CRUD

- 管理员可分页、按精确状态筛选或按标题/slug 查询文章，并创建、读取和更新文章。
- 文章包含 slug、标题、摘要、Markdown 正文、可空分类、最多 10 个不重复标签和可空 SEO 字段。
- 列表不返回 Markdown 正文；只有 DRAFT 可以硬删除。

### A6 Article Lifecycle

- 文章状态只允许 `DRAFT -> PUBLISHED -> ARCHIVED`，不提供撤回、恢复草稿或重新发布归档内容。
- 发布可立即生效或指定未来 UTC 时间；未来发布文章在时间到达前继续对公开 API、页面和 sitemap 隐藏。
- 首次发布后 slug 不可修改；ARCHIVED 文章只读。

### A7 Article Markdown Preview

- 文章预览调用服务端与 P2 相同的 Markdown 白名单渲染器，不在浏览器实现第二套 parser。
- 预览不持久化、不写审计；危险 HTML、脚本、图片、事件属性和不安全 URL 不得执行或透传。

### A8 Categories And Tags

- 管理员可分别管理分类和标签的列表、创建、更新与删除。
- 名称和 slug 在各自资源类型内唯一；重复值返回 409 `duplicate_slug` 或字段校验错误。
- 被文章引用的分类或标签不能删除，返回 409 `taxonomy_in_use`。

### A9 Project CRUD

- 管理员可分页管理项目，并创建、读取、更新和删除 DRAFT 项目。
- 项目包含 slug、标题、摘要、Markdown 描述、可空 HTTPS 项目/仓库链接、featured 和非负 sortOrder。
- 列表不返回 Markdown 描述；所有更新和删除受 version 保护。

### A10 Project Lifecycle And Preview

- 项目沿用 `DRAFT -> PUBLISHED -> ARCHIVED`，支持立即或未来发布以及服务端 Markdown 预览。
- 首次发布后 slug 不可修改；只有 DRAFT 可删除，ARCHIVED 项目只读。
- P2 公开页面只展示发布时间已到的 PUBLISHED 项目。

### A11 Profile Singleton

- 个人简介是单例资源；首次保存使用 `version=null` 创建，后续保存必须提交当前非负 version。
- 管理字段为 displayName、headline、bioMarkdown 和可空 seoDescription。
- API 响应进入表单时只映射允许字段，不能把 id 或审计时间混入 strict 表单导致后续保存失败。

### A12 Skills And Experiences

- 管理员可创建、更新和删除技能与经历；两类资源使用 visible 和非负 sortOrder 控制公开展示。
- 技能包含名称、分类和可空摘要；经历包含组织、角色、日期范围和 Markdown 摘要。
- 经历结束日期不得早于开始日期；所有已有资源写入均受 version 保护。

### A13 Validation And Contract Consistency

- Bean Validation、领域规则、数据库约束、OpenAPI、generated TypeScript 和前端 strict schema 必须表达一致契约。
- slug、Markdown、URL、枚举、日期、长度、分页和标签数量在入口校验，并由领域或数据库提供第二道防线。
- 管理表单始终显示必填、长度、格式、数量、URL、日期和数值范围提示；字段失焦后显示错误，提交时显示全部错误，修正后实时清除。
- 前端校验失败不得发送管理写请求；后端 `fieldErrors` 必须显示在对应字段，并聚焦第一个失败字段。
- 未知数据库错误不能通过模糊字符串匹配伪装成已知业务错误。

### A14 Frontend Runtime And Session Client

- 所有管理响应先通过 strict Zod schema，再经显式 map/filter 进入表单或 TypeScript union，禁止直接 `as` 强转未知 API 值。
- 管理写客户端使用同源 Session 和 P1 CSRF 能力；TanStack Query 管理服务端数据及精确失效。
- Pinia 只保存当前浏览器会话中的未提交文章或项目草稿，不复制用户、角色或服务端列表。
- 后端成功响应契约保持实体、Location 或 `204` 不变；前端只在响应成功解析后显示与添加、保存、删除、发布、归档或退出登录对应的中文操作结果。

### A15 Admin Shell And Routing

- 管理后台实际路由为 `/admin`、`/admin/articles`、`/admin/articles/new`、`/admin/articles/:id`、`/admin/categories`、`/admin/tags` 和 `/admin/portfolio/**`。
- 管理后台统一采用 Element Plus 的表单、按钮、表格、分页、菜单、消息和确认交互；公共页面继续使用现有自定义视觉体系。
- Element Plus 主题变量和控件迁移只作用于管理后台；公共退出可复用全局消息服务，但不得改变公共路由的布局、认证入口、内容展示或 SEO 行为。
- `/admin/portfolio/**` 包含 projects、profile、skills 和 experiences 页面；未知后台路径显示后台 404。
- 匿名用户转到登录页，普通用户转到 forbidden；导航与退出沿用 P1 认证契约。

### A16 Content Management Experience

- 文章、分类和标签页面覆盖 loading、empty、error、保存中、校验失败、操作失败和并发冲突。
- 文章列表更新时间使用浏览器本地时区显示到分钟，保留标准 Instant 供机器读取和精确查看。
- 文章、分类和标签创建/编辑使用同一字段提示、必填标记、客户端合法性校验和无障碍错误关联。
- 文章、分类和标签的所有写操作均显示明确成功提示，失败时同时保留原有字段级或表单级诊断，并显示对应失败提示。
- Markdown 编辑器提供编辑/预览模式；按钮防止重复提交，脏表单离开前确认。
- 成功保存或明确丢弃后清理本地草稿，资源 version 变化时不得自动覆盖服务端数据。
- 新增文章成功后使用明确的“继续添加”和“返回列表”操作；关闭对话框保留当前页面，不把关闭误判为返回列表。
- 删除、发布、归档和离开脏表单等破坏性操作使用统一确认框，并明确区分确认、取消和关闭结果。

### A17 Portfolio Management Experience

- 项目、简介、技能和经历页面提供完整写入流程、明确主操作和紧凑可扫描布局。
- 项目、简介、技能和经历的添加、保存、删除、发布与归档响应均通过 Element Plus 全局消息反馈，通知不得早于后端成功响应。
- 项目 URL、可选文本和结束日期清空时归一化为 `null`；经历日期范围错误绑定到结束日期字段。
- 页面在桌面和移动端不得产生内容重叠或横向溢出；空状态不展示虚构统计或演示数据。
- 项目预览和文章预览遵循相同服务端安全边界。

### A18 Aggregate Acceptance

- 浏览器验收覆盖匿名、普通用户和管理员路由，文章/项目状态流、taxonomy 冲突、Markdown XSS、并发冲突和 portfolio 写入。
- 管理写入必须正确影响 P2 公开读取，同时继续隐藏草稿、归档和未来发布内容。
- P0-P2 的 OAuth、Session、公开内容、SEO、Caddy、OpenAPI、Compose 和测试不得回归。

## 4. Failure Contract

| 场景 | 对外行为 |
|---|---|
| 匿名访问管理 API | 401 `application/problem+json` |
| 普通用户访问管理 API | 403 `code=forbidden` |
| 管理写请求缺失或携带无效 CSRF | 403 `code=csrf_failed`，不产生业务写入或审计 |
| 请求体、分页、枚举、日期、URL 或数量非法 | 400 `code=validation_failed`，可包含 `fieldErrors` |
| 资源不存在 | 404 `code=not_found` |
| slug 或 taxonomy 唯一值冲突 | 409 `code=duplicate_slug` |
| 客户端 version 已过期 | 409 `code=stale_version`，保留前端输入 |
| 非法发布、归档或只读资源修改 | 409 `code=invalid_state_transition` |
| 删除非 DRAFT 文章或项目 | 409 `code=draft_delete_only` |
| 删除仍被文章引用的分类或标签 | 409 `code=taxonomy_in_use` |
| 管理响应不符合 strict runtime schema | 前端拒绝响应并进入稳定错误状态 |
| Markdown 预览包含危险载荷 | 返回清洗后的安全 HTML，不执行危险内容 |

错误响应不得包含堆栈、SQL、Markdown 正文、实体快照、GitHub ID、Session ID、CSRF/OAuth token 或完整安全异常。

## 5. Acceptance Criteria

- A1-A18 均有自动化证据，主要映射见 `TECH.md`。
- 匿名、普通用户、管理员与 CSRF 的授权矩阵通过后端集成测试和浏览器测试。
- 文章、taxonomy、项目、简介、技能和经历的 CRUD、状态、删除、校验与并发规则通过。
- 每个成功变更同事务产生最小 append-only 审计，失败路径不产生成功审计。
- 管理 API、ProblemDetail、OpenAPI、generated TypeScript、Zod mapping 和表单 schema 保持同步。
- 所有管理表单在发送请求前执行合法性校验，并提供常驻规则提示、字段错误和无障碍属性。
- Element Plus 自动导入、后台主题、确认框和操作消息通过单元测试，所有管理页面的核心写入与错误路径通过 View 测试。
- 服务端 Markdown 预览和前端唯一安全渲染路径通过 XSS 回归测试。
- 管理路由、页面状态、草稿保护、重复保存和响应式布局通过 Vitest 与 Playwright。
- P0-P2 后端、前端、契约、Compose 和公开读取回归检查通过。

## 6. Non-Goals

- 不提供审计查询 API、审计管理页面、审计记录修改或删除能力。
- 不提供富文本编辑器、媒体上传、图片管理或附件能力；媒体属于 P5。
- 不提供撤回发布、恢复草稿、重新发布归档内容、版本历史或内容回滚。
- 不提供批量编辑、批量删除、导入导出、全文搜索、统计报表或多管理员协作锁。
- 不提供用户管理、角色编辑、评论审核或反滥用能力；评论属于 P4。
- 不执行生产部署、监控、备份或性能加固；这些属于后续阶段。

## 7. Scope Gate

P3 Task 1-18 已实现并通过既有 CI，当前状态保持 `awaiting-user-acceptance`。只有用户明确验收 P3 后才能更新阶段状态或开始 P4；本规格不构成后续阶段授权。
