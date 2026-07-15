# StudyStack P0 Foundation Prompt

## 使用方法

将下面完整提示词交给 AI。AI 本轮只能创建 P0 的三份规格文档，完成后必须停止，不能继续生成工程代码。

## Prompt

```text
请先读取并遵守以下全局规则：

C:\Users\6666\.codex\AGENTS.md

总体设计文档：

C:\softWare\project\workDoc\java-personal-website-design.md

只读参考项目：

C:\softWare\project\latest\skillhub

新项目目标目录：

C:\softWare\project\studystack

## 一、强制边界

1. 参考项目只能用于分析工程结构、认证、存储、测试和 CI 模式。
2. 禁止修改、创建、删除、格式化或生成参考项目中的任何文件。
3. 操作前后都必须执行：

   git -C C:\softWare\project\latest\skillhub status --short --branch

4. 如果参考项目出现任何新增、修改或删除，立即停止并报告。
5. 不得整体复制参考项目，不得复制其业务名称、权限模型、接口或企业业务代码。
6. 所有 PowerShell 操作必须使用 PowerShell 7（pwsh）。
7. 不得提交、推送或创建远程仓库，除非我后续明确要求。

## 二、本次任务范围

本次只建设 Phase 0 工程底座，不实现具体业务功能。

暂时不得实现：

- GitHub OAuth登录
- 本地账号注册
- 文章和分类
- 作品集
- 评论和审核
- 文件上传
- 管理后台
- 服务器部署

## 三、规格先行

在编写任何工程代码前，只允许创建以下规格文件：

specs/features/P0-FOUNDATION-001/PRODUCT.md
specs/features/P0-FOUNDATION-001/TECH.md
specs/features/P0-FOUNDATION-001/IMPLEMENTATION_PLAN.md

规格文件创建在：

C:\softWare\project\studystack\specs\features\P0-FOUNDATION-001

### PRODUCT.md要求

从开发者和运维人员视角定义可观察行为，并使用稳定编号：

- B1：新环境能够按照统一命令初始化和启动项目。
- B2：Spring Boot能够连接 PostgreSQL并执行 Flyway迁移。
- B3：Vue前端能够通过同源 `/api` 访问后端。
- B4：开发和测试环境能够访问 OpenAPI和 Swagger UI。
- B5：生产环境默认不向匿名用户开放 OpenAPI和 Swagger UI。
- B6：配置、源码、镜像和前端构建产物中不存在真实密钥。
- B7：前端和后端构建、测试、类型检查可以重复执行。
- B8：直接刷新 Vue深层路由不会返回 404。
- B9：Spring Modulith模块边界能够通过自动化测试验证。
- B10：只读参考项目在整个过程中保持零改动。

PRODUCT还必须包含：

- 问题和目标
- 使用者
- 正常行为
- 失败行为
- 边缘情况
- 成功标准
- 验证方式
- 非目标

### TECH.md要求

必须记录：

- 新项目目录结构
- 单 Maven后端工程和按业务包划分的 Spring Modulith边界
- Java、Spring Boot和依赖版本选择策略
- Vue 3、TypeScript、Vite和 pnpm配置
- Vue Router
- TanStack Query for Vue
- Pinia，仅用于必要的客户端状态
- Zod运行时校验
- PostgreSQL、Flyway和 Testcontainers
- Spring Security和 OAuth 2.0基础依赖，但不实现登录业务
- springdoc-openapi和 OpenAPI 3
- Caddy同源代理与 SPA fallback
- Docker Compose开发结构
- 环境变量和密钥隔离
- 前后端测试结构
- GitHub Actions基础门禁
- 日志、健康检查和错误结构
- 发布和回滚的预留边界
- 风险与非目标

TECH必须建立：

PRODUCT行为编号 -> 测试文件 -> 验证命令

的完整映射。

### IMPLEMENTATION_PLAN.md要求

实施计划必须：

1. 按可独立验证的小任务拆分。
2. 每个任务列出精确的创建、修改和测试文件路径。
3. 每项行为按照 RED、GREEN、REFACTOR执行。
4. 明确测试失败时的预期原因。
5. 明确测试通过时的预期结果。
6. 包含前端、后端、OpenAPI、Docker和 CI验证命令。
7. 不得使用 TODO、TBD、“补充适当测试”等模糊描述。
8. 不得提前加入 P0范围外的业务代码。

三份规格写完后必须立即停止，先向我汇报：

- 创建了哪些规格文件
- 关键架构选择
- 模块和目录结构
- PRODUCT与测试映射
- 仍需我确认的事项

未经我明确确认，不得继续创建工程代码。

## 四、确认后实施的技术方案

我确认三份规格后，再按照实施计划建设以下骨架。

### 后端

- Java 21 LTS
- Spring Boot 3.x仍受维护的补丁版本
- Maven Wrapper
- 单 Maven工程
- Spring MVC REST API
- Spring Security
- OAuth 2.0 Client
- Spring Data JPA
- PostgreSQL
- Flyway
- Spring Modulith
- Bean Validation
- Actuator
- springdoc-openapi
- JUnit 5
- Testcontainers

后端包边界至少预留：

- content
- portfolio
- identity
- comment
- media
- shared

P0只创建边界和必要骨架，不实现业务类。

### OpenAPI规范

使用 OpenAPI 3新版注解：

- `@Tag`
- `@Operation`
- `@Parameter`
- `@Schema`
- `@ApiResponse` / `@ApiResponses`
- `@SecurityRequirement`
- `@OpenAPIDefinition`
- `@SecurityScheme`

禁止使用旧版：

- `@Api`
- `@ApiOperation`
- `@ApiParam`
- `@ApiModel`
- `@ApiModelProperty`

开发和测试环境提供：

- `/v3/api-docs`
- Swagger UI

生产环境默认关闭，或仅允许管理员访问。

### 前端

- Vue 3
- TypeScript
- Vite
- pnpm
- Vue Router
- TanStack Query for Vue
- Pinia
- Zod
- Vitest
- Vue Test Utils
- Playwright

前端目录至少划分：

- api
- features
- pages
- router
- shared

生产环境不运行 Node.js。Vue构建产物由 Caddy提供。

### 基础设施

- PostgreSQL开发容器
- 最小 Docker Compose
- Caddy HTTPS和反向代理配置骨架
- `/api/**` 代理到 Spring Boot
- `/oauth2/**` 和 `/login/oauth2/**` 代理到 Spring Boot
- Vue页面路由使用 `index.html` fallback
- `.env.example`
- 不包含真实密钥
- GitHub Actions基础检查

P0不得引入：

- Redis
- Elasticsearch
- 消息队列
- MinIO
- 微服务
- Kubernetes
- 服务注册中心

## 五、P0验收标准

完成工程骨架后必须验证：

1. 后端能够编译和启动。
2. Spring上下文测试通过。
3. Spring Modulith模块边界测试通过。
4. PostgreSQL Testcontainers集成测试通过。
5. Flyway能够创建初始 Schema。
6. `/actuator/health` 可访问。
7. `/v3/api-docs` 能生成有效 OpenAPI。
8. Swagger UI在开发环境可访问。
9. Vue能够完成生产构建。
10. Vue类型检查和 Vitest通过。
11. Playwright能够打开前端首页骨架。
12. Vue深层路由直接刷新不会返回 404。
13. Docker Compose配置能够通过解析检查。
14. 前端构建产物中不存在密钥。
15. 不存在旧版 Swagger注解。
16. 没有实现 P0范围外的业务功能。
17. 参考项目保持零改动。
18. `git diff --check` 通过。
19. `git status --short --branch` 只显示本任务相关文件。

请现在只完成三份 P0规格文档，然后停止并等待我确认。
```

## 确认后的续接提示词

```text
P0 的 PRODUCT、TECH 和 IMPLEMENTATION_PLAN 已审阅通过。
请严格按 IMPLEMENTATION_PLAN 一次执行一个任务，遵循 RED、GREEN、REFACTOR。
每个任务完成后运行对应验证并汇报证据，不得提前实现 P1 及后续业务功能。
```
