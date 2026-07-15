---
phase: P0
feature: P0-FOUNDATION-001
status: awaiting-user-acceptance
verified: 2026-07-15
---

# StudyStack 实施进度

## 当前状态

P0 工程底座的 B1-B12 已在本地完成聚合验证。P0 当前状态为“等待用户验收”，不代表 P1 已获准实施。登录、文章、作品、评论、上传和后台业务仍未进入实施范围。

验证环境为 Windows、PowerShell 7、Java 21.0.10 和 Docker Desktop。由于本机 8080 端口已有其他进程使用，最终 Compose 与 Playwright 验收通过 `CADDY_HTTP_PORT=18080` 和 `E2E_BASE_URL=http://127.0.0.1:18080` 执行；Compose 内部服务拓扑和 Caddy 路由规则未改变。

## B1-B12 验证证据

| 行为 | 测试文件或配置 | 2026-07-15 验证结果 |
|---|---|---|
| B1 | `server/src/test/java/com/studystack/foundation/BuildRuntimeContractTest.java` | `server/.\mvnw.cmd verify` 通过；Java 21 Enforcer、单 POM、无 Maven modules 断言通过 |
| B2 | `server/src/test/java/com/studystack/foundation/StudyStackModulesTest.java` | 精确识别 `admin`、`comment`、`content`、`identity`、`media`、`portfolio`、`shared`；定向测试 4/4 通过，聚合测试通过 |
| B3 | `server/src/test/java/com/studystack/foundation/PostgresFlywayIntegrationTest.java` | PostgreSQL 17.7 Testcontainers、Flyway V1、Hibernate `validate`、无业务表及连接/checksum 失败路径通过 |
| B4 | `server/src/test/java/com/studystack/foundation/ActuatorIntegrationTest.java` | liveness/readiness 正常路径、数据库停止后的 503/DOWN 路径和未暴露端点断言通过 |
| B5 | `server/src/test/java/com/studystack/foundation/OpenApiDevelopmentIntegrationTest.java`; `OpenApiAnnotationPolicyTest.java` | dev/test OpenAPI 3、Swagger UI、固定标题/版本及新版注解政策通过；生成 `server/target/openapi/openapi.json` |
| B6 | `server/src/test/java/com/studystack/foundation/OpenApiProductionIntegrationTest.java` | prod 文档 JSON 与 Swagger UI 路径返回 404 的断言通过 |
| B7 | `web/src/app/create-app.spec.ts`; `web/src/router/router.spec.ts` | lint、类型检查、Vitest 和生产构建通过；全量 Vitest 6 个文件、26 个测试全部通过 |
| B8 | `web/src/config/env.spec.ts`; `web/src/tests/config-policy.spec.ts` | 环境变量格式、示例配置、生产示例密码拒绝和错误信息不泄密断言通过 |
| B9 | `web/e2e/foundation-routing.spec.ts` | Playwright 3/3 通过；SPA、静态 asset、后端前缀及 Actuator 隔离符合契约 |
| B10 | `deploy/tests/compose-config.test.mjs`; `deploy/compose.yml` | Compose 配置解析通过；`postgres`、`app`、`caddy` 全部达到 healthy，随后成功清理 |
| B11 | `web/src/tests/contract-sync.spec.ts`; `web/scripts/assert-contract-sync.mjs` | `pnpm contract:check` 状态为 0，生成类型与后端 OpenAPI 一致 |
| B12 | `web/src/tests/ci-workflow.spec.ts`; `.github/workflows/ci.yml` | CI 静态测试 4/4 通过；五个只读验证 job、OpenAPI artifact 传递和禁止发布能力断言通过 |

## 聚合命令结果

| 命令 | 结果 |
|---|---|
| `server/.\mvnw.cmd verify` | 状态 0，23 个后端测试通过，JAR 构建成功 |
| `pnpm install --frozen-lockfile` | 状态 0，pnpm 11.9.0，lockfile 无改写需求 |
| `pnpm lint` | 状态 0，无 warning |
| `pnpm typecheck` | 状态 0 |
| `pnpm test` | 状态 0，6 个测试文件、26 个测试通过 |
| `pnpm contract:check` | 状态 0 |
| `pnpm build` | 状态 0，`web/dist/index.html` 与生产 asset 已生成 |
| `docker compose ... config --quiet` | 状态 0 |
| `docker compose ... up -d --build --wait` | 状态 0，三个服务 healthy |
| `pnpm test:e2e -- foundation-routing.spec.ts` | 状态 0，3 个 Playwright 测试通过 |
| `docker compose ... down --volumes` | 状态 0，三个容器、网络和 `studystack_postgres-data` 已删除 |

首次聚合 `verify` 暴露 `StudyStackModulesTest` 的基础类型白名单未包含 Task 7 已批准的 `shared/config/ProductionEnvironmentGuard.java`。白名单仅增加该精确路径，业务模块约束未放宽；定向测试和后端全量验证随后通过。

## 数据与安全结论

- 数据库迁移当前只有 `server/src/main/resources/db/migration/V1__baseline.sql`。V1 只建立无业务 schema 基线，不创建登录、内容、作品、评论、上传或后台业务表。
- PostgreSQL 不可连接和 Flyway checksum 不一致均由自动化失败路径覆盖；未配置内存数据库降级。
- Hibernate DDL 策略为 `validate`，schema 变更只归 Flyway 所有。
- 示例配置仅含公开值和 `EXAMPLE_ONLY_` sentinel，不含真实密钥；生产 profile 拒绝缺失、空值或 sentinel 数据库密码。
- 旧版 Swagger 注解扫描、范围外基础设施扫描、真实密钥形态扫描和模糊占位词扫描均为零命中。
- CI 根权限为 `contents: read`，不包含 deployment environment、registry login、镜像推送、Release、远程 shell 或远程资源创建。

## 未运行项

- 未触发 GitHub 托管的 CI。Task 11 已对 workflow 做静态契约验证；本次按用户要求不提交、不推送，因此没有创建远程运行。
- 未执行公网部署、域名、TLS、备份恢复、生产监控或自动回滚；这些均为 P0 非目标。
- 未运行 P1 业务测试或创建 P1 文件，因为 P1 尚未获得用户授权。

## 仓库与只读参考项目

StudyStack 根目录当前存在 `.git` 元数据，与实施计划中“非 Git 仓库”的历史假设不同。本次 Task 12 未读取当前分支状态，也未执行 add、commit、push 或远程仓库操作。

只读参考项目结束状态为：

```text
## codex/new-feature-development
```

该输出与 P0 规格开始时记录的状态一致，没有文件状态行。全过程未在 `C:\softWare\project\latest\skillhub` 执行修改、创建、删除、格式化、构建或生成操作。

## 下一阶段前置条件

1. 用户审阅本文件及 P0 实现证据，并明确确认 P0 验收通过。
2. 用户批准对应 P1 产品、技术和实施规格后，才能创建登录或其他业务代码。
3. 在上述两项完成前，P1 保持未授权状态。
