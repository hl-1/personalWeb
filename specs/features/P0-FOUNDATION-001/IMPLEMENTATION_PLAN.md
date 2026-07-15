# StudyStack P0 Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立通过 B1-B12 验收、没有任何业务能力的 Java 21 Spring Modulith + Vue 3 + PostgreSQL StudyStack 工程底座。

**Architecture:** `server/` 是单模块 Maven 的 Spring Boot 模块化单体，`web/` 是独立 pnpm Vue SPA，PostgreSQL 由 Flyway 管理，Caddy 在本地 Compose 中提供静态文件和后端路径代理。OpenAPI JSON 是前后端契约来源，CI 只做验证，不部署或推送制品。

**Tech Stack:** Java 21、Spring Boot 3.x、Spring Modulith、Spring Data JPA、PostgreSQL、Flyway、Testcontainers、Actuator、springdoc-openapi、OpenAPI 3、Vue 3、TypeScript、Vite、Vue Router、TanStack Query for Vue、Pinia、Zod、pnpm、Vitest、Playwright、Caddy、Docker Compose、GitHub Actions

---

## 0. 执行门禁

- 本计划只有在用户明确批准 `PRODUCT.md`、`TECH.md` 和本文件后才能执行。
- 一次只执行一个 Task；每个 Task 严格按 RED、GREEN、REFACTOR 顺序进行。
- RED 必须保存真实失败输出；如果测试意外通过，先解释现有行为，不能直接进入 GREEN。
- 每次只创建该 Task 的 Files 列表所列文件。不得创建登录、文章、作品、评论、上传、后台业务或任何 P1+ 文件。
- 所有 PowerShell 命令使用 PowerShell 7，即 `pwsh`。
- 本计划不授权 `git init`、commit、push、创建分支、创建远程仓库、发布镜像或部署。
- Spring Boot 选择实施当日仍受维护的 3.x 补丁版并固定在 POM；Maven、Node、pnpm 和前端依赖都由配置文件与 lockfile固定，不使用浮动版本范围或 snapshot。

## 1. 文件责任图

| 区域 | 责任 | 禁止内容 |
|---|---|---|
| `server/src/main/java/com/studystack` | Spring Boot 入口和七个 Modulith 包边界 | 业务实体、Repository、业务服务、业务 Controller |
| `server/src/main/resources` | profile、Flyway baseline、健康与 springdoc 配置 | 真实密码、业务表 DDL、Hibernate 自动建表 |
| `web/src/app` | 安装 Router、QueryClient、Pinia | 业务 store、认证状态、服务端数据副本 |
| `web/src/config` | Zod 校验公开 Vite 配置 | 服务端秘密或静默默认值 |
| `web/src/shared/api/generated` | OpenAPI 生成的 TypeScript 类型 | 手写业务类型或手工修补生成结果 |
| `deploy` | 本地 Compose、Caddy、配置结构测试 | 公网证书、远程主机、Kubernetes、部署脚本 |
| `.github/workflows/ci.yml` | P0 验证门禁 | deploy job、镜像 push、远程资源操作 |

## Task 1: Java 21 单 Maven 工程与 Spring Boot 入口

**覆盖行为：** B1

**Files:**

- Create: `server/.mvn/wrapper/maven-wrapper.properties`
- Create: `server/mvnw`
- Create: `server/mvnw.cmd`
- Create: `server/pom.xml`
- Create: `server/src/main/java/com/studystack/StudyStackApplication.java`
- Create: `server/src/test/java/com/studystack/foundation/BuildRuntimeContractTest.java`
- Create: `server/src/test/resources/application-test.yml`

### RED

- [ ] 创建 Maven Wrapper、单个 `server/pom.xml` 和 `BuildRuntimeContractTest`。POM 先只配置 Java 21、Spring Boot 测试基础、Maven Enforcer 和 Surefire；测试通过文件系统与运行时断言 Java feature 为 21、`server/pom.xml` 不含 `<modules>`、应用入口类存在且带 Spring Boot 启动注解。
- [ ] 暂不创建 `StudyStackApplication.java`，执行：

```powershell
pwsh -NoLogo -NoProfile -Command "Set-Location server; .\mvnw.cmd -Dtest=BuildRuntimeContractTest test"
```

Expected: `BuildRuntimeContractTest` 失败，失败信息明确指出 `com.studystack.StudyStackApplication` 或对应源文件不存在；Maven Wrapper 本身可运行。

### GREEN

- [ ] 创建 `StudyStackApplication.java`，只包含 `@SpringBootApplication` 和最小 `main` 入口；POM 加入 TECH §3.1 列出的运行和测试依赖，所有版本固定且不声明 `<modules>`。
- [ ] 再次执行定向测试。

Expected: 1 个测试类通过，Enforcer 确认 Java 21，Maven 输出 `BUILD SUCCESS`。

### REFACTOR

- [ ] 运行 effective POM 与依赖收敛检查：

```powershell
pwsh -NoLogo -NoProfile -Command "Set-Location server; .\mvnw.cmd help:effective-pom -Doutput=target/effective-pom.xml"
pwsh -NoLogo -NoProfile -Command "Set-Location server; .\mvnw.cmd dependency:analyze"
```

Expected: `target/effective-pom.xml` 生成；项目没有子 module；依赖分析不报告可删除的直接依赖或未声明的已使用依赖。

## Task 2: Spring Modulith 七模块边界

**覆盖行为：** B2

**Files:**

- Create: `server/src/main/java/com/studystack/admin/package-info.java`
- Create: `server/src/main/java/com/studystack/comment/package-info.java`
- Create: `server/src/main/java/com/studystack/content/package-info.java`
- Create: `server/src/main/java/com/studystack/identity/package-info.java`
- Create: `server/src/main/java/com/studystack/media/package-info.java`
- Create: `server/src/main/java/com/studystack/portfolio/package-info.java`
- Create: `server/src/main/java/com/studystack/shared/package-info.java`
- Create: `server/src/test/java/com/studystack/foundation/StudyStackModulesTest.java`

### RED

- [ ] 创建 `StudyStackModulesTest`，断言发现的 module name 集合精确等于 `admin`、`comment`、`content`、`identity`、`media`、`portfolio`、`shared`，并调用 `ApplicationModules.verify()`。
- [ ] 在业务包尚不存在时执行：

```powershell
pwsh -NoLogo -NoProfile -Command "Set-Location server; .\mvnw.cmd -Dtest=StudyStackModulesTest test"
```

Expected: 测试失败并显示期望七个模块、实际模块为空或不完整。

### GREEN

- [ ] 创建七个 `package-info.java`。六个业务模块只允许依赖 `shared`；`admin` 允许依赖五个业务模块、`identity` 和 `shared` 的公开 API；`shared` 不依赖业务模块。
- [ ] 重跑定向测试。

Expected: 精确发现七个模块，`verify()` 无 cycle、越界或未声明依赖，测试通过。

### REFACTOR

- [ ] 在测试中增加 P0 空模块约束：六个业务模块目录除 `package-info.java` 外不得出现 `.java` 文件，`shared` 只允许规格列出的基础配置类型。
- [ ] 再次运行同一命令。

Expected: 模块验证与 P0 范围扫描同时通过；新增业务 Java 类型会得到包含文件路径的失败信息。

## Task 3: PostgreSQL、Flyway 与 Testcontainers

**覆盖行为：** B3

**Files:**

- Create: `server/src/main/resources/application.yml`
- Create: `server/src/main/resources/application-dev.yml`
- Create: `server/src/main/resources/application-prod.yml`
- Create: `server/src/main/resources/db/migration/V1__baseline.sql`
- Create: `server/src/test/java/com/studystack/foundation/PostgresFlywayIntegrationTest.java`
- Modify: `server/src/test/resources/application-test.yml`
- Modify: `server/pom.xml`

### RED

- [ ] 先加入 Testcontainers PostgreSQL、Flyway 和 JPA 测试依赖，创建 `PostgresFlywayIntegrationTest`。测试要求数据库产品名为 PostgreSQL、Flyway history 包含成功版本 `1`、Hibernate DDL 为 `validate`、不存在业务表。
- [ ] 尚未创建数据源配置与迁移时执行：

```powershell
pwsh -NoLogo -NoProfile -Command "Set-Location server; .\mvnw.cmd -Dtest=PostgresFlywayIntegrationTest test"
```

Expected: Spring context 或 Flyway 断言失败，原因是数据源/迁移未配置；不得出现 H2 启动成功。

### GREEN

- [ ] 创建三个 profile 配置和 `V1__baseline.sql`。baseline 只执行 PostgreSQL 合法的无业务 schema 基线；配置启用 Flyway 并设置 `spring.jpa.hibernate.ddl-auto=validate`。
- [ ] 使用 `@ServiceConnection` 或 `@DynamicPropertySource` 将 Testcontainers 连接仅注入测试 context。
- [ ] 重跑定向测试。

Expected: PostgreSQL 容器启动，Flyway schema history 中版本 `1` 为 success，未发现业务表，测试通过。

### REFACTOR

- [ ] 增加失败路径测试：使用错误端口启动独立 context，断言 context 启动失败且根因是 PostgreSQL 连接；使用修改后的 migration 副本验证 checksum mismatch 被 Flyway 拒绝。
- [ ] 运行定向测试。

Expected: 正常路径通过，两个失败路径被测试捕获；没有内存数据库降级。

## Task 4: Actuator liveness 与 readiness

**覆盖行为：** B4

**Files:**

- Create: `server/src/test/java/com/studystack/foundation/ActuatorIntegrationTest.java`
- Modify: `server/src/main/resources/application.yml`
- Modify: `server/src/test/resources/application-test.yml`

### RED

- [ ] 创建 `ActuatorIntegrationTest`，以随机端口和正常 Testcontainers 数据库启动应用，断言两个探针；随后停止数据库容器并等待健康缓存刷新，断言应用进程仍可访问、liveness 保持 `UP`、readiness 为 503 且状态 `DOWN`。
- [ ] 在未配置 probe group 时执行：

```powershell
pwsh -NoLogo -NoProfile -Command "Set-Location server; .\mvnw.cmd -Dtest=ActuatorIntegrationTest test"
```

Expected: readiness group 或数据库成员断言失败，显示缺失 `db` 或状态码不符。

### GREEN

- [ ] 配置只暴露 `health`、`info`，启用 probes，并将 `readinessState` 与 `db` 放入 readiness group；健康详情保持非匿名隐藏。
- [ ] 重跑定向测试。

Expected: 正常 liveness/readiness 为 200 `UP`；数据库失败场景 readiness 为 503 `DOWN`。

### REFACTOR

- [ ] 增加端点暴露负面断言，确认 `/actuator/env`、`/actuator/beans`、`/actuator/configprops` 和 `/actuator/heapdump` 均未暴露。
- [ ] 重跑定向测试。

Expected: 探针和负面端点断言全部通过。

## Task 5: OpenAPI 3 生成、注解政策与生产关闭

**覆盖行为：** B5、B6

**Files:**

- Create: `server/src/main/java/com/studystack/shared/openapi/OpenApiConfiguration.java`
- Create: `server/src/test/java/com/studystack/foundation/OpenApiAnnotationPolicyTest.java`
- Create: `server/src/test/java/com/studystack/foundation/OpenApiDevelopmentIntegrationTest.java`
- Create: `server/src/test/java/com/studystack/foundation/OpenApiProductionIntegrationTest.java`
- Modify: `server/src/main/resources/application-dev.yml`
- Modify: `server/src/main/resources/application-prod.yml`
- Modify: `server/src/test/resources/application-test.yml`
- Modify: `server/pom.xml`

### RED

- [ ] 创建开发测试，要求 `/v3/api-docs` 为 OpenAPI 3、标题为 `StudyStack API`，Swagger UI 可访问，并把规范化 JSON 写到 `target/openapi/openapi.json`。
- [ ] 创建生产测试，使用 `prod` profile 断言文档 JSON 和 Swagger 四类路径均返回 404。
- [ ] 创建源码政策测试，扫描 `src/main/java` 中的旧 Swagger package import 和五个禁用注解短名。
- [ ] 尚未添加 springdoc 配置时执行：

```powershell
pwsh -NoLogo -NoProfile -Command "Set-Location server; .\mvnw.cmd -Dtest=OpenApiDevelopmentIntegrationTest,OpenApiProductionIntegrationTest,OpenApiAnnotationPolicyTest test"
```

Expected: 开发文档端点、固定标题或生产关闭断言至少一项失败；注解政策测试通过。

### GREEN

- [ ] 添加 springdoc UI starter，创建集中 `@OpenAPIDefinition` 配置；在 dev/test 开启文档和 UI，在 prod 直接关闭两者。
- [ ] 规范化 JSON 输出使用固定 key 顺序与 LF，不加入时间戳、绝对路径或随机值。
- [ ] 重跑定向测试。

Expected: 三个测试类全部通过；`server/target/openapi/openapi.json` 存在，prod 四类路径均为 404。

### REFACTOR

- [ ] 连续执行两次 OpenAPI 开发测试并比较 SHA-256：

```powershell
pwsh -NoLogo -NoProfile -Command "Set-Location server; .\mvnw.cmd -Dtest=OpenApiDevelopmentIntegrationTest test; (Get-FileHash target/openapi/openapi.json -Algorithm SHA256).Hash"
pwsh -NoLogo -NoProfile -Command "Set-Location server; .\mvnw.cmd -Dtest=OpenApiDevelopmentIntegrationTest test; (Get-FileHash target/openapi/openapi.json -Algorithm SHA256).Hash"
```

Expected: 两次 hash 完全一致。

## Task 6: Vue 3 工程、插件入口与路由

**覆盖行为：** B7

**Files:**

- Create: `web/.env.example`
- Create: `web/eslint.config.js`
- Create: `web/index.html`
- Create: `web/package.json`
- Create: `web/pnpm-lock.yaml`
- Create: `web/tsconfig.json`
- Create: `web/tsconfig.app.json`
- Create: `web/tsconfig.node.json`
- Create: `web/vite.config.ts`
- Create: `web/vitest.config.ts`
- Create: `web/src/App.vue`
- Create: `web/src/main.ts`
- Create: `web/src/app/create-app.ts`
- Create: `web/src/app/create-app.spec.ts`
- Create: `web/src/router/index.ts`
- Create: `web/src/router/router.spec.ts`
- Create: `web/src/views/HomeView.vue`
- Create: `web/src/views/FoundationView.vue`
- Create: `web/src/views/NotFoundView.vue`

### RED

- [ ] 创建 package scripts、strict TypeScript/Vitest 配置和两个测试文件。测试要求 `create-app` 安装 Router、QueryClient、Pinia，并要求 `/`、`/foundation`、未知路径渲染各自稳定标识。
- [ ] 只创建测试与最小配置，不创建应用入口和 views，执行：

```powershell
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm install"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm test -- create-app.spec.ts router.spec.ts"
```

Expected: 依赖安装成功并生成 lockfile；Vitest 因入口模块或 view 不存在失败。

### GREEN

- [ ] 创建最小应用壳、三个插件入口、三条路由和三个 view。页面只展示 StudyStack 标识、foundation 深链标识或未找到状态，不创建业务导航与业务请求。
- [ ] 重跑定向测试。

Expected: 插件、根路由、深层路由和未找到路由测试全部通过。

### REFACTOR

- [ ] 将应用创建与 DOM mount 分离，确保测试不依赖全局单例；统一路由工厂允许 memory history 注入。
- [ ] 执行：

```powershell
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm lint"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm typecheck"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm test"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm build"
```

Expected: 四条命令状态均为 0，`web/dist/index.html` 存在。

## Task 7: 环境变量与密钥策略

**覆盖行为：** B8

**Files:**

- Create: `.env.example`
- Create: `.gitignore`
- Create: `web/src/config/env.ts`
- Create: `web/src/config/env.spec.ts`
- Create: `web/src/tests/config-policy.spec.ts`
- Modify: `server/src/main/resources/application.yml`
- Modify: `server/src/main/resources/application-prod.yml`
- Modify: `web/src/app/create-app.ts`

### RED

- [ ] 创建 `env.spec.ts`，覆盖 `/api` 合法、缺失、绝对 URL、协议相对 URL、带凭据、带查询串和非 `/` 开头值。
- [ ] 创建 `config-policy.spec.ts`，扫描 `.env.example`、`web/.env.example`、Spring YAML 和 `web/src`：禁止 Vite 暴露服务端变量名，禁止真实密钥形态，要求敏感示例值使用 `EXAMPLE_ONLY_` sentinel。
- [ ] 尚未创建 `env.ts` 与根示例文件时执行：

```powershell
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm test -- env.spec.ts config-policy.spec.ts"
```

Expected: 测试因 env schema 或根示例配置不存在失败，且输出缺失路径。

### GREEN

- [ ] 创建 Zod env schema 与两级示例文件；Spring 数据源只从服务端变量读取，production profile 明确拒绝 sentinel 密码。前端应用创建时先校验公开配置。
- [ ] 配置 `.gitignore` 排除 `.env`、`.env.*.local`、证书、私钥、数据库转储、测试临时契约和构建目录，同时不忽略两个 `.env.example`。
- [ ] 重跑定向测试。

Expected: 所有合法/非法 env case 与静态配置政策断言通过。

### REFACTOR

- [ ] 让 Zod 错误只显示变量名和规则，不回显秘密值；增加测试验证错误文本不含输入密码。
- [ ] 重跑定向测试。

Expected: 失败信息可定位变量且不泄露值，测试通过。

## Task 8: OpenAPI TypeScript 生成与同步检查

**覆盖行为：** B11

**Files:**

- Create: `web/scripts/assert-contract-sync.mjs`
- Create: `web/src/tests/contract-sync.spec.ts`
- Create: `web/src/shared/api/generated/openapi.d.ts`
- Modify: `web/package.json`
- Modify: `web/pnpm-lock.yaml`
- Modify: `.gitignore`

### RED

- [ ] 先运行 Task 5 测试生成 `server/target/openapi/openapi.json`。
- [ ] 添加固定版本 `openapi-typescript`、`contract:generate` 和 `contract:check` scripts，创建测试要求检查命令不改写正式文件、内容不同时状态为 1、相同时为 0。
- [ ] 暂不创建正式生成类型，执行：

```powershell
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm contract:check"
```

Expected: 命令状态为 1，输出指出 `src/shared/api/generated/openapi.d.ts` 缺失或与 `.contract/openapi.d.ts` 不一致。

### GREEN

- [ ] 实现换行归一化比较脚本并运行正式生成：

```powershell
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm contract:generate"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm contract:check"
```

Expected: 正式类型文件生成，第二条命令状态为 0，且正式文件时间戳在检查命令期间不变化。

### REFACTOR

- [ ] 临时修改 `.contract/openapi.d.ts` 的可比较内容验证差异信息，再由命令重新生成临时文件；执行同步单测。

```powershell
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm test -- contract-sync.spec.ts"
```

Expected: 相同、差异、缺失三条路径均通过；差异错误包含两个相对路径，不含机器绝对路径。

## Task 9: Docker 镜像、Compose 与 Caddy 配置

**覆盖行为：** B9、B10

**Files:**

- Create: `server/Dockerfile`
- Create: `server/.dockerignore`
- Create: `web/Dockerfile`
- Create: `web/.dockerignore`
- Create: `deploy/Caddyfile`
- Create: `deploy/compose.yml`
- Create: `deploy/tests/compose-config.test.mjs`
- Modify: `.env.example`

### RED

- [ ] 使用 Node 内置 test runner 创建 Compose 静态测试，解析 `docker compose ... config --format json`，断言服务集合精确为 `postgres`、`app`、`caddy`，存在 `postgres-data` 命名卷、PostgreSQL healthcheck、app healthy 依赖和 Caddyfile mount。
- [ ] 在 Compose 尚不存在时执行：

```powershell
pwsh -NoLogo -NoProfile -Command "node --test deploy/tests/compose-config.test.mjs"
```

Expected: 测试失败并指出 `deploy/compose.yml` 不存在。

### GREEN

- [ ] 创建后端 Java 21 runtime 镜像、前端 pnpm 多阶段构建+Caddy runtime 镜像、Compose 和 Caddyfile。Caddy 必须先拒绝 `/actuator` 裸路径及子路径，再代理三个后端裸前缀及其子路径，最后才执行 SPA fallback。
- [ ] 运行：

```powershell
pwsh -NoLogo -NoProfile -Command "docker compose --env-file .env.example -f deploy/compose.yml config --quiet"
pwsh -NoLogo -NoProfile -Command "node --test deploy/tests/compose-config.test.mjs"
```

Expected: Compose 解析状态为 0，静态测试通过，服务和卷集合没有额外项。

### REFACTOR

- [ ] 检查最终镜像阶段：app 不包含 Maven cache，Caddy 镜像不包含 `node_modules`、pnpm store 或前端源码；Compose 不映射 PostgreSQL 公网端口。
- [ ] 构建镜像但不推送：

```powershell
pwsh -NoLogo -NoProfile -Command "docker compose --env-file .env.example -f deploy/compose.yml build"
```

Expected: 三个本地镜像构建成功；无 push、registry login 或远程动作。

## Task 10: Caddy 路由 Playwright E2E

**覆盖行为：** B9、B10

**Files:**

- Create: `web/playwright.config.ts`
- Create: `web/e2e/foundation-routing.spec.ts`
- Modify: `web/package.json`
- Modify: `web/pnpm-lock.yaml`

### RED

- [ ] 创建 Playwright 测试，覆盖 `/`、`/foundation`、`/unknown-page`、静态 asset、三个裸后端前缀和三个子路径。前端路径必须返回 SPA HTML；后端路径允许后端 404，但 content-type/body 不得是 SPA HTML；`/actuator` 与 `/actuator/health` 必须由 Caddy 返回 404 且不得返回 SPA HTML。
- [ ] 尚未启动 Compose 时执行：

```powershell
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm test:e2e -- foundation-routing.spec.ts"
```

Expected: Playwright 因 base URL 不可连接失败，证明测试实际依赖运行拓扑。

### GREEN

- [ ] 启动 Compose 并运行 E2E：

```powershell
pwsh -NoLogo -NoProfile -Command "docker compose --env-file .env.example -f deploy/compose.yml up -d --build --wait"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm test:e2e -- foundation-routing.spec.ts"
```

Expected: 三个服务健康；静态、fallback、代理隔离和 Actuator 非代理断言全部通过。

### REFACTOR

- [ ] 让 E2E 失败输出包含 URL、status、content-type 与响应前 200 字符，便于区分 Caddy 404、后端 404 和 SPA HTML。
- [ ] 重跑 E2E 后清理本轮容器与测试卷：

```powershell
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm test:e2e -- foundation-routing.spec.ts"
pwsh -NoLogo -NoProfile -Command "docker compose --env-file .env.example -f deploy/compose.yml down --volumes"
```

Expected: E2E 通过，随后 Compose 服务和 `postgres-data` 测试卷被移除。

## Task 11: CI 质量门禁

**覆盖行为：** B12

**Files:**

- Create: `.github/workflows/ci.yml`
- Create: `web/src/tests/ci-workflow.spec.ts`
- Modify: `web/package.json`
- Modify: `web/pnpm-lock.yaml`

### RED

- [ ] 创建 CI workflow 静态测试，使用 YAML parser 断言存在 `backend`、`frontend`、`contract`、`compose`、`e2e` jobs；断言 backend 上传 OpenAPI artifact、contract 下载 artifact；断言不存在 deployment environment、registry login、image push、release 或远程 SSH 命令。
- [ ] 在 workflow 尚不存在时执行：

```powershell
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm test -- ci-workflow.spec.ts"
```

Expected: 测试失败并指出 `.github/workflows/ci.yml` 缺失。

### GREEN

- [ ] 创建 CI workflow。backend 使用 Java 21 和 Maven cache；frontend/contract 使用固定 pnpm 与 frozen lockfile；compose 复用 `.env.example`；e2e 运行同一 Compose 和 Playwright 脚本；权限为 `contents: read`。
- [ ] 重跑静态测试。

Expected: 五个 job 与全部命令断言通过，未发现部署或推送能力。

### REFACTOR

- [ ] 确保 job 间只通过 OpenAPI artifact 传递契约，不共享可变 workspace；所有 third-party action 固定到明确 major 或 commit policy，pnpm install 都使用 frozen lockfile。
- [ ] 执行前端全量测试确认 CI 文件解析没有影响其他配置。

```powershell
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm test"
```

Expected: Vitest 全量通过，CI workflow 测试无警告。

## Task 12: P0 聚合验收与进度记录

**覆盖行为：** B1-B12 与参考项目只读门禁

**Files:**

- Create: `specs/PROGRESS.md`
- Modify only if a failing acceptance proves a spec mismatch: `specs/features/P0-FOUNDATION-001/PRODUCT.md`
- Modify only if a failing acceptance proves a spec mismatch: `specs/features/P0-FOUNDATION-001/TECH.md`
- Modify only if a failing acceptance proves a plan defect: `specs/features/P0-FOUNDATION-001/IMPLEMENTATION_PLAN.md`

### RED

- [ ] 在第一次聚合验收前，逐条运行下列命令并记录任何失败；不得先改规格掩盖真实实现差异。

```powershell
pwsh -NoLogo -NoProfile -Command "Set-Location server; .\mvnw.cmd verify"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm install --frozen-lockfile"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm lint"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm typecheck"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm test"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm contract:check"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm build"
pwsh -NoLogo -NoProfile -Command "docker compose --env-file .env.example -f deploy/compose.yml config --quiet"
```

Expected: 若前面 Task 有遗漏，至少一个命令以非零状态暴露具体缺口；若全部已满足，保存这组首次全绿输出作为无需额外 GREEN 修复的证据。

### GREEN

- [ ] 只修复 RED 暴露且属于 P0 的缺口，每次修复后先重跑最小相关命令，再重跑聚合命令。
- [ ] 启动拓扑并执行最终 E2E：

```powershell
pwsh -NoLogo -NoProfile -Command "docker compose --env-file .env.example -f deploy/compose.yml up -d --build --wait"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm test:e2e -- foundation-routing.spec.ts"
pwsh -NoLogo -NoProfile -Command "docker compose --env-file .env.example -f deploy/compose.yml down --volumes"
```

Expected: Compose 三个服务健康，Playwright 全部通过，清理命令状态为 0。

### REFACTOR

- [ ] 创建 `specs/PROGRESS.md`，记录 P0 状态、B1-B12 的命令证据、未运行检查、数据迁移 `V1`、安全结论、当前非 Git 仓库状态和下一阶段前置条件；不得把 P1 标记为可执行，除非用户已验收 P0。
- [ ] 扫描范围、旧注解、模糊占位和秘密：

```powershell
pwsh -NoLogo -NoProfile -Command "rg -n --glob '*.java' 'io\.swagger\.annotations|@Api(Operation|Param|Model|ModelProperty)?\b' server/src/main/java"
pwsh -NoLogo -NoProfile -Command "rg -n 'Redis|Elasticsearch|RabbitMQ|Kafka|MinIO|Kubernetes|spring-cloud-starter-netflix-eureka' server web deploy .github"
pwsh -NoLogo -NoProfile -Command "rg -n 'BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|ghp_[A-Za-z0-9]{20,}|github_pat_|AKIA[0-9A-Z]{16}' . --glob '!specs/**'"
pwsh -NoLogo -NoProfile -Command "git -C C:\softWare\project\latest\skillhub status --short --branch"
```

Expected: 前三条扫描零命中；参考项目输出只有 `## codex/new-feature-development`，与规格编写开始时一致。

## 2. 最终完成定义

P0 只有在以下条件同时满足时才完成：

1. PRODUCT B1-B12 每个编号都有 TECH §9 的测试文件和新鲜命令证据。
2. 后端 `verify`、前端 lint/typecheck/test/build、契约同步、Compose config 和 Playwright E2E 全部状态为 0。
3. `server/target/openapi/openapi.json` 可重复生成，prod 文档路径测试为 404，源码不存在旧版 Swagger 注解。
4. 七个 Modulith 模块边界测试通过，六个业务模块没有 P0 业务类型。
5. PostgreSQL/Flyway 正常和失败路径均被验证，Hibernate 不修改 schema。
6. 示例配置没有真实密钥，Vite 配置不含服务端秘密。
7. CI 只验证，不包含部署、推送镜像或远程资源动作。
8. 只读参考项目状态与开始时完全一致。
9. 用户审阅实施证据并明确允许后，才可进入 P1。
