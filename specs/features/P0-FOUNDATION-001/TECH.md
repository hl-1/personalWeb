---
task_id: P0-FOUNDATION-001
title: StudyStack 工程底座技术规格
phase: P0
status: draft
created: 2026-07-14
updated: 2026-07-14
product_ref: specs/features/P0-FOUNDATION-001/PRODUCT.md
design_refs:
  - C:/softWare/project/workDoc/java-personal-website-design.md
  - docs/AI-PROJECT-DELIVERY-PLAN.md
---

# P0-FOUNDATION-001: StudyStack 工程底座 Tech Spec

## 1. 技术目标与约束

P0 交付一个前后端分离、运行时单体、数据库唯一持久化的最小工程。后端位于 `server/`，是一个 Java 21、Spring Boot 3.x 单模块 Maven 工程；前端位于 `web/`，是一个由 pnpm 管理的 Vue 3 SPA；`deploy/` 提供本地 Compose 和 Caddy 路由基线；`.github/workflows/ci.yml` 只执行质量门禁。

Spring Boot 3.x 的具体补丁版本在实施 Task 1 时从仍受维护的 3.x 版本中选定，并作为常量写入 `server/pom.xml` 与 Maven Wrapper 配置。不得使用版本范围、`RELEASE`、`LATEST` 或 snapshot。前端依赖同样写入 `package.json` 并由 `pnpm-lock.yaml` 固定，不允许 CI 漂移解析版本。

P0 不提供业务端点和业务表。包目录的存在只表达后续模块所有权，不授权提前实现业务。

## 2. 仓库结构

```text
studystack/
├── .env.example
├── .gitignore
├── .github/
│   └── workflows/
│       └── ci.yml
├── deploy/
│   ├── Caddyfile
│   ├── compose.yml
│   └── tests/
│       └── compose-config.test.mjs
├── server/
│   ├── .mvn/wrapper/maven-wrapper.properties
│   ├── .dockerignore
│   ├── Dockerfile
│   ├── mvnw
│   ├── mvnw.cmd
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/studystack/
│       │   │   ├── StudyStackApplication.java
│       │   │   ├── admin/package-info.java
│       │   │   ├── comment/package-info.java
│       │   │   ├── content/package-info.java
│       │   │   ├── identity/package-info.java
│       │   │   ├── media/package-info.java
│       │   │   ├── portfolio/package-info.java
│       │   │   └── shared/
│       │   │       ├── package-info.java
│       │   │       └── openapi/OpenApiConfiguration.java
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-dev.yml
│       │       ├── application-prod.yml
│       │       └── db/migration/V1__baseline.sql
│       └── test/
│           ├── java/com/studystack/foundation/
│           │   ├── ActuatorIntegrationTest.java
│           │   ├── BuildRuntimeContractTest.java
│           │   ├── OpenApiAnnotationPolicyTest.java
│           │   ├── OpenApiDevelopmentIntegrationTest.java
│           │   ├── OpenApiProductionIntegrationTest.java
│           │   ├── PostgresFlywayIntegrationTest.java
│           │   └── StudyStackModulesTest.java
│           └── resources/application-test.yml
├── web/
│   ├── .env.example
│   ├── .dockerignore
│   ├── Dockerfile
│   ├── eslint.config.js
│   ├── index.html
│   ├── package.json
│   ├── pnpm-lock.yaml
│   ├── playwright.config.ts
│   ├── tsconfig.app.json
│   ├── tsconfig.json
│   ├── tsconfig.node.json
│   ├── vite.config.ts
│   ├── vitest.config.ts
│   ├── e2e/foundation-routing.spec.ts
│   ├── scripts/assert-contract-sync.mjs
│   └── src/
│       ├── App.vue
│       ├── main.ts
│       ├── app/create-app.ts
│       ├── app/create-app.spec.ts
│       ├── config/env.ts
│       ├── config/env.spec.ts
│       ├── router/index.ts
│       ├── router/router.spec.ts
│       ├── shared/api/generated/openapi.d.ts
│       ├── tests/ci-workflow.spec.ts
│       ├── tests/config-policy.spec.ts
│       ├── tests/contract-sync.spec.ts
│       └── views/
│           ├── HomeView.vue
│           ├── FoundationView.vue
│           └── NotFoundView.vue
└── specs/features/P0-FOUNDATION-001/
    ├── PRODUCT.md
    ├── TECH.md
    └── IMPLEMENTATION_PLAN.md
```

`server` 下只能有一个 `pom.xml`，且该 POM 不声明 `<modules>`。`web` 不是 Maven module。生产构建时 Node.js 只用于生成静态文件，不进入运行容器。

## 3. 后端边界

### 3.1 Spring Boot

Spring Boot 负责进程生命周期、Spring MVC、外部化配置、Actuator、JPA 启动校验和 springdoc 集成。P0 依赖边界如下：

- 运行依赖：Spring Boot Web、Actuator、Validation、Data JPA、PostgreSQL driver、Flyway、`flyway-database-postgresql`、Spring Modulith core、springdoc OpenAPI UI starter。
- 测试依赖：Spring Boot Test、Spring Modulith test、Testcontainers JUnit Jupiter、Testcontainers PostgreSQL、Spring Boot Testcontainers。
- P0 不引入 Spring Security。生产 API 文档通过 springdoc profile 配置彻底禁用，而不是依赖尚不存在的认证规则。
- P0 不创建业务 Controller。Spring Boot 的错误响应和后续统一错误契约不在本阶段固化。

Maven Enforcer 在 `validate` 阶段要求 Java 21，并拒绝 Maven 版本范围和不收敛依赖。Surefire 执行单元及 Spring 集成测试，`verify` 是后端唯一全量入口。

### 3.2 Spring Modulith

`com.studystack` 是应用根包。七个直接子包是应用模块：

| 模块 | P0 内容 | 允许依赖 | 后续所有权 |
|---|---|---|---|
| `shared` | OpenAPI 集中配置与稳定基础约定 | 无业务模块 | 通用错误、审计字段、时间抽象 |
| `identity` | `package-info.java` | `shared` | 登录、用户、角色与访问控制 |
| `content` | `package-info.java` | `shared` | 文章、分类、标签与 SEO |
| `portfolio` | `package-info.java` | `shared` | 简介、经历、技能与作品 |
| `comment` | `package-info.java` | `shared` | 评论与审核 |
| `media` | `package-info.java` | `shared` | 媒体元数据和存储抽象 |
| `admin` | `package-info.java` | `content`、`portfolio`、`identity`、`comment`、`media`、`shared` 的公开 API | 后台用例编排 |

模块声明使用 `org.springframework.modulith.ApplicationModule`。除 `shared` 中的基础配置外，P0 的模块包不能含其他 Java 类型。后续模块协作只能依赖公开类型、ID、不可变值对象或领域事件，不能引用其他模块实体或 Repository。

`StudyStackModulesTest` 必须同时断言模块集合精确等于上述七个名称并调用 `ApplicationModules.of(StudyStackApplication.class).verify()`。因此新增第八个直接子包、循环依赖或未声明依赖都会失败。

### 3.3 PostgreSQL 与 Flyway

PostgreSQL 是唯一关系数据库。运行配置从 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USER`、`DB_PASSWORD` 组装 JDBC URL；测试由 Testcontainers 动态注入连接信息，不读取开发机数据库。

Flyway 是唯一 schema 变更入口：

- `V1__baseline.sql` 是合法、可重复验证的 PostgreSQL 迁移，只建立 Flyway 历史基线，不创建任何业务表。
- `spring.jpa.hibernate.ddl-auto=validate`，所有 profile 均禁止 `create`、`create-drop` 和 `update`。
- Flyway 校验失败阻止应用 ready。
- Testcontainers 测试断言数据库产品为 PostgreSQL、Flyway schema history 记录版本 `1`，并断言不存在 P0 禁止的业务表名前缀。

P0 不建立数据库模块 schema 隔离；业务表命名和所有权在首次业务迁移前由对应阶段 TECH 固化。

### 3.4 Actuator

仅启用 `health` 和 `info`，健康详情默认不向匿名请求显示。探针开启：

- `/actuator/health/liveness` 包含 `livenessState`。
- `/actuator/health/readiness` 包含 `readinessState` 与 `db`。
- readiness 中任一必需组件 `DOWN` 时返回 HTTP 503。

Caddy 不匹配 `/actuator/**`，Compose 通过应用容器内部端口执行健康检查。P0 不公开 Prometheus、env、beans、configprops 或 heapdump。

## 4. OpenAPI 3 边界

### 4.1 生成与 profile

`OpenApiConfiguration` 使用 `@OpenAPIDefinition` 集中定义标题 `StudyStack API`、P0 版本和说明。P0 没有业务路径，但文档必须包含有效的 `openapi`、`info` 和 `paths` 字段。

| Profile | `/v3/api-docs` | Swagger UI | 依据 |
|---|---|---|---|
| `dev` | 开启 | 开启 | 本地联调 |
| `test` | 开启 | 开启 | 契约测试与产物生成 |
| `prod` | 关闭 | 关闭 | 未建立认证前不匿名暴露 |

`OpenApiDevelopmentIntegrationTest` 从真实 Spring MVC 上下文请求文档，验证 OpenAPI 3 格式后将规范化 JSON 写入 `server/target/openapi/openapi.json`。输出使用稳定 key 顺序和 LF 换行，避免无语义漂移。

### 4.2 注解策略

允许注解仅来自 `io.swagger.v3.oas.annotations`。接口实现阶段按职责使用：Controller 类用 `@Tag`，方法用 `@Operation`，不能由签名表达的重要参数用 `@Parameter`，DTO 和枚举用 `@Schema`，主要响应使用 `@ApiResponse` 或 `@ApiResponses`，认证接口使用 `@SecurityRequirement`，全局信息和安全方案使用 `@OpenAPIDefinition` 与 `@SecurityScheme`。

`OpenApiAnnotationPolicyTest` 扫描 `server/src/main/java`，拒绝 `io.swagger.annotations` import 以及 `@Api`、`@ApiOperation`、`@ApiParam`、`@ApiModel`、`@ApiModelProperty`。扫描限定 Java 源码，不扫描规格文档中的政策说明。

### 4.3 前端契约同步

`openapi-typescript` 读取 `server/target/openapi/openapi.json`：

- `pnpm contract:generate` 更新 `web/src/shared/api/generated/openapi.d.ts`。
- `pnpm contract:check` 生成临时文件到 `web/.contract/openapi.d.ts`，由 `assert-contract-sync.mjs` 归一化换行后与已提交类型逐字节比较。
- 临时目录加入 `.gitignore`，检查命令不得改写正式生成文件。
- 差异时命令输出正式文件和临时文件路径并以状态 1 退出。

前端不得用 `as` 把未经校验的 API 数据直接强转为 union 或 enum。P0 没有业务响应；从 P1 开始每个外部响应在进入前端领域状态前必须通过 Zod 的 parse、safeParse 或明确 filter/map。

## 5. 前端边界

### 5.1 构建与依赖

前端采用 Vue 3 Composition API、TypeScript strict mode、Vite、Vue Router、TanStack Query for Vue、Pinia、Zod、Vitest、Vue Test Utils、Playwright、ESLint 和 `openapi-typescript`。`pnpm-lock.yaml` 是依赖解析事实来源，CI 使用 `pnpm install --frozen-lockfile`。

标准脚本为：

| 脚本 | 职责 |
|---|---|
| `pnpm lint` | ESLint 静态检查 |
| `pnpm typecheck` | `vue-tsc --noEmit` |
| `pnpm test` | Vitest 单元及静态契约测试 |
| `pnpm build` | 类型检查后的 Vite production build |
| `pnpm contract:generate` | 从后端 OpenAPI 生成正式 TypeScript 类型 |
| `pnpm contract:check` | 比较后端 OpenAPI 与正式生成类型 |
| `pnpm test:e2e` | Playwright 验证 Compose/Caddy 路由 |

### 5.2 应用入口与状态

`create-app.ts` 创建 Vue 应用并安装 Router、Pinia、Vue Query plugin。TanStack Query 只管理服务端状态；Pinia 仅为后续主题或未提交草稿等客户端状态预留，不在 P0 创建 store。Zod 用于 `env.ts` 的公开运行时配置校验，并作为后续 API 边界校验标准。

路由仅包含：

- `/` -> `HomeView.vue`，显示 StudyStack 最小应用壳。
- `/foundation` -> `FoundationView.vue`，用于证明深层 SPA 刷新。
- `/:pathMatch(.*)*` -> `NotFoundView.vue`，显示前端未找到状态。

不得创建登录、文章、作品、评论、上传或后台路由。P0 页面不请求业务 API。

### 5.3 前端环境配置

`web/.env.example` 只定义 `VITE_API_BASE_URL=/api`。`env.ts` 使用 Zod 要求该值为同源绝对路径，必须以 `/` 开头且不得包含协议、凭据或查询串。数据库、OAuth 和管理员配置只存在服务端或 Compose 环境中，Vite 不得读取。

## 6. Caddy 与 Docker 边界

### 6.1 Caddy

P0 `deploy/Caddyfile` 用于本地容器验收：

1. 精确 matcher 覆盖 `/actuator` 与 `/actuator/*`，命中后直接返回 404。
2. 精确 matcher 覆盖 `/api`、`/api/*`、`/oauth2`、`/oauth2/*`、`/login/oauth2`、`/login/oauth2/*`。
3. 后端 matcher 命中后 `reverse_proxy app:8080`。
4. 其余请求以 Vue `dist` 为 root，先尝试真实文件，再回退 `/index.html`，最后 `file_server`。
5. 不配置 CORS，不实现认证或限流。

路由匹配必须先于 SPA fallback。P0 只验证 HTTP 本地拓扑；公网 HTTPS、主域名跳转和证书生命周期属于 P7。

### 6.2 Docker Compose

`deploy/compose.yml` 仅定义：

| 服务 | 职责 | 持久化/依赖 |
|---|---|---|
| `postgres` | PostgreSQL | 命名卷 `postgres-data`；使用 `pg_isready` 健康检查 |
| `app` | 构建并运行 Spring Boot | 等待 `postgres` healthy；环境变量注入数据源；内部暴露 8080 |
| `caddy` | 提供 Vue dist 并代理后端 | 绑定本地 HTTP 端口；不保存业务数据 |

Compose 使用 `.env.example` 展示 `DB_NAME`、`DB_USER`、`DB_PASSWORD` 和 `CADDY_HTTP_PORT`。`DB_PASSWORD` 示例值带 `EXAMPLE_ONLY_` 前缀，配置策略测试保证其不被误判为真实密钥，并保证 production profile 拒绝示例值。

Docker build 文件在 P0 实现中为 `server/Dockerfile` 与 `web/Dockerfile`：后端镜像只含 Java 21 runtime 和应用 jar；前端多阶段镜像使用 pnpm 构建后把 `dist` 复制到 Caddy 镜像。生产环境不运行 Node.js。

## 7. CI 边界

`.github/workflows/ci.yml` 在 pull request 和显式分支 push 上运行以下 job：

| Job | 输入 | 门禁 |
|---|---|---|
| `backend` | Java 21、Maven cache、Docker | `server/mvnw.cmd verify` 的 Linux 等价命令 `./mvnw verify`，上传 OpenAPI artifact |
| `frontend` | Node LTS、pnpm、lockfile | frozen install、lint、typecheck、test、build |
| `contract` | backend OpenAPI artifact、frontend lockfile | `pnpm contract:check` |
| `compose` | Docker Compose、仓库配置 | `docker compose config --quiet` 与 Compose 静态断言 |
| `e2e` | backend、frontend、Compose | 构建三个服务并运行 Playwright 路由测试 |

工作流授予最小只读仓库权限，不配置环境部署权限，不登录容器仓库，不推送镜像，不创建 release。所有 job 通过后才满足 B12。

## 8. 配置与安全基线

- 根 `.env.example`、`web/.env.example` 只包含可公开模板值；实际 `.env`、证书、私钥、数据库转储和本地卷目录加入 `.gitignore`。
- Spring 配置使用 `${VARIABLE}` 或带非敏感本地默认值的表达式。数据库密码不得有可用于 production 的默认值。
- `application-prod.yml` 禁止 Swagger/OpenAPI，并禁止 example-only 密码。
- 日志不得输出完整 JDBC URL 中的凭据或环境变量值。
- Caddy 与应用保持同源，不开放宽泛 CORS。
- 依赖安全扫描和镜像漏洞门禁属于 P6；P0 只保证依赖锁定与最小镜像边界。

## 9. PRODUCT 行为到测试与命令映射

以下映射覆盖 PRODUCT 的全部行为编号。命令均从项目根目录执行，并强制使用 PowerShell 7。

| 行为 | 测试文件 | 验证命令 | 预期结果 |
|---|---|---|---|
| B1 | `server/src/test/java/com/studystack/foundation/BuildRuntimeContractTest.java` | `pwsh -NoLogo -NoProfile -Command "Set-Location server; .\mvnw.cmd -Dtest=BuildRuntimeContractTest test"` | Java feature 为 21，单 POM、无 `<modules>`，测试通过 |
| B2 | `server/src/test/java/com/studystack/foundation/StudyStackModulesTest.java` | `pwsh -NoLogo -NoProfile -Command "Set-Location server; .\mvnw.cmd -Dtest=StudyStackModulesTest test"` | 精确发现七个模块且 `verify()` 无违规 |
| B3 | `server/src/test/java/com/studystack/foundation/PostgresFlywayIntegrationTest.java` | `pwsh -NoLogo -NoProfile -Command "Set-Location server; .\mvnw.cmd -Dtest=PostgresFlywayIntegrationTest test"` | PostgreSQL 容器启动，Flyway 版本 1 成功，DDL 为 validate，无业务表 |
| B4 | `server/src/test/java/com/studystack/foundation/ActuatorIntegrationTest.java` | `pwsh -NoLogo -NoProfile -Command "Set-Location server; .\mvnw.cmd -Dtest=ActuatorIntegrationTest test"` | liveness/readiness 正常为 200；数据库失败场景 readiness 为 503 |
| B5 | `server/src/test/java/com/studystack/foundation/OpenApiDevelopmentIntegrationTest.java`; `server/src/test/java/com/studystack/foundation/OpenApiAnnotationPolicyTest.java` | `pwsh -NoLogo -NoProfile -Command "Set-Location server; .\mvnw.cmd -Dtest=OpenApiDevelopmentIntegrationTest,OpenApiAnnotationPolicyTest test"` | dev/test 文档与 UI 可访问，OpenAPI 3 有效，禁用注解扫描零命中 |
| B6 | `server/src/test/java/com/studystack/foundation/OpenApiProductionIntegrationTest.java` | `pwsh -NoLogo -NoProfile -Command "Set-Location server; .\mvnw.cmd -Dtest=OpenApiProductionIntegrationTest test"` | prod 下文档 JSON 和 Swagger UI 路径均为 404 |
| B7 | `web/src/app/create-app.spec.ts`; `web/src/router/router.spec.ts` | `pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm test -- create-app.spec.ts router.spec.ts"` | 三个插件安装，根/深层/未找到路由渲染符合契约 |
| B8 | `web/src/config/env.spec.ts`; `web/src/tests/config-policy.spec.ts` | `pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm test -- env.spec.ts config-policy.spec.ts"` | 合法同源路径通过，非法值和秘密命名失败，示例文件无真实密钥 |
| B9 | `web/e2e/foundation-routing.spec.ts` | `pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm test:e2e -- foundation-routing.spec.ts"` | 静态文件和 SPA fallback 成功，三组后端前缀不返回 SPA HTML |
| B10 | `deploy/tests/compose-config.test.mjs` | `pwsh -NoLogo -NoProfile -Command "node --test deploy/tests/compose-config.test.mjs"` | Compose 仅含三个服务、命名卷和健康依赖，配置可解析 |
| B11 | `web/src/tests/contract-sync.spec.ts`; `web/scripts/assert-contract-sync.mjs` | `pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm contract:check"` | 后端 OpenAPI 与正式 TypeScript 类型逐字节一致 |
| B12 | `web/src/tests/ci-workflow.spec.ts`; `.github/workflows/ci.yml` | `pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm test -- ci-workflow.spec.ts"` | 所有 P0 job 与门禁存在，且无 deploy、push image 或远程资源步骤 |

### 9.1 聚合验证命令

后端全量：

```powershell
pwsh -NoLogo -NoProfile -Command "Set-Location server; .\mvnw.cmd verify"
```

预期：Maven `BUILD SUCCESS`，B1-B6 对应测试全部通过，并生成 `server/target/openapi/openapi.json`。

前端全量：

```powershell
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm install --frozen-lockfile"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm lint"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm typecheck"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm test"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm contract:check"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm build"
```

预期：所有命令状态为 0，Vitest 无失败，契约无差异，`web/dist/index.html` 存在。

部署配置与 E2E：

```powershell
pwsh -NoLogo -NoProfile -Command "docker compose --env-file .env.example -f deploy/compose.yml config --quiet"
pwsh -NoLogo -NoProfile -Command "docker compose --env-file .env.example -f deploy/compose.yml up -d --build --wait"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm test:e2e -- foundation-routing.spec.ts"
pwsh -NoLogo -NoProfile -Command "docker compose --env-file .env.example -f deploy/compose.yml down --volumes"
```

预期：Compose 配置有效，三个服务达到健康条件，Playwright 全部通过，验收容器与测试卷被清理。

### 9.2 只读参考项目过程门禁

参考项目不是 StudyStack 的测试依赖，不读取其构建产物，也不运行其构建。规格工作开始和结束均执行：

```powershell
pwsh -NoLogo -NoProfile -Command "git -C C:\softWare\project\latest\skillhub status --short --branch"
```

预期两次输出完全一致，只有 `## codex/new-feature-development`。该门禁不对应新项目测试文件，因为它验证的是外部只读工作树状态。

## 10. 风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| 空业务模块被误认为可提前开发 | P0 越界并污染后续契约 | 模块测试只接受 `package-info.java`，验收扫描禁止业务类型 |
| OpenAPI 在空 paths 时生成不稳定 | 前端类型频繁漂移 | 规范化 JSON key 顺序与换行，契约检查使用固定生成器和 lockfile |
| prod 文档只被代理层遮挡 | 应用端口直连仍泄露契约 | `application-prod.yml` 直接关闭 springdoc，prod 集成测试断言 404 |
| Caddy fallback 截获 OAuth/API | 登录阶段出现 HTML 伪响应 | 精确 path matcher 先执行，E2E 同时覆盖裸前缀和子路径 |
| 本地示例密码误入生产 | 数据库弱凭据 | 示例值使用明确 sentinel，production 配置策略拒绝该前缀 |
| Compose 与 CI 命令分叉 | 本地通过而 CI 失败 | CI 复用 package scripts、Maven `verify` 与同一 `deploy/compose.yml` |

## 11. 后续阶段接口

P0 只向后续阶段提供以下稳定基础：七个模块包、PostgreSQL/Flyway、Actuator、OpenAPI 3 生成规则、Vue 应用入口、同源 `/api` 约定、Caddy OAuth/API 代理前缀以及 CI 门禁。任何登录、业务 DTO、业务表或页面路由都必须由对应阶段 PRODUCT 和 TECH 定义后再加入。
