---
project: StudyStack
feature: P3-ADMIN-001
purpose: P3 Task 8 后的新会话快速交接
updated: 2026-07-20
status: task-8-complete-awaiting-task-9-authorization
branch: codex/p3-admin
head: 39d2765
---

# P3 Task 8 后会话交接

本文件是后续 P3 开发的最快恢复入口。Task 1-8 已完成并留在当前未提交工作区；下一个任务是 Task 9。除非用户明确授权，不得开始 Task 9，也不得提交、推送或清理工作区。

## 1. 新会话直接使用

推荐提示：

```text
请读取 C:\Users\6666\.codex\AGENTS.md、
C:\softWare\project\studystack\specs\features\P3-ADMIN-001\SESSION_HANDOFF_AFTER_TASK8.md，
再读取 C:\softWare\project\studystack\specs\features\P3-ADMIN-001\IMPLEMENTATION_PLAN.md 的 Task 9。
当前分支 codex/p3-admin，P3 Task 1-8 已完成且尚未提交。本次只执行 Task 9；使用一次 RED、一次必要 GREEN、一次最终最小聚合，完成后停止，不提交、不推送。
```

上下文优先级：

1. `C:\Users\6666\.codex\AGENTS.md`：本机、Git、测试和代码质量规则。
2. `IMPLEMENTATION_PLAN.md`：当前 Task 的权威文件范围和行为契约。
3. 本文件：已完成状态、实现事实、测试证据和工作区保护项。
4. `docs/AI-CONTEXT-HANDOFF.md`：需要追溯 Task 1-8 细节时再读。
5. P2 handoff：只有需要核对匿名公开行为时再读。

不要在新会话重新分析 Task 1-8；先用 `git status --short --branch` 确认状态仍匹配，再直接进入用户授权的 Task。

## 2. 仓库快照与保护项

- 仓库：`C:\softWare\project\studystack`
- 分支：`codex/p3-admin`
- `HEAD`：`39d2765`，P3 开始时与 `origin/main` 相同。
- P3 Task 1-8 尚未暂存、提交或推送，大量文件显示为未跟踪是预期状态。
- `.env.example` 已存在的删除必须保留，不恢复、不暂存。
- `server/.idea/` 是既有未跟踪目录，不删除、不纳入任务。
- `specs/features/P3-ADMIN-001/` 是权威规格目录，即使未跟踪也不能清理。
- 只修改当前 Task 文件；不要格式化、回滚或暂存其他 Task 的累积改动。
- 用户要求每次只完成一个 Task；完成后停止。

## 3. 已稳定的架构契约

- `content` 与 `portfolio` 拥有 Entity、Repository、领域规则和管理命令。
- `admin` 只通过 `content :: admin`、`portfolio :: admin` named interface 编排 HTTP 与审计。
- `/api/v1/admin/**` 必须动态具备 `ROLE_ADMIN`；所有写请求必须通过 CSRF。
- 所有可变资源使用 JPA `@Version`；旧 version 返回 `409 stale_version`，不得自动重试覆盖。
- 成功业务写入和审计位于同一事务；失败、GET 和预览不写成功审计。
- 审计只保存 actor UUID、资源类型、资源 UUID、动作、version 和时间，不保存正文或请求安全对象。
- P3 不改变 P2 匿名 GET、公开响应字段、可见性、未来发布时间和 sitemap 行为。
- 错误使用安全的 `application/problem+json`；不得返回 SQL、堆栈、Markdown 或安全上下文。

## 4. Task 1-8 完成能力

| Task | 已完成能力 |
|---|---|
| 1 | Modulith 边界、admin 模块和 P2/P3 范围门禁 |
| 2-3 | V5 append-only 审计表、审计模型、actor 解析和事务写入 |
| 4-5 | 文章管理命令、状态机、固定 API、version、审计和安全错误 |
| 6 | 分类/标签 CRUD、引用保护、V6 name 唯一约束和审计 |
| 7 | 项目管理命令、HTTPS URL、状态机、分页和并发 |
| 8 | 项目固定 API、Bean Validation、version DTO 复用和 PROJECT 审计 |

Task 8 的正确固定路径是：

```text
GET|POST /api/v1/admin/portfolio/projects
GET|PUT  /api/v1/admin/portfolio/projects/{id}
DELETE   /api/v1/admin/portfolio/projects/{id}?version=<version>
POST     /api/v1/admin/portfolio/projects/{id}/publish
POST     /api/v1/admin/portfolio/projects/{id}/archive
```

不要误用 `/api/v1/admin/projects`。创建响应的 `Location` 必须使用完整固定路径。

Task 5 的 `AdminVersionRequest` 和 `AdminPublishRequest` 已被项目 API 复用。文章与项目目前各有局部异常 advice；统一 ProblemDetail/OpenAPI 属于 Task 11，不要在 Task 9 提前重构。

## 5. 下一任务：Task 9

Task 9 名称：个人简介、技能与经历管理后端。

只允许修改或创建实施计划列出的文件：

```text
portfolio/domain/PortfolioProfile.java
portfolio/domain/Skill.java
portfolio/domain/Experience.java
portfolio/domain/PortfolioProfileRepository.java
portfolio/domain/SkillRepository.java
portfolio/domain/ExperienceRepository.java
portfolio/application/admin/ProfileAdminService.java
portfolio/application/admin/SkillAdminService.java
portfolio/application/admin/ExperienceAdminService.java
portfolio/application/admin/PortfolioAdminViews.java
admin/application/AdminPortfolioUseCase.java
admin/web/portfolio/AdminProfileController.java
admin/web/portfolio/AdminSkillController.java
admin/web/portfolio/AdminExperienceController.java
portfolio/application/admin/PortfolioAdminServiceIntegrationTest.java
admin/web/portfolio/AdminPortfolioApiIntegrationTest.java
```

不得进入 Task 10 的 Markdown preview，也不得提前实现 Task 11 的全局异常/OpenAPI 聚合。

### Task 9 必须覆盖

- profile 不存在时 GET 404。
- profile PUT：`version=null` 只用于首次创建；已有 profile 使用非负 version 更新。
- 两个并发首次创建只能一个成功；单例主键冲突映射为 `stale_version`，不重试覆盖。
- skill：稳定列表、创建、更新、删除、visible、非负 sortOrder、业务重复值策略和并发。
- experience：稳定列表、创建、更新、删除、visible、日期范围、Markdown 长度、sortOrder 和并发。
- skill/experience 管理列表固定 `sortOrder ASC, id ASC`。
- 公开接口仍只返回 P2 定义的 visible 数据，不改变 P2 公开排序和字段。
- PROFILE、SKILL、EXPERIENCE 的每次成功写操作各写一条审计；404、校验和冲突不审计。

“skill 重复业务值策略”在计划中没有授权新增数据库唯一约束。先核对现有迁移和验收契约，不要自行把 name/category 设为唯一。

## 6. Task 9 当前代码基线

### PortfolioProfile

- 单例主键固定为 `PortfolioProfile.SINGLETON_ID = 1`。
- 字段：`displayName(120)`、`headline(180)`、`bioMarkdown(50000)`、可空 `seoDescription(160)`、时间和 version。
- 当前只有构造器与 getter，没有更新方法。
- Repository 当前只有 `save`、`findById`。

### Skill

- UUID 主键。
- 字段：`name(120)`、`category(120)`、可空 `summary(500)`、非负 sortOrder、visible、时间和 version。
- 当前只有构造器与 getter，没有 revise 方法。
- Repository 当前只有 `save`、`findById`、`findVisibleSkills`。
- P2 公开查询排序已是 `sort_order ASC, id ASC`。

### Experience

- UUID 主键。
- 字段：`organization(180)`、`role(180)`、必填 startDate、可空 endDate、`summaryMarkdown(20000)`、非负 sortOrder、visible、时间和 version。
- 当前构造器拒绝 `endDate < startDate`，没有 revise 方法。
- Repository 当前只有 `save`、`findById`、`findVisibleExperiences`。
- P2 公开查询排序是 `sort_order ASC, start_date DESC, id ASC`；Task 9 管理列表必须单独使用 `sort_order ASC, id ASC`，不要改公开 SQL。

`PortfolioFieldRules` 当前位于 `Project.java` 同 package 范围，已集中提供文本长度、可空值、非负 sortOrder 和日期范围规则。Task 9 应复用或按计划最小整理这些规则，不在 Controller、Service 和 Entity 重复写领域判断。

## 7. 推荐的最短正确执行路径

1. 只读核对 Task 9、V4 portfolio migration、三个 Entity/Repository 和 P2 public query。
2. 先新增两个计划内集成测试文件，一次运行确认管理服务/API 缺失的 RED。
3. 在 Entity 中新增原子更新方法：先校验所有输入到局部变量，再统一赋值，避免部分修改。
4. Repository 只补管理列表、删除、flush 和并发所需的最小能力；公共查询 SQL 不改。
5. 实现 portfolio 管理 Service/View，再实现 admin use case/controller 和 Bean Validation。
6. 运行一次 Task 9 定向 GREEN；只按真实失败修复，不重复无变化命令。
7. 先做规格审查，再做代码质量审查；审查代理禁止重复运行 Maven。
8. 最后只运行一次最小聚合，覆盖 Task 9 服务/API、P2 公开 portfolio、Modulith 和 P3 范围。
9. 更新本文件和 `docs/AI-CONTEXT-HANDOFF.md`，运行 Git 检查后停止。

建议最终聚合基线：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=PortfolioAdminServiceIntegrationTest,AdminPortfolioApiIntegrationTest,PublicPortfolioQueryIntegrationTest,PublicPortfolioApiIntegrationTest,StudyStackModulesTest,P3ScopeContractTest test'
```

如果 Task 9 修改导致现有 Repository 夹具或行为变化，再把 `PortfolioRepositoryIntegrationTest` 加入同一次聚合；不要先单独重复运行。

## 8. 最近可信验证

Task 8 最终命令：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=AdminProjectApiIntegrationTest,ProjectAdminServiceIntegrationTest,AdminArticleApiIntegrationTest,PublicPortfolioApiIntegrationTest,AuthorizationIntegrationTest,StudyStackModulesTest,P3ScopeContractTest test'
```

结果：35 tests，0 failures，0 errors，0 skipped，`BUILD SUCCESS`。

该结果已覆盖 Task 8 项目 API/服务、Task 5 文章 API、P2 公开作品、P1 授权和 Modulith/P3 边界。新会话开始 Task 9 时不需要重跑它；只在 Task 9 最终聚合中选择受影响的回归。

## 9. 环境与命令约束

- PowerShell 必须使用 `pwsh`，不能使用 `powershell.exe`。
- Maven Wrapper 传 `-D...` 时使用 `--%`。
- 多模块 Maven 使用 `-pl <module> -am`。
- Docker Desktop、Java 21.0.10、PostgreSQL 17.7 Testcontainers 已验证可用。
- `git diff --check` 可能仅输出 LF/CRLF warning；exit code 0 表示没有空白错误。
- 完成 Task 前检查 `git status --short --branch`、`git diff --check`、`git diff --cached --check`。
- 不 commit、不 push、不创建 PR，除非用户另行明确授权。
