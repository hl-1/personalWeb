# Java Source Structure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every Java top-level type its own same-package source file and prevent recurrence.

**Architecture:** Preserve every live type's package and visibility so fully qualified names and runtime wiring remain unchanged. Remove unregistered controller-local exception handlers because the registered global advice already owns those contracts. Enforce the convention by parsing Java compilation units with the JDK compiler AST.

**Tech Stack:** Java 21, Spring Boot, Spring Modulith, Jakarta Persistence, JUnit 5, Maven Wrapper.

---

### Task 1: Add the source structure gate

**Files:**
- Create: `server/src/test/java/com/studystack/foundation/JavaSourceStructureTest.java`

- [ ] Add a JUnit test that parses all main and test Java sources with `JavacTask.parse()` and reports files containing more than one top-level `ClassTree`.
- [ ] Run `pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd --% -Dtest=JavaSourceStructureTest test'`.
- [ ] Confirm RED reports the eight existing multi-type source files.

### Task 2: Extract live package-private types

**Files:**
- Create: `server/src/main/java/com/studystack/admin/domain/JpaAdminAuditRepository.java`
- Create: `server/src/main/java/com/studystack/content/domain/ContentFieldRules.java`
- Create: `server/src/main/java/com/studystack/content/domain/ContentSlugConverter.java`
- Create: `server/src/main/java/com/studystack/portfolio/domain/PortfolioFieldRules.java`
- Create: `server/src/main/java/com/studystack/portfolio/domain/PortfolioSlugConverter.java`
- Create: `server/src/main/java/com/studystack/portfolio/domain/PortfolioUrlPolicy.java`
- Modify: `server/src/main/java/com/studystack/admin/domain/AdminAuditRepository.java`
- Modify: `server/src/main/java/com/studystack/content/domain/Article.java`
- Modify: `server/src/main/java/com/studystack/portfolio/domain/Project.java`

- [ ] Move each live package-private type verbatim into its same-named file and same package.
- [ ] Remove imports from the former host file only when they are no longer used there.
- [ ] Keep annotations, constructors, method signatures, and visibility unchanged.

### Task 3: Remove superseded local exception handlers

**Files:**
- Modify: `server/src/main/java/com/studystack/admin/web/article/AdminArticleController.java`
- Modify: `server/src/main/java/com/studystack/admin/web/portfolio/AdminProfileController.java`
- Modify: `server/src/main/java/com/studystack/admin/web/portfolio/AdminProjectController.java`
- Modify: `server/src/main/java/com/studystack/admin/web/preview/AdminMarkdownPreviewController.java`
- Modify: `server/src/main/java/com/studystack/admin/web/taxonomy/AdminCategoryController.java`

- [ ] Delete the five unregistered `Admin*ExceptionHandler` top-level classes.
- [ ] Remove imports used only by those handlers.
- [ ] Keep `AdminApiExceptionHandler` as the sole registered admin error mapper.

### Task 4: Synchronize source contracts and verify

**Files:**
- Modify: `server/src/test/java/com/studystack/foundation/P2ScopeContractTest.java`

- [ ] Add the five new content and portfolio helper files to the exact P2 responsibility map.
- [ ] Re-run `JavaSourceStructureTest` and confirm GREEN.
- [ ] Run the foundation, audit, content, portfolio, and admin API integration tests.
- [ ] Run `pwsh -NoLogo -NoProfile -Command 'Set-Location server; .\mvnw.cmd verify'`.
- [ ] Run `git diff --check`, inspect task-scoped status, and confirm no generated or unrelated files were added.
