---
task_id: P1-IDENTITY-001
title: StudyStack 身份与权限
phase: P1
status: draft
created: 2026-07-16
updated: 2026-07-16
design_refs:
  - C:/softWare/project/workDoc/java-personal-website-design.md
  - docs/AI-PROJECT-DELIVERY-PLAN.md
  - specs/features/P0-FOUNDATION-001/PRODUCT.md
  - specs/features/P0-FOUNDATION-001/TECH.md
---

# StudyStack P1 Identity Implementation Plan

> **For agentic workers:** 使用 `superpowers:executing-plans` 按 Task 执行。用户每次只授权一个 Task；当前 Task 验证完成后必须停止，不得自动进入下一 Task。

**Goal:** 在 P0 模块化单体基础上实现 GitHub OAuth、PostgreSQL Session、CSRF、用户身份绑定、管理员授权和 Vue 登录体验，不引入 P2 及后续业务。

**Architecture:** `identity` 模块拥有用户、GitHub 身份、登录和授权规则；Spring Security 负责 OAuth 协议、Session Fixation 和 CSRF，Spring Session JDBC 将 Session 存入现有 PostgreSQL。Vue 通过同源认证 API 和 TanStack Query读取认证状态，所有响应先经过 Zod运行时校验。

**Tech Stack:** Java 21、Spring Boot 3.5.16、Spring Security、OAuth2 Client、Spring Session JDBC、Spring Data JPA、PostgreSQL、Flyway、OpenAPI 3、Vue 3、TypeScript、TanStack Query、Zod、Vitest、Playwright。

---

## 0. 使用方式与执行门禁

执行某个任务时，只需向 AI 发送：

```text
请读取 C:\Users\6666\.codex\AGENTS.md 和
C:\softWare\project\studystack\specs\features\P1-IDENTITY-001\IMPLEMENTATION_PLAN.md。
本次只执行 Task N。严格完成该 Task 的 RED、GREEN、REFACTOR 和验证，完成后停止，不提交、不推送。
```

- 本计划获用户明确批准后才能执行。
- 每次只执行一个 Task，只修改该 Task 的 Files 列表。
- 预期 RED 失败是 TDD 证据，不是停止条件；意外通过时必须先解释现有行为。
- 所有 PowerShell命令遵守 `C:\Users\6666\.codex\AGENTS.md`，Maven `-D...` 参数使用 `--%`。
- 未经用户单独授权，不执行 commit、push、PR、部署或真实 GitHub OAuth App配置。
- `C:\softWare\project\latest\skillhub` 始终只读。
- 不实现用户名密码、JWT、Redis、文章、作品、评论、上传和后台业务 CRUD。
- P0 当前进度文件仍标记为 `awaiting-user-acceptance`；Task 1 开始前必须确认用户已验收 P0，但不得借 P1 修改 P0 历史证据。

## 1. 固定契约

- OAuth入口：`/oauth2/authorization/github`；回调：`/login/oauth2/code/github`。
- GitHub `id` 是唯一身份依据，必须为大于 0 的 64 位整数；`login` 必须非空。
- `name` 缺失时回退为 `login`；无效或缺失头像映射为 `null`；email不作为必需字段或绑定键。
- 本地用户公开 ID使用 UUID；外部身份唯一键为 `(provider, provider_subject)`，provider固定为 `github`。
- 状态只有 `ACTIVE`、`DISABLED`；角色只有 `USER`、`ADMIN`。
- 所有登录用户拥有 USER；`STUDYSTACK_ADMIN_GITHUB_IDS` 白名单用户同时拥有 ADMIN。
- Session使用 Spring Session JDBC，最大空闲时间 24小时，每 10分钟清理过期记录。
- Cookie名为 `STUDYSTACK_SESSION`，始终 HttpOnly、SameSite=Lax、Path=/；dev/test允许非 Secure，prod强制 Secure。
- `GET /api/v1/auth/me` 匿名返回 `200`、`authenticated=false`、`user=null`。
- `GET /api/v1/auth/csrf` 返回非空 `token` 和 `headerName=X-CSRF-TOKEN`。
- `POST /api/v1/auth/logout` 校验 CSRF，成功或重复退出均返回 204。
- 认证用户响应只包含本地 UUID、login、displayName、avatarUrl和 roles；不得返回 GitHub ID、email、token或 Session ID。
- OAuth失败码固定为 `oauth_denied`、`invalid_profile`、`identity_conflict`、`account_disabled`、`login_failed`。
- Vue认证数据由 TanStack Query管理，Pinia不复制；接口数据经 Zod解析后才能进入 union/enum。

## 2. 文件责任图

| 区域 | 责任 | 禁止内容 |
|---|---|---|
| `identity/domain` | 用户、外部身份、状态和仓储端口 | Spring MVC、OAuth claims、前端角色判断 |
| `identity/application` | claims归一化、身份绑定、当前用户和授权用例 | 跨模块 Repository调用、原始 token持久化 |
| `identity/infrastructure` | JPA、Spring Session、Security和 OAuth适配 | 文章、评论、上传业务 |
| `identity/web` | auth Controller、DTO和 OpenAPI注解 | Entity直接返回、原始 provider错误 |
| `web/src/features/auth` | runtime schema、API client、query和 CSRF内存状态 | JWT、Session ID、Pinia用户副本 |
| `web/src/views`、`router` | 登录页、占位后台页和路由体验 | 服务端权限替代、后台 CRUD |

## Task 1: Spring Security依赖与配置边界

**覆盖行为：** I6、I10、I13

**Files:**

- Modify: `server/pom.xml`
- Modify: `server/src/main/resources/application.yml`
- Modify: `server/src/main/resources/application-dev.yml`
- Modify: `server/src/main/resources/application-prod.yml`
- Modify: `server/src/test/resources/application-test.yml`
- Modify: `server/src/test/java/com/studystack/foundation/StudyStackModulesTest.java`
- Create: `server/src/main/java/com/studystack/identity/config/IdentitySecurityProperties.java`
- Create: `server/src/main/java/com/studystack/identity/config/IdentityConfiguration.java`
- Create: `server/src/main/java/com/studystack/identity/infrastructure/security/SecurityConfiguration.java`
- Create: `server/src/test/java/com/studystack/identity/config/IdentitySecurityPropertiesTest.java`
- Create: `server/src/test/java/com/studystack/identity/config/IdentitySecurityBaselineTest.java`

### RED

- [ ] 先写配置测试，要求依赖中存在 Security、OAuth2 Client、Spring Session JDBC和 `spring-security-test`；要求 GitHub registration只申请 `read:user`，Session JDBC禁止自动建表，管理员 ID配置绑定到服务端属性。
- [ ] 修改模块测试：从“所有业务模块均为空”改为只有 `admin`、`comment`、`content`、`media`、`portfolio` 仍为空；`identity` 允许 P1实现但仍受 Modulith依赖验证。
- [ ] 在依赖和配置尚未加入时运行：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=IdentitySecurityPropertiesTest,IdentitySecurityBaselineTest,StudyStackModulesTest test'
```

Expected: 身份测试因缺少依赖、配置属性或 SecurityFilterChain失败；P0模块测试继续识别七个模块。

### GREEN

- [ ] 添加由 Spring Boot 3.5.16管理版本的 `spring-boot-starter-security`、`spring-boot-starter-oauth2-client`、`spring-session-jdbc` 和 test scope `spring-security-test`。
- [ ] 创建 `IdentitySecurityProperties`，集中绑定管理员 ID原始配置、Session超时和 Cookie策略；错误信息只能显示变量名，不能回显值。
- [ ] 创建最小 `SecurityConfiguration`，保持 P0公开健康和开发文档行为，不创建用户、OAuth handler或业务 API；后续 Task在同一配置上收紧授权。
- [ ] dev/test使用明确示例 OAuth配置；prod只读取 `GITHUB_CLIENT_ID`、`GITHUB_CLIENT_SECRET` 和 `STUDYSTACK_ADMIN_GITHUB_IDS`，不得提供可运行默认秘密。
- [ ] 重跑定向测试。

Expected: 配置和模块测试通过；现有 P0 OpenAPI与 Actuator定向测试不因加入 Security而回归。

### REFACTOR

- [ ] 将配置规范化和 profile差异保持在配置类/YAML中，Security配置不得自行解析逗号字符串。
- [ ] 执行：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=IdentitySecurityPropertiesTest,IdentitySecurityBaselineTest,StudyStackModulesTest,OpenApiDevelopmentIntegrationTest,OpenApiProductionIntegrationTest,ActuatorIntegrationTest test'
```

Expected: 新旧测试全部通过，生产 OpenAPI仍为 404，Actuator契约不变。

## Task 2: 身份与 Spring Session数据迁移

**覆盖行为：** I2、I3、I5、I11

**Files:**

- Create: `server/src/main/resources/db/migration/V2__identity_and_sessions.sql`
- Create: `server/src/test/java/com/studystack/identity/infrastructure/IdentitySchemaIntegrationTest.java`

### RED

- [ ] 使用 PostgreSQL Testcontainers写迁移测试，断言存在 `identity_user_account`、`identity_external_identity`、`spring_session`、`spring_session_attributes`；断言外部身份唯一键、用户状态 CHECK、Session索引和外键。
- [ ] 迁移文件不存在时运行：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=IdentitySchemaIntegrationTest test'
```

Expected: 测试明确报告 V2或目标表不存在。

### GREEN

- [ ] 创建 Flyway V2。用户表包含 UUID主键、login、display_name、可空 avatar_url、status、created_at、updated_at、last_login_at和 version。
- [ ] 外部身份表包含 UUID主键、user_id、provider、provider_subject和时间字段；建立 `(provider, provider_subject)` 与 `(user_id, provider)` 唯一约束。
- [ ] 使用 Spring Session PostgreSQL兼容表结构和索引；所有 schema由 Flyway拥有，Hibernate与 Spring Session均不得自动建表。
- [ ] 重跑定向测试。

Expected: V1、V2按序成功，约束和索引断言通过。

### REFACTOR

- [ ] 增加失败测试：重复 provider subject、未知用户状态、孤立 identity和孤立 Session attributes必须被数据库拒绝。
- [ ] 执行：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=IdentitySchemaIntegrationTest,PostgresFlywayIntegrationTest test'
```

Expected: P0 baseline和 P1 V2同时通过，Hibernate仍为 `validate`。

## Task 3: 用户领域模型与 Repository

**覆盖行为：** I2、I3、I11

**Files:**

- Create: `server/src/main/java/com/studystack/identity/domain/AccountStatus.java`
- Create: `server/src/main/java/com/studystack/identity/domain/UserAccount.java`
- Create: `server/src/main/java/com/studystack/identity/domain/ExternalIdentity.java`
- Create: `server/src/main/java/com/studystack/identity/domain/UserAccountRepository.java`
- Create: `server/src/main/java/com/studystack/identity/domain/ExternalIdentityRepository.java`
- Create: `server/src/test/java/com/studystack/identity/infrastructure/IdentityRepositoryIntegrationTest.java`

### RED

- [ ] 写 Repository集成测试，覆盖 UUID持久化、GitHub身份查找、乐观锁、ACTIVE/DISABLED、唯一键和允许为空的头像。
- [ ] 实体与仓储不存在时运行：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=IdentityRepositoryIntegrationTest test'
```

Expected: 编译因领域类型或仓储缺失失败。

### GREEN

- [ ] 创建最小 JPA模型并精确映射 V2；Entity不暴露 public setter集合，不把 provider subject当作用户公开 ID。
- [ ] Repository只提供 P1所需的按 UUID和 `(provider, providerSubject)` 查询能力，不增加通用动态查询。
- [ ] 重跑定向测试。

Expected: 正常持久化、状态读取和唯一约束测试通过。

### REFACTOR

- [ ] 集中状态转换；未知数据库状态不得回退为 ACTIVE。确保 `toString`、异常和日志不输出身份 subject。
- [ ] 运行 Task 2、3定向测试和 Modulith验证。

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=IdentitySchemaIntegrationTest,IdentityRepositoryIntegrationTest,StudyStackModulesTest test'
```

Expected: schema、Repository与模块边界同时通过。

## Task 4: GitHub claims集中归一化

**覆盖行为：** I4

**Files:**

- Create: `server/src/main/java/com/studystack/identity/application/GitHubIdentityClaims.java`
- Create: `server/src/main/java/com/studystack/identity/application/GitHubClaimsNormalizer.java`
- Create: `server/src/main/java/com/studystack/identity/application/InvalidGitHubClaimsException.java`
- Create: `server/src/test/java/com/studystack/identity/application/GitHubClaimsNormalizerTest.java`

### RED

- [ ] 参数化测试 `id` 的 Long、Integer和数字字符串；拒绝缺失、零、负数、浮点、布尔、溢出和非数字值。
- [ ] 测试 login trim、空 login拒绝、name回退、HTTPS头像保留以及缺失/HTTP/非法头像映射为 null。
- [ ] 运行：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=GitHubClaimsNormalizerTest test'
```

Expected: 编译因 normalizer不存在失败。

### GREEN

- [ ] 实现一个纯函数式 normalizer，输出不可变 `GitHubIdentityClaims`；provider subject使用十进制数字字符串。
- [ ] 异常只携带固定原因码和字段名，不保留完整 claims或非法原值。
- [ ] 重跑测试。

Expected: 合法映射和全部拒绝/兼容路径通过。

### REFACTOR

- [ ] 扫描 identity源码，确保没有第二处直接读取 `id`、`login`、`name`、`avatar_url` 的规则。
- [ ] 运行 normalizer和模块测试。

Expected: 归一化规则只有一个实现位置，测试通过。

## Task 5: 首次、重复与并发身份绑定

**覆盖行为：** I2、I3、I11

**Files:**

- Create: `server/src/main/java/com/studystack/identity/application/AuthenticatedIdentity.java`
- Create: `server/src/main/java/com/studystack/identity/application/IdentityBindingService.java`
- Create: `server/src/test/java/com/studystack/identity/application/IdentityBindingServiceIntegrationTest.java`

### RED

- [ ] 使用 Testcontainers写集成测试：首次登录创建一个用户和一个身份；重复登录保留用户 UUID并更新可变资料；DISABLED拒绝；两个线程并发首次登录最终只有一条绑定。
- [ ] 运行：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=IdentityBindingServiceIntegrationTest test'
```

Expected: 编译因绑定服务不存在失败。

### GREEN

- [ ] 在单个事务内按 `(github, subject)` 查找或创建身份；首次创建用户，重复登录更新 login、displayName、avatarUrl和 lastLoginAt。
- [ ] 唯一键竞争时只捕获已确认的身份唯一约束，再重新读取同一 subject；其他完整性错误继续抛出。
- [ ] 返回最小 `AuthenticatedIdentity`，不包含 OAuth token。
- [ ] 重跑测试。

Expected: 首次、重复、禁用和并发路径通过，数据库无重复行。

### REFACTOR

- [ ] 验证事务失败不留下孤立用户；重复登录的无变化资料不替换用户 UUID或创建新身份。
- [ ] 执行 Task 3、5测试。

Expected: 回滚与幂等路径通过。

## Task 6: OAuth成功、失败与 token清理

**覆盖行为：** I1、I2、I4、I9、I13

**Files:**

- Modify: `server/src/main/java/com/studystack/identity/infrastructure/security/SecurityConfiguration.java`
- Create: `server/src/main/java/com/studystack/identity/infrastructure/security/StudyStackPrincipal.java`
- Create: `server/src/main/java/com/studystack/identity/infrastructure/security/GitHubOAuth2UserService.java`
- Create: `server/src/main/java/com/studystack/identity/infrastructure/security/OAuthLoginSuccessHandler.java`
- Create: `server/src/main/java/com/studystack/identity/infrastructure/security/OAuthLoginFailureHandler.java`
- Create: `server/src/test/java/com/studystack/identity/support/GitHubOAuthProviderStub.java`
- Create: `server/src/test/java/com/studystack/identity/infrastructure/security/OAuthLoginIntegrationTest.java`

### RED

- [ ] 使用进程内 HTTP stub模拟 authorization、token和 user-info端点；测试登录入口302、回调成功、provider拒绝、claims非法、身份冲突和禁用账号。
- [ ] 断言成功跳转 `/login?status=success`，失败只产生固定安全码；URL和日志不含 code、token或完整 claims。
- [ ] 运行：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=OAuthLoginIntegrationTest test'
```

Expected: OAuth登录尚未配置，入口或回调测试失败。

### GREEN

- [ ] `GitHubOAuth2UserService`调用 Task 4 normalizer和 Task 5 binding service，创建可序列化最小 principal。
- [ ] 成功 handler轮换/保留新 Session、移除不再需要的 authorized client并安全跳转；失败 handler映射固定错误码，不透传 provider消息。
- [ ] SecurityFilterChain启用 oauth2Login，不自实现 state、授权码交换或 token校验。
- [ ] 重跑集成测试。

Expected: 所有模拟 provider路径通过，数据库和 Session中不存在长期 OAuth token副本。

### REFACTOR

- [ ] 检查 handler只做适配，claims规则和身份事务仍分别集中在 Task 4、5类中。
- [ ] 重跑 OAuth、binding和 normalizer测试。

Expected: 分层边界清晰且行为不变。

## Task 7: PostgreSQL Session与 Cookie策略

**覆盖行为：** I5、I6、I9

**Files:**

- Modify: `server/src/main/resources/application.yml`
- Modify: `server/src/main/resources/application-dev.yml`
- Modify: `server/src/main/resources/application-prod.yml`
- Modify: `server/src/test/resources/application-test.yml`
- Create: `server/src/test/java/com/studystack/identity/infrastructure/security/SessionPolicyIntegrationTest.java`

### RED

- [ ] 测试 JDBC Session记录、24小时最大空闲、10分钟清理 cron、登录前后 Session ID变化、退出删除、过期拒绝。
- [ ] 使用同一 PostgreSQL容器先后启动两个应用 context，证明有效 Cookie在应用重启后仍能恢复认证。
- [ ] 分 profile断言 Cookie：共同 HttpOnly/Lax/Path=/，dev/test非 Secure，prod Secure。
- [ ] 运行：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=SessionPolicyIntegrationTest test'
```

Expected: Session/Cookie配置尚不完整，至少一个契约失败。

### GREEN

- [ ] 配置 `spring.session.store-type=jdbc`、`initialize-schema=never`、24小时 timeout和每10分钟清理；配置 `STUDYSTACK_SESSION` Cookie差异。
- [ ] 确保 principal可序列化且只保存恢复认证所需字段，不保存 OAuth token、完整 claims或管理员角色快照。
- [ ] 重跑测试。

Expected: 持久化、重启恢复、轮换、过期、退出和 profile契约通过。

### REFACTOR

- [ ] 增加 Session表内容安全断言，禁止序列化属性出现测试 token、client secret和完整 claims JSON。
- [ ] 执行 OAuth与 Session测试。

Expected: 功能和敏感数据断言同时通过。

## Task 8: 当前用户、CSRF与退出 API

**覆盖行为：** I7、I8、I9、I13

**Files:**

- Modify: `server/src/main/java/com/studystack/shared/openapi/OpenApiConfiguration.java`
- Modify: `server/src/main/java/com/studystack/identity/infrastructure/security/SecurityConfiguration.java`
- Create: `server/src/main/java/com/studystack/identity/web/AuthController.java`
- Create: `server/src/main/java/com/studystack/identity/web/AuthStateResponse.java`
- Create: `server/src/main/java/com/studystack/identity/web/AuthUserResponse.java`
- Create: `server/src/main/java/com/studystack/identity/web/CsrfTokenResponse.java`
- Create: `server/src/test/java/com/studystack/identity/web/AuthControllerIntegrationTest.java`
- Create: `server/src/test/java/com/studystack/identity/web/AuthOpenApiIntegrationTest.java`

### RED

- [ ] 测试匿名/已登录 `/api/v1/auth/me`、角色稳定顺序、可空头像、CSRF响应、缺失/错误/正确 token退出和重复退出。
- [ ] OpenAPI测试要求三个路径、DTO schema、200/204/403响应、Session cookie安全方案和新版注解。
- [ ] 运行：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=AuthControllerIntegrationTest,AuthOpenApiIntegrationTest test'
```

Expected: auth Controller和契约不存在，测试失败。

### GREEN

- [ ] 实现三个固定 API；Entity和 GitHub ID不得进入 DTO。`/me`匿名不是异常，返回判别状态。
- [ ] CSRF token从 Spring Security请求属性读取；logout清理 SecurityContext、Session和 Cookie并保持204幂等。
- [ ] 使用 `io.swagger.v3.oas.annotations` 新版注解，OpenAPI版本更新为 P1并集中声明 Session安全方案。
- [ ] 重跑测试。

Expected: 运行时和 OpenAPI契约同时通过。

### REFACTOR

- [ ] 抽取唯一的 principal到响应映射，角色只允许 USER、ADMIN并固定顺序；错误响应不含堆栈和安全值。
- [ ] 重跑 auth、旧注解政策和生产文档关闭测试。

Expected: 新接口完整，P0 OpenAPI安全门禁不回归。

## Task 9: 管理员授权与禁用账号

**覆盖行为：** I10、I11

**Files:**

- Modify: `server/src/main/java/com/studystack/identity/config/IdentitySecurityProperties.java`
- Modify: `server/src/main/java/com/studystack/identity/infrastructure/security/SecurityConfiguration.java`
- Create: `server/src/main/java/com/studystack/identity/application/AdminGithubIdPolicy.java`
- Create: `server/src/main/java/com/studystack/identity/infrastructure/security/ActiveAccountFilter.java`
- Create: `server/src/test/java/com/studystack/identity/application/AdminGithubIdPolicyTest.java`
- Create: `server/src/test/java/com/studystack/identity/infrastructure/security/AuthorizationIntegrationTest.java`

### RED

- [ ] 测试管理员 CSV trim、去重和稳定角色；拒绝空项、零、负数、浮点、非数字和溢出。dev/test允许空，prod拒绝空白名单。
- [ ] 测试游客访问 admin为401、普通用户403、管理员通过 Security过滤后到达 MVC 404；证明不是前端守卫授权。
- [ ] 将已登录用户改为 DISABLED，后续受保护请求必须清除 Session并返回401。
- [ ] 运行：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=AdminGithubIdPolicyTest,AuthorizationIntegrationTest test'
```

Expected: 白名单策略、admin授权或禁用过滤缺失导致失败。

### GREEN

- [ ] 集中实现 `AdminGithubIdPolicy`，每次根据 principal GitHub ID和当前服务端白名单计算 ADMIN，不信任 Session角色快照。
- [ ] `/api/v1/admin/**`要求 ADMIN；`ActiveAccountFilter`检查当前账号状态，禁用时使 Session失效。
- [ ] 重跑测试。

Expected: 四类主体权限矩阵和禁用路径通过。

### REFACTOR

- [ ] 检查授权规则只有一个白名单解析器；错误消息只显示环境变量名和规则，不显示管理员 ID集合。
- [ ] 重跑配置、授权、Session和 `/me`测试。

Expected: 权限来源一致，修改白名单后不需要修改数据库角色。

## Task 10: 前端认证 schema、API client与 Query

**覆盖行为：** I7、I8、I9、I12

**Files:**

- Create: `web/src/features/auth/auth-schema.ts`
- Create: `web/src/features/auth/auth-schema.spec.ts`
- Create: `web/src/features/auth/auth-client.ts`
- Create: `web/src/features/auth/auth-client.spec.ts`
- Create: `web/src/features/auth/auth-query.ts`
- Create: `web/src/features/auth/auth-query.spec.ts`

### RED

- [ ] Zod测试覆盖匿名/用户/管理员、非法 role、重复 role、缺失 user、伪造额外敏感字段和畸形 URL；只有规范化结果进入应用类型。
- [ ] client测试覆盖相对同源路径、`credentials: same-origin`、CSRF内存缓存、logout前取 token、403清缓存且不自动重试。
- [ ] Query测试要求 `/me`由 TanStack Query拥有，Pinia中不存在用户或角色状态。
- [ ] 运行：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location web; pnpm test -- src/features/auth/auth-schema.spec.ts src/features/auth/auth-client.spec.ts src/features/auth/auth-query.spec.ts'
```

Expected: auth前端模块不存在，测试失败。

### GREEN

- [ ] 创建判别联合 runtime schema和显式 mapping；拒绝未知 role，不用 `as`掩盖数据。
- [ ] 创建最小 fetch client和 query options/composable；CSRF token只存模块内存，logout成功清空认证 query。
- [ ] 重跑测试。

Expected: schema、请求行为和 Query缓存契约通过。

### REFACTOR

- [ ] 统一网络错误、401、403和非法响应错误类型；错误对象不得保存响应中的未知敏感字段。
- [ ] 运行前端 lint、typecheck和定向测试。

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location web; pnpm lint; pnpm typecheck; pnpm test -- src/features/auth'
```

Expected: 无类型断言绕过、lint或测试错误。

## Task 11: Vue登录页、退出与路由守卫

**覆盖行为：** I1、I9、I10、I12

**Files:**

- Modify: `web/src/App.vue`
- Modify: `web/src/router/index.ts`
- Modify: `web/src/router/router.spec.ts`
- Create: `web/src/features/auth/return-to.ts`
- Create: `web/src/features/auth/return-to.spec.ts`
- Create: `web/src/views/LoginView.vue`
- Create: `web/src/views/AdminView.vue`
- Create: `web/src/views/ForbiddenView.vue`
- Create: `web/src/features/auth/auth-view.spec.ts`

### RED

- [ ] 测试 `/login`、`/admin`、`/forbidden`路由；游客进 admin跳 login，普通用户到 forbidden，管理员进入占位页。
- [ ] 参数化测试 returnTo只接受单 `/`开头的站内路径，拒绝 `//`、协议、主机、反斜杠和控制字符。
- [ ] 测试登录按钮指向固定 OAuth入口，安全错误码映射固定文案，未知错误使用通用文案且不直接渲染 query值。
- [ ] 运行：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location web; pnpm test -- src/router/router.spec.ts src/features/auth/return-to.spec.ts src/features/auth/auth-view.spec.ts'
```

Expected: 页面、路由和守卫缺失导致失败。

### GREEN

- [ ] 实现登录页、管理员占位页、无权限页和最小导航；不创建后台 CRUD。
- [ ] returnTo只在校验后写入 sessionStorage，绝不存 token、角色和用户资料；登录成功刷新 `/me`后跳回合法站内路径。
- [ ] 路由守卫等待认证 query结果，但服务端仍是最终授权者。
- [ ] 重跑测试。

Expected: 游客、普通用户、管理员和错误状态页面行为通过。

### REFACTOR

- [ ] 将守卫依赖做成可注入接口，测试不依赖全局 QueryClient或浏览器真实 Session。
- [ ] 运行 lint、typecheck、全量 Vitest和 build。

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location web; pnpm lint; pnpm typecheck; pnpm test; pnpm build'
```

Expected: 前端全量检查通过且 P0页面不回归。

## Task 12: 环境、Caddy、契约同步与 E2E

**覆盖行为：** I1、I6、I12、I13

**Files:**

- Modify: `.env.example`
- Modify: `deploy/compose.yml`
- Modify: `deploy/tests/compose-config.test.mjs`
- Modify: `web/src/tests/config-policy.spec.ts`
- Modify: `web/src/shared/api/generated/openapi.d.ts`
- Create: `web/e2e/auth-routing.spec.ts`
- Create: `server/src/test/java/com/studystack/identity/infrastructure/security/SensitiveAuthenticationDataPolicyTest.java`

### RED

- [ ] 配置测试要求 app容器接收 GitHub client ID/secret和管理员 ID，Caddy继续只代理 `/api`、`/oauth2`、`/login/oauth2`，OAuth秘密不进入 web build args。
- [ ] 后端生成 P1 OpenAPI后运行契约检查，预期已提交类型与新接口不同。
- [ ] E2E使用浏览器路由拦截模拟 `/auth/me`和 `/auth/csrf`，覆盖游客、普通用户、管理员、Session过期和非法响应；不得连接真实 GitHub。
- [ ] 敏感数据扫描测试覆盖源码、示例配置、日志捕获和生成前端产物。
- [ ] 运行定向检查。

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=OpenApiDevelopmentIntegrationTest,SensitiveAuthenticationDataPolicyTest test'
pwsh -NoLogo -NoProfile -Command 'Set-Location web; pnpm contract:check; pnpm test -- src/tests/config-policy.spec.ts'
pwsh -NoLogo -NoProfile -Command 'node --test deploy/tests/compose-config.test.mjs'
```

Expected: 契约类型未同步，新增环境或安全断言至少一项失败。

### GREEN

- [ ] 更新示例环境和 Compose，仅向 app传服务端变量；示例值使用 `EXAMPLE_ONLY_`，不放真实 ID或secret。
- [ ] 保持当前 Caddy代理顺序；仅在测试证明现有配置不满足固定路径时修改 Caddyfile。
- [ ] 生成并提交到工作区的 OpenAPI TypeScript类型，禁止手改生成文件。

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=OpenApiDevelopmentIntegrationTest test'
pwsh -NoLogo -NoProfile -Command 'Set-Location web; pnpm contract:generate; pnpm contract:check'
```

- [ ] 使用 Compose启动本地拓扑并执行 auth E2E，完成后清理容器和测试卷。

Expected: 环境、代理、契约、安全扫描和 E2E通过。

### REFACTOR

- [ ] E2E失败输出必须包含路由、预期身份、实际页面状态和 API错误类别，不打印 token或响应全文。
- [ ] 重跑 Task 12全部定向检查并确认参考项目状态未变化。

## Task 13: P1聚合验收与进度记录

**覆盖行为：** I1-I13

**Files:**

- Modify: `specs/PROGRESS.md`
- Modify: `docs/AI-CONTEXT-HANDOFF.md`

### RED

- [ ] 先逐项运行聚合命令并记录真实失败，不先修改规格掩盖实现差异。

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd verify'
pwsh -NoLogo -NoProfile -Command 'Set-Location web; pnpm install --frozen-lockfile; pnpm lint; pnpm typecheck; pnpm test; pnpm contract:check; pnpm build'
pwsh -NoLogo -NoProfile -Command 'docker compose --env-file .env.example -f deploy/compose.yml config --quiet'
```

Expected: 所有已实现任务通过；任何失败都映射回唯一 Task和 I编号。

### GREEN

- [ ] 只修复属于 P1且由聚合验证暴露的缺口，先重跑最小失败命令，再重跑聚合命令。
- [ ] 使用本地示例配置启动 Compose，运行全部 Playwright后清理；不得使用真实 GitHub secret或公网。
- [ ] 更新进度文件：记录 I1-I13证据、Flyway V2、测试数量、未运行真实 OAuth联调和下一阶段前置条件。
- [ ] 更新交接文档：P1状态只能写为 `awaiting-user-acceptance`，不得直接授权 P2。

Expected: 后端、前端、契约、Compose和 E2E聚合验证全部为状态0，文档证据与实际输出一致。

### REFACTOR

- [ ] 扫描范围、旧 Swagger注解、真实秘密、token日志、前端 `as`绕过和模糊占位。
- [ ] 执行文档与 Git检查：

```powershell
pwsh -NoLogo -NoProfile -Command 'rg -n -e "io\.swagger\.annotations" -e "springfox" -e "@Api\b" -e "@ApiOperation\b" server web specs'
pwsh -NoLogo -NoProfile -Command 'git diff --check; git status --short --branch'
```

Expected: 禁用注解和真实秘密零命中；Git只显示 P1任务相关文件。不得暂存、提交或推送。

## 3. 行为到任务与验证映射

| 行为 | Task | 主要测试 |
|---|---|---|
| I1 OAuth流程 | 6、11、12 | `OAuthLoginIntegrationTest`、`auth-view.spec.ts`、`auth-routing.spec.ts` |
| I2 首次登录 | 2、3、5、6 | `IdentitySchemaIntegrationTest`、`IdentityBindingServiceIntegrationTest` |
| I3 重复与并发登录 | 2、3、5 | `IdentityBindingServiceIntegrationTest` |
| I4 claims规则 | 4、6 | `GitHubClaimsNormalizerTest`、`OAuthLoginIntegrationTest` |
| I5 Session持久化 | 2、7 | `SessionPolicyIntegrationTest` |
| I6 Cookie profile | 1、7、12 | `IdentitySecurityBaselineTest`、`SessionPolicyIntegrationTest` |
| I7 当前用户 | 8、10 | `AuthControllerIntegrationTest`、`auth-schema.spec.ts` |
| I8 CSRF | 8、10 | `AuthControllerIntegrationTest`、`auth-client.spec.ts` |
| I9 退出 | 6、7、8、11 | `SessionPolicyIntegrationTest`、`AuthControllerIntegrationTest` |
| I10 管理员权限 | 1、9、11 | `AuthorizationIntegrationTest`、`router.spec.ts` |
| I11 禁用账号 | 3、5、9 | `IdentityBindingServiceIntegrationTest`、`AuthorizationIntegrationTest` |
| I12 Vue认证体验 | 10、11、12 | auth Vitest、`auth-routing.spec.ts` |
| I13 契约与秘密 | 1、6、8、12 | OpenAPI、config-policy、sensitive-data测试 |

## 4. 最终完成定义

P1只有在以下条件同时满足时完成：

- 13个 Task均由用户逐项授权并完成各自验证。
- GitHub首次、重复、并发、拒绝、异常 claims和禁用账号路径均有自动化证据。
- Session、Cookie、CSRF、退出和管理员权限符合固定契约。
- Vue不信任未经校验的 API数据，不存储 token或 Session副本。
- OpenAPI、generated TypeScript、Zod mapping和实际 Controller一致。
- P0后端、前端、Compose和 Caddy契约无回归。
- 进度状态为等待用户验收；未经用户明确验收，不开始 P2。
