# P2 Content And Portfolio Session Handoff After Task 15

更新日期：2026-07-17

## 1. 交接目的

本文档用于在新 Codex 会话中安全继续 StudyStack 开发。当前检查点是：

- P2 `P2-CONTENT-PORTFOLIO-001` Task 1 至 Task 15 已完成 RED、GREEN、REFACTOR 和聚合验证。
- 当前阶段状态为 `P2 / awaiting-user-acceptance`，等待用户验收。
- P2 全部改动仍保留在本地工作区，未暂存、未提交、未推送。
- 不得回滚、清理、覆盖或格式化现有未提交改动。
- `specs/features/P3-ADMIN-001/IMPLEMENTATION_PLAN.md` 是用户已有未跟踪文件，不属于 P2 产物，不得修改或清理。
- 未经用户明确授权，不得开始 P3、提交、推送、创建 PR 或执行部署。

## 2. 新会话必读顺序

1. `C:\Users\6666\.codex\AGENTS.md`
2. `C:\softWare\project\studystack\docs\AI-CONTEXT-HANDOFF.md`
3. `C:\softWare\project\studystack\specs\PROGRESS.md`
4. `C:\softWare\project\studystack\specs\features\P2-CONTENT-PORTFOLIO-001\IMPLEMENTATION_PLAN.md`
5. 本文档
6. 只有用户明确授权 P3 后，才读取 `C:\softWare\project\studystack\specs\features\P3-ADMIN-001\IMPLEMENTATION_PLAN.md`

`SESSION_HANDOFF_AFTER_TASK5.md` 只保留历史检查点用途。新会话应以本文件和 `specs/PROGRESS.md` 为最新事实来源。

## 3. 仓库快照

- 仓库：`C:\softWare\project\studystack`
- 当前分支：`codex/p2-content-portfolio`
- 当前基准提交：`f41ebb24e54bbe3a96c3d3aa89b0d30afe4ab7a4`
- 基准提交标题：`docs(identity): 完善 P1 身份与权限规格`
- 暂存区：空
- Task 1 至 Task 15 的改动均未提交。

当前工作区包含完整 P2 后端、前端、迁移、配置、E2E、进度和交接改动。它们不是意外脏文件。禁止执行：

- `git reset --hard`
- `git checkout -- <path>`
- `git clean`
- 删除未跟踪的 P2/P3 规格或源码
- 对整个仓库执行无关格式化

## 4. P2 已完成能力

### 4.1 共享能力

- `shared.slug` 集中维护 3 至 120 位小写 ASCII slug、归一化和发布后不可变规则。
- 前端 `web/src/shared/api/slug-schema.ts` 是唯一运行时 slug 正则来源；V3/V4 中的正则是必要的数据库防线。
- `shared.markdown` 使用 CommonMark 0.28.0、GFM table/strikethrough 和 OWASP Java HTML Sanitizer 20260313.1。
- 原始 HTML、脚本、事件属性、图片、iframe、`javascript:` 和 `data:` URL 不得形成可执行公开 HTML。
- `shared.seo` 提供公开 origin、sitemap、robots 和模块贡献接口。
- `shared.web` 提供稳定 `application/problem+json` 映射。

### 4.2 Content

- Flyway V3 创建文章、分类、标签和 article-tag 关系。
- 文章支持 `DRAFT`、`PUBLISHED`、`ARCHIVED`，首次发布后 slug 锁定。
- 匿名查询只返回 `PUBLISHED` 且 `publishedAt <= now` 的文章。
- 列表固定 `publishedAt DESC, id DESC`，支持 0 基分页和分类/标签组合筛选。
- 分类和标签只统计当前公开文章，避免 N+1。
- 公开文章 API、详情、404、OpenAPI、sitemap 贡献均已实现。

### 4.3 Portfolio

- Flyway V4 创建简介、项目、技能和经历表。
- 项目使用与文章一致的发布可见性和 slug 稳定规则，支持 featured、分页和详情。
- 技能和经历具有稳定排序、日期约束和安全 Markdown 摘要。
- 简介不存在时公开 API 返回 404；项目隐藏或不存在统一返回 404。
- 公开 portfolio API、OpenAPI 和 sitemap 贡献均已实现。

### 4.4 Frontend

- content/portfolio API 对象必须通过 strict Zod schema 和显式 mapping。
- API client 只使用同源 `/api`，不接受任意绝对域名或客户端排序字段。
- TanStack Query key 包含分页、筛选和详情 slug，不在 Pinia 复制服务端内容。
- 公开路由为 `/`、`/about`、`/blog`、`/blog/:slug`、`/projects`、`/projects/:slug`。
- 页面覆盖加载、空、错误和 404；数据库为空时仍保留站点身份和稳定状态。
- `v-html` 只允许出现在 `SafeMarkdownView`，且输入先通过危险片段检查。
- `usePageSeo` 管理 title、description、canonical 和 Open Graph，并在卸载时清理。

### 4.5 Environment And Proxy

- `STUDYSTACK_PUBLIC_BASE_URL` 只注入后端 app，prod 必须是无 path/query/fragment 的 HTTPS origin。
- Caddy 拒绝 `/actuator`，代理 `/api`、OAuth、`/sitemap.xml` 和 `/robots.txt`，然后才执行 SPA fallback。
- OpenAPI TypeScript 必须使用 `pnpm contract:generate` 生成，不得手工编辑。
- Compose 仍只有 PostgreSQL、app 和 Caddy，不包含 Redis、Elasticsearch、MinIO 或其他服务。

## 5. Task 15 聚合修复

首次 `mvnw verify` RED 真实暴露两个旧测试上下文缺口：

1. `ProductionEnvironmentPolicyTest` 只验证 EnvironmentPostProcessor，却实例化了无关的 P2 查询 bean。测试上下文改为 lazy initialization，仍由真实生产密码 guard 执行验证。
2. `SessionPolicyIntegrationTest` 主动启动完整 prod profile，但未提供新增公开基址。该测试现显式传入 `STUDYSTACK_PUBLIC_BASE_URL=https://example.com`。

两个缺口先分别通过定向测试，再通过完整 Maven 验证。不要通过放宽生产 HTTPS、禁用 P2 bean 或伪造业务 Repository 来回避这些测试。

Task 15 REFACTOR 还将 content schema、portfolio schema 和博客 query 中重复的 slug 规则收敛到 `web/src/shared/api/slug-schema.ts`，并新增 3 项回归测试。

## 6. 最新验证证据

最终结果：

| 范围 | 结果 |
|---|---|
| 后端 `mvnw verify` | 234 tests，0 failures，0 errors，0 skipped，JAR 成功 |
| 前端 lint | 状态 0，无 warning |
| 前端 typecheck | 状态 0 |
| 前端 Vitest | 20 files，149 tests 全部通过 |
| OpenAPI contract | 状态 0，generated TypeScript 与服务端一致 |
| 前端 build | 状态 0，215 modules transformed |
| Compose 配置测试 | 6 tests 全部通过 |
| Compose topology | PostgreSQL、app、Caddy healthy |
| Playwright | 12 tests 全部通过，含移动端空数据与横向溢出检查 |
| 空数据库浏览器验收 | 首页、About、博客、项目状态稳定，无横向溢出 |
| Git 检查 | `git diff --check` 和 `git diff --cached --check` 状态 0 |

Compose 验收使用本机端口 18080，因为 8080 被外部 Java 进程占用。验收结束后容器、网络和 `studystack_postgres-data` 已删除。

## 7. 安全与范围扫描结论

- 生产 Java 源码没有 Swagger 2 / Springfox 注解。
- content 和 portfolio 没有 POST、PUT、PATCH、DELETE Controller。
- `v-html` 只命中 `SafeMarkdownView.vue`。
- 没有真实私钥、GitHub token 或 personal access token 命中。
- content、portfolio、shared 不依赖 admin、comment 或 media 实现。
- 前端运行时 slug 正则只有共享 schema 一处；V3/V4 CHECK 保留。
- P3 管理写功能、P4 评论、P5 媒体和后续部署能力均未实现。

## 8. 只读参考项目

参考项目 `C:\softWare\project\latest\skillhub` 只读，当前状态为：

```text
## codex/fix-rerelease-skill-identity
 M server/skillhub-app/src/main/java/com/skillhub/domain/skill/service/SkillPublishService.java
 M server/skillhub-app/src/test/java/com/skillhub/domain/skill/service/SkillPublishServiceTest.java
?? specs/features/P5-BE-RERELEASE-IDENTITY-001/
```

这些是用户已有改动。不得修改、清理、暂存、提交、构建或生成该参考项目内容。

## 9. 后续开发门禁

新会话必须先判断用户授权类型：

- **仅查看/验收 P2：** 只读取证据、运行必要验证或解释实现，不执行 Git 写操作。
- **提交/上传 P2：** 必须由用户明确要求；按全局 `AGENTS.md` 一次成功流程，只提交 P2 相关文件，排除用户 P3 规格和其他无关改动。
- **开始 P3：** 必须由用户明确授权具体 P3 Task；先完整读取 P3 实施计划，再只执行该 Task 的 RED、GREEN、REFACTOR 和验证。
- **其他功能：** 先读取对应获批规格；没有规格或授权时不得假设范围。

`awaiting-user-acceptance` 不等于 P2 已验收，也不自动授权 P3。

## 10. 可直接发送的新会话提示词

### 10.1 继续验收或处理 P2

```text
请先读取并遵守：
1. C:\Users\6666\.codex\AGENTS.md
2. C:\softWare\project\studystack\docs\AI-CONTEXT-HANDOFF.md
3. C:\softWare\project\studystack\specs\PROGRESS.md
4. C:\softWare\project\studystack\specs\features\P2-CONTENT-PORTFOLIO-001\SESSION_HANDOFF_AFTER_TASK15.md

P2 Task 1 至 Task 15 的改动均未提交，不要回滚或清理。
本次只执行我随后明确指定的 P2 验收、修复或 Git 操作，不要开始 P3。
```

### 10.2 用户明确授权某个 P3 Task 后

```text
请先读取并遵守：
1. C:\Users\6666\.codex\AGENTS.md
2. C:\softWare\project\studystack\docs\AI-CONTEXT-HANDOFF.md
3. C:\softWare\project\studystack\specs\PROGRESS.md
4. C:\softWare\project\studystack\specs\features\P2-CONTENT-PORTFOLIO-001\SESSION_HANDOFF_AFTER_TASK15.md
5. C:\softWare\project\studystack\specs\features\P3-ADMIN-001\IMPLEMENTATION_PLAN.md

P2 Task 1 至 Task 15 的改动均未提交，不要回滚、清理、暂存或提交它们。
本次只执行 P3 Task N，严格完成 RED、GREEN、REFACTOR 和验证后停止。
不要进入下一 Task，不要提交或推送，除非我另行明确授权。
```
