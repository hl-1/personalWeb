# StudyStack

StudyStack 是一个基于 Java 21、Spring Boot、Spring Modulith、Vue 3 和 PostgreSQL 的个人网站项目。当前仓库已完成 P0 工程底座，尚未实现登录、文章、作品、评论、上传或后台业务。

## 技术基线

- 后端：Java 21、Spring Boot 3.5、Spring Modulith、PostgreSQL、Flyway、Actuator、OpenAPI 3
- 前端：Vue 3、TypeScript、Vite、Vue Router、TanStack Query、Pinia、Zod、pnpm
- 验证：JUnit、Testcontainers、Vitest、Playwright、Docker Compose、GitHub Actions
- 入口：Caddy 提供 Vue 静态文件和 SPA fallback，并代理 `/api`、`/oauth2`、`/login/oauth2`

## 本地启动

需要 Java 21、pnpm 和 Docker Desktop。所有 PowerShell 命令使用 PowerShell 7。

```powershell
pwsh -NoLogo -NoProfile -Command "docker compose --env-file .env.example -f deploy/compose.yml up -d --build --wait"
```

默认入口为 `http://localhost:8080`。若 8080 已被占用，可在当前终端设置其他端口：

```powershell
pwsh -NoLogo -NoProfile -Command '$env:CADDY_HTTP_PORT="18080"; docker compose --env-file .env.example -f deploy/compose.yml up -d --build --wait'
```

停止并清理本地测试数据：

```powershell
pwsh -NoLogo -NoProfile -Command "docker compose --env-file .env.example -f deploy/compose.yml down --volumes"
```

## 验证入口

```powershell
pwsh -NoLogo -NoProfile -Command "Set-Location server; .\mvnw.cmd verify"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm install --frozen-lockfile"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm lint"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm typecheck"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm test"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm contract:check"
pwsh -NoLogo -NoProfile -Command "Set-Location web; pnpm build"
```

完整 Compose 与 Playwright 验收命令见技术规格和实施进度。

## 文档

- [P0 Product Spec](specs/features/P0-FOUNDATION-001/PRODUCT.md)
- [P0 Tech Spec](specs/features/P0-FOUNDATION-001/TECH.md)
- [P0 Implementation Plan](specs/features/P0-FOUNDATION-001/IMPLEMENTATION_PLAN.md)
- [P0 验证进度](specs/PROGRESS.md)
- [AI 上下文衔接](docs/AI-CONTEXT-HANDOFF.md)
- [项目交付路线](docs/AI-PROJECT-DELIVERY-PLAN.md)

P0 当前等待用户验收。进入后续业务阶段前，必须先批准对应的 PRODUCT、TECH 和实施计划。
