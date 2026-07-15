---
task_id: P0-FOUNDATION-001
title: StudyStack 工程底座
phase: P0
status: draft
created: 2026-07-14
updated: 2026-07-14
design_refs:
  - C:/softWare/project/workDoc/java-personal-website-design.md
  - docs/AI-PROJECT-DELIVERY-PLAN.md
---

# P0-FOUNDATION-001: StudyStack 工程底座 Product Spec

## 1. 摘要

P0 建立一个无业务功能、可重复构建和验证的 StudyStack 工程底座。开发者能够分别构建 Java 后端和 Vue 前端，使用 PostgreSQL 与 Flyway 启动本地环境，生成 OpenAPI 3 契约，并通过 Caddy 验证静态文件、SPA fallback 与反向代理边界；运维人员能够通过健康端点、Docker Compose 配置和 CI 结果判断底座是否可发布到后续阶段。

P0 只建立架构边界和验证入口。登录、文章、作品、评论、上传及后台业务必须等待后续阶段的规格获批后实现。

## 2. 用户与目标

### 2.1 开发者

> 作为开发者，我希望在一台具备 Java 21、pnpm 和 Docker 的机器上使用仓库自带命令完成构建、测试、类型检查与契约检查，以便在实现业务前确认工程基础一致。

### 2.2 运维人员

> 作为运维人员，我希望通过 Compose 配置、健康检查和 Caddy 路由结果判断应用、数据库及静态站点是否正确协作，并确保示例配置不包含真实密钥。

## 3. 行为契约

以下编号是稳定验收标识。后续实现不得重用或改变既有编号的含义；需要新增行为时追加新编号。

### B1 - Java 21 单 Maven 工程

开发者在 `server` 目录使用 Maven Wrapper 执行验证时，系统必须以 Java 21 编译一个 Spring Boot 3.x 单模块 Maven 工程，不得形成 Maven 聚合工程或多个可部署后端应用。Java 主版本不是 21、依赖使用浮动版本范围或构建插件失败时，命令必须以非零状态结束，并输出可定位的 Maven 失败信息。

### B2 - Spring Modulith 包边界

后端必须识别 `admin`、`comment`、`content`、`identity`、`media`、`portfolio` 和 `shared` 七个 Spring Modulith 应用模块。P0 中前六个业务模块只声明包边界，`shared` 只容纳稳定基础配置；不得创建业务实体、Repository、应用服务或业务 Controller。任何未声明的跨模块依赖必须使模块验证测试失败。

### B3 - PostgreSQL 与 Flyway 启动契约

后端集成测试必须通过 Testcontainers 启动 PostgreSQL，并由 Flyway 成功执行版本化 baseline 迁移。Hibernate 只能校验 schema，不得创建或修改表结构。PostgreSQL 不可连接、凭据错误、迁移校验和不一致或迁移失败时，应用 readiness 必须为失败状态，启动或验证命令必须以非零状态结束；系统不得静默切换到内存数据库。

### B4 - Actuator 健康契约

运行中的后端必须提供 `/actuator/health/liveness` 和 `/actuator/health/readiness`。JVM 正常且应用已启动时 liveness 返回 `UP`；数据库可连接且迁移完成时 readiness 返回 `UP`。数据库不可用时 readiness 返回 HTTP 503 和 `DOWN`，不得返回伪成功。P0 不通过 Caddy 匿名代理 Actuator 端点。

### B5 - 开发测试环境的 OpenAPI 3

在 `dev` 和 `test` profile 中，后端必须生成 `/v3/api-docs` 并提供 Swagger UI。文档必须是有效 OpenAPI 3，包含固定的 StudyStack 标题和版本信息。新增接口文档只能使用 `io.swagger.v3.oas.annotations` 下的 `@Tag`、`@Operation`、`@Parameter`、`@Schema`、`@ApiResponse`、`@ApiResponses`、`@SecurityRequirement`、`@OpenAPIDefinition` 和 `@SecurityScheme`；禁止使用旧版 `@Api`、`@ApiOperation`、`@ApiParam`、`@ApiModel` 和 `@ApiModelProperty`。文档生成失败或出现禁用注解时，契约验证必须失败。

### B6 - 生产环境默认关闭 API 文档

在 `prod` profile 中，匿名请求 `/v3/api-docs`、`/v3/api-docs/**`、`/swagger-ui.html` 和 `/swagger-ui/**` 必须得到 HTTP 404。P0 不实现管理员认证，因此生产 profile 不提供例外开放路径；后续阶段如需受控开放，必须先更新身份与部署规格。

### B7 - Vue 3 前端工程入口

开发者必须能够使用 pnpm 完成前端安装、lint、类型检查、单元测试和生产构建。前端采用 Vue 3、TypeScript、Vite、Vue Router、TanStack Query for Vue、Pinia 和 Zod；应用启动时必须安装 Router、QueryClient 和 Pinia。根路由与一个深层 SPA 路由可渲染最小 StudyStack 壳，未知路由显示明确的未找到状态。依赖未安装、类型错误、测试失败或构建失败时，对应命令必须以非零状态结束。

### B8 - 环境变量与密钥边界

后端数据库连接和 profile、前端公开 API 基础路径以及 Compose 参数必须通过环境变量注入，并提供只含非敏感示例值的 `.env.example` 文件。前端只允许读取以 `VITE_` 开头且可公开的配置；数据库密码、OAuth 密钥、管理员标识和其他服务端秘密不得进入前端配置或构建产物。必需变量缺失、URL 格式非法或生产环境使用示例密码时，配置检查必须给出具体变量名并失败。

### B9 - Caddy 静态文件、SPA fallback 与代理边界

Caddy 必须直接提供 Vue 生产静态文件。请求 `/`、已声明的深层前端路由或未知的非后端页面路径时，Caddy 必须回退到 `index.html`，浏览器刷新不得返回 Caddy 404。请求 `/api`、`/api/**`、`/oauth2`、`/oauth2/**`、`/login/oauth2` 或 `/login/oauth2/**` 时，Caddy 必须代理到 Spring Boot，绝不能返回 SPA HTML。P0 未实现这些业务端点，因此相应代理请求允许返回后端 404，但响应不得伪装成前端页面。请求 `/actuator` 或 `/actuator/**` 时，Caddy 必须直接返回 404，不代理到后端，也不进入 SPA fallback。

### B10 - Docker Compose 本地运行边界

开发者必须能够通过一个 Compose 文件解析并启动 `postgres`、`app` 和 `caddy` 三个服务。PostgreSQL 数据使用命名卷；`app` 在 PostgreSQL 健康且迁移可执行后变为 ready；`caddy` 只依赖可访问的后端和已构建前端静态资源。Compose 缺少必需环境变量、端口冲突、数据库不健康或应用不 ready 时，启动或验收命令必须明确失败。P0 不定义公网域名、生产证书、远程部署或自动回滚。

### B11 - OpenAPI 前后端契约同步

后端验证必须输出确定性的 OpenAPI JSON；前端必须能从该文件生成 TypeScript 契约类型，并提供不改写已提交类型文件的同步检查入口。后端契约变化但生成类型未同步、OpenAPI 文件无效或生成结果不确定时，契约检查必须以非零状态结束并指出差异文件。

### B12 - CI 质量门禁

CI 必须在干净环境中执行后端 `verify`、前端 lint、类型检查、单元测试、生产构建、OpenAPI 契约同步检查、Compose 配置校验和 Caddy 路由 E2E。任一门禁失败时工作流整体失败，不得执行部署、推送镜像或创建远程资源。所有门禁成功时，CI 才能将 P0 标记为通过。

## 4. 失败路径总表

| 失败类别 | 可观察结果 | 禁止的降级行为 |
|---|---|---|
| Java 版本不符 | Maven Enforcer 在编译前失败并显示需要 Java 21 | 使用其他 JDK 继续编译 |
| 模块越界 | Spring Modulith 验证测试列出依赖违规 | 忽略违规或只记录警告 |
| PostgreSQL 不可用 | readiness 为 503，集成验证失败 | 切换 H2 或跳过 Flyway |
| Flyway 迁移异常 | 应用启动失败并保留 Flyway 错误原因 | 由 Hibernate 自动修表 |
| OpenAPI 生成异常 | 后端契约测试或前端契约检查失败 | 使用手写类型掩盖差异 |
| 生产文档端点被打开 | production profile 测试失败 | 依赖 Caddy 隐藏仍可直连的端点 |
| 前端外部配置非法 | Zod 校验报告具体变量并阻止启动或构建 | 回退到含秘密或错误域名的默认值 |
| Caddy 路由误判 | E2E 指出具体路径返回了错误内容类型或上游 | 将后端路径交给 SPA fallback |
| CI 子检查失败 | 工作流失败并保留对应 job 日志 | 继续部署或发布镜像 |

## 5. 验收标准

- [ ] B1：Java 21 Maven Wrapper 全量验证通过，且 effective POM 只有一个 Maven project。
- [ ] B2：七个模块被 Spring Modulith 精确识别，依赖验证无违规，业务包内没有 P0 业务实现。
- [ ] B3：PostgreSQL Testcontainers 启动、Flyway baseline 与校验通过，Hibernate DDL 模式为 `validate`。
- [ ] B4：liveness/readiness 正常路径通过，数据库失败时 readiness 返回 503。
- [ ] B5：`dev`、`test` 可获得有效 `/v3/api-docs` 与 Swagger UI，源码注解策略检查通过。
- [ ] B6：`prod` 下四类 API 文档路径均不向匿名请求开放。
- [ ] B7：前端 lint、类型检查、Vitest 和 Vite build 通过，必要插件已安装并可渲染根路由与深层路由。
- [ ] B8：示例配置扫描与运行时配置校验通过，不存在真实密钥或服务端秘密进入 Vite 的路径。
- [ ] B9：Caddy 静态资源、SPA fallback 和三组后端代理前缀的 E2E 断言全部通过。
- [ ] B10：Compose 配置解析通过且仅定义三个运行服务，PostgreSQL 使用命名卷和健康依赖。
- [ ] B11：后端 OpenAPI 产物与前端生成类型一致，人工改动任一侧可使同步检查失败。
- [ ] B12：CI 包含并强制执行所有 P0 门禁，不含部署、镜像推送或远程资源创建步骤。
- [ ] 只读参考项目在结束时执行 `git status --short --branch` 与开始时结果一致：`## codex/new-feature-development`，无文件状态行。

## 6. 非目标

P0 明确不包含以下内容：

- GitHub OAuth、Session、CSRF、用户、角色、管理员白名单或任何登录流程。
- 文章、分类、标签、作品、个人经历、评论、审核、Markdown 业务处理或后台页面。
- 文件上传、媒体元数据、本地媒体存储或对象存储接口实现。
- 业务 REST API、业务 DTO、业务数据库表、业务实体、Repository 或领域事件。
- Redis、Elasticsearch、消息队列、MinIO、微服务、API 网关、服务注册中心或 Kubernetes。
- 公网域名、HTTPS 证书、生产发布、备份、恢复、监控告警和自动回滚。
- Nuxt、服务端渲染、Node.js 生产运行时或移动应用。

## 7. 范围门禁

三份 P0 规格经用户明确确认前，不得创建本规格中描述的工程文件。P0 实现完成且 B1-B12 全部提供新鲜验证证据前，不得开始 P1 或创建任何业务功能。
