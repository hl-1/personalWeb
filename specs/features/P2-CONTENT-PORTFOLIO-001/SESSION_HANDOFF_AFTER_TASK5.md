# P2 Content And Portfolio Session Handoff After Task 5

更新日期：2026-07-17

## 1. 交接目的

本文档用于在新 Codex 会话中继续执行 P2 实施计划。当前检查点是：

- Task 1 至 Task 5 已完成 RED、GREEN、REFACTOR 和验证。
- 下一项只能在用户明确授权后执行 Task 6。
- 当前改动全部留在本地工作区，未暂存、未提交、未推送。
- 不要回滚、清理或覆盖现有未提交改动。

## 2. 新会话必读顺序

1. `C:\Users\6666\.codex\AGENTS.md`
2. `C:\softWare\project\studystack\specs\features\P2-CONTENT-PORTFOLIO-001\IMPLEMENTATION_PLAN.md`
3. 本文档

实施计划要求每次只执行一个 Task。完成当前 Task 的 RED、GREEN、REFACTOR 和验证后必须停止，不得自动进入下一 Task。

## 3. 仓库快照

- 仓库：`C:\softWare\project\studystack`
- 当前分支：`codex/p2-content-portfolio`
- 当前基准提交：`f41ebb24e54bbe3a96c3d3aa89b0d30afe4ab7a4`
- 基准提交标题：`docs(identity): 完善 P1 身份与权限规格`
- 暂存区：空
- Task 1 至 Task 5 的改动均未提交。

当前工作区包含下列 P2 改动，不要把它们当作意外脏文件：

- Task 1：P2 模块和范围门禁。
- Task 2：共享 `Slug` 和 `SlugPolicy`。
- Task 3：安全 Markdown 渲染器及 Maven 依赖。
- Task 4：Flyway V3 内容 schema 及迁移测试兼容调整。
- Task 5：内容领域模型、Repository、模块接口补全和集成测试。
- P2 实施计划本身也是未跟踪文件。

禁止执行 `git reset --hard`、`git checkout --` 或清理未跟踪文件。未经用户单独授权，不要暂存、提交、推送或创建 PR。

## 4. 已完成任务摘要

### Task 1

- `content`、`portfolio` 已进入实现阶段。
- `admin`、`comment`、`media` 仍保持空模块。
- P2 范围测试拒绝写 Controller、上传、评论和后台 CRUD 越界实现。

### Task 2

- Slug 规则集中在 `shared.slug`。
- 仅执行 trim 和小写归一化，不自动转写空格、中文或特殊字符。
- 格式固定为 3 至 120 个小写 ASCII 字母或数字，以单个短横线分隔。
- 已发布资源的 slug 变更返回固定冲突码。

### Task 3

- CommonMark、GFM table/strikethrough 和 OWASP sanitizer 已固定版本。
- 原始 HTML、危险 URL、图片和脚本载荷被拒绝或清洗。
- `MarkdownRenderer` 返回不可变的安全 HTML 值。

### Task 4

- `V3__content_schema.sql` 已创建文章、分类、标签和文章标签表。
- UUID、唯一 slug、状态约束、外键、长度约束、乐观锁和公开查询索引已覆盖。
- P0/P1 迁移测试已调整为允许后续 migration，同时继续验证各自负责的 schema。

### Task 5

- 已实现 `ArticleStatus`、`Article`、`Category`、`Tag` 和三个 Repository。
- Article 支持 draft、publish、archive，首次发布后 slug 永久锁定。
- Category/Tag 关系维护双向一致性，重复 tag 被拒绝，集合不提供 public setter。
- 公开文章查询固定为 `publishedAt DESC, id DESC`，排除草稿、归档和未来发布记录。
- Category/Tag Repository 已提供公开文章计数投影，直接聚合并稳定排序，供 Task 6 避免 N+1。
- 所有领域命令先完成参数校验，再一次性修改状态；异常后字段、状态和双方关系保持不变。
- 字段校验集中在包级 `ContentFieldRules`，Category/Tag 不复制名称规则。

## 5. Task 5 关键技术决策

### 5.1 预分配 UUID 与版本字段

三个实体的 `@Version` 字段必须保持为可空 `Long`，并且 `version()` 必须直接返回该 `Long`。

不要把新实体的 null 转成基本类型 `0`。Spring Data 3.5.13 的 BeanWrapper 会识别 record 风格的 `version()` 方法；若该方法返回 `0`，预分配 UUID 的新实体会被误判为旧实体，`save` 将错误地走 `merge`。

数据库落库后的初始版本仍是 `0L`，V3 的 `BIGINT NOT NULL DEFAULT 0` 不变。

### 5.2 模块接口

- `Slug` 和 `SlugPolicy` 标注为 `@NamedInterface("slug")`。
- content 模块只允许依赖 `shared :: slug`。
- 不要为了绕过 Modulith 验证把整个 shared 模块改为 OPEN。

Task 6 将首次从 content 引用 `shared.markdown`。预计需要采用同样的精确 Named Interface 方式暴露 Markdown API，并把 content allowlist 增加 `shared :: markdown`。修改前应先用 Modulith 的真实失败确认，不要直接开放整个 shared 模块。

### 5.3 Repository 边界

- 不接受调用方传入 `Sort` 或 `Pageable`。
- 不返回 `Page<Entity>` 给 web 层。
- 公开查询在 Repository 内固定可见性和排序。
- taxonomy projection 包含 `name`、`slug` 和 `publishedArticleCount`。
- 分页参数校验属于 Task 6 application 层，不要把任意 web 契约塞进 Entity。

### 5.4 事务和懒加载

Entity 关系保持 LAZY。测试或 application 映射必须在只读事务内访问 lazy collection，不要通过 EAGER 加载掩盖事务边界问题。

## 6. 最新验证证据

最终联合命令：

```powershell
pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=ContentRepositoryIntegrationTest,ContentSchemaIntegrationTest,SlugPolicyTest,StudyStackModulesTest,P2ScopeContractTest test'
```

结果：`53` tests，`0` failures，`0` errors，`0` skipped。

明细：

- `ContentRepositoryIntegrationTest`：9
- `ContentSchemaIntegrationTest`：11
- `SlugPolicyTest`：23
- `StudyStackModulesTest`：4
- `P2ScopeContractTest`：6

其他收尾检查：

- `git diff --check` 通过。
- `git diff --cached --check` 通过。
- 暂存区为空。
- Task 5 相关文件无尾随空白。
- Mockito 动态 agent 警告是既有工具链警告，不是测试失败。

## 7. 只读参考项目状态

参考项目 `C:\softWare\project\latest\skillhub` 只读，Task 5 前后状态一致：

```text
## codex/fix-rerelease-skill-identity
 M server/skillhub-app/src/main/java/com/skillhub/domain/skill/service/SkillPublishService.java
 M server/skillhub-app/src/test/java/com/skillhub/domain/skill/service/SkillPublishServiceTest.java
?? specs/features/P5-BE-RERELEASE-IDENTITY-001/
```

这些是用户已有改动。不得修改、清理、暂存或提交参考项目内容。

## 8. 下一步：Task 6

Task 6 名称：文章公开查询服务。

开始前必须重新读取实施计划中的完整 Task 6，不要只依赖本文摘要。Task 6 计划文件范围是：

- Create: `server/src/main/java/com/studystack/content/application/ArticleSummary.java`
- Create: `server/src/main/java/com/studystack/content/application/ArticleDetail.java`
- Create: `server/src/main/java/com/studystack/content/application/TaxonomySummary.java`
- Create: `server/src/main/java/com/studystack/content/application/PublicArticleQuery.java`
- Create: `server/src/main/java/com/studystack/content/application/ArticleNotFoundException.java`
- Create: `server/src/test/java/com/studystack/content/application/PublicArticleQueryIntegrationTest.java`

Task 6 必须覆盖：

- 默认分页 0/10、最大 50、负数和超限拒绝。
- 分类和标签组合筛选，以及相同发布时间下 UUID 稳定排序。
- 详情只允许当前已发布文章；草稿、归档、未来、非法和未知 slug 统一 not found。
- Markdown 只通过唯一 `MarkdownRenderer` 转为安全 HTML，不泄露 `bodyMarkdown`、status 或 version。
- taxonomy 只返回至少一个当前公开文章的分类/标签及正确计数。
- application 映射发生在只读事务内，避免 lazy collection 问题。
- 不创建 Controller、写接口、P3 管理功能或演示数据。

可直接复用：

- `ArticleRepository.findPublicArticles(...)`
- `ArticleRepository.countPublicArticles(...)`
- `ArticleRepository.findPublicBySlug(...)`
- `CategoryRepository.findPublicCategoryCounts(...)`
- `TagRepository.findPublicTagCounts(...)`
- `SlugPolicy`
- `MarkdownRenderer`

## 9. 新会话启动检查

1. 使用 PowerShell 7，所有 PowerShell 命令通过 `pwsh` 执行。
2. Maven Wrapper 传递 `-D...` 时使用 `--%`。
3. 读取三个必读文件。
4. 运行一次 `git status --short --branch`，确认现有 P2 改动仍在且暂存区为空。
5. 记录参考项目状态，不修改参考项目。
6. 只执行 Task 6 的 RED、GREEN、REFACTOR 和验证。
7. 遇到意外测试失败时先定位真实根因，不重复执行相同失败命令。
8. 完成后运行 Task 5/6 和 Modulith 相关验证，再做 `git diff --check`。
9. 停止，不进入 Task 7，不提交、不推送。

## 10. 可直接发送的新会话提示词

```text
请先读取并遵守：
1. C:\Users\6666\.codex\AGENTS.md
2. C:\softWare\project\studystack\specs\features\P2-CONTENT-PORTFOLIO-001\IMPLEMENTATION_PLAN.md
3. C:\softWare\project\studystack\specs\features\P2-CONTENT-PORTFOLIO-001\SESSION_HANDOFF_AFTER_TASK5.md

当前 Task 1 至 Task 5 的改动均未提交，不要回滚或清理。
本次只执行 Task 6，严格完成 RED、GREEN、REFACTOR 和验证后停止。
不要暂存、提交、推送或进入 Task 7。
```
