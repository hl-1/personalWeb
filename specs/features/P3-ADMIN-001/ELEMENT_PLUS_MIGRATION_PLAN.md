---
task_id: P3-ADMIN-001
title: StudyStack 管理后台 Element Plus 迁移计划
status: implementation-complete-awaiting-verification
created: 2026-07-22
updated: 2026-07-22
product_ref: specs/features/P3-ADMIN-001/PRODUCT.md
tech_ref: specs/features/P3-ADMIN-001/TECH.md
---

# Element Plus Admin Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the complete StudyStack administration UI to Element Plus while preserving public pages and all existing API, validation, draft, security, and routing contracts.

**Architecture:** Vite resolves Element Plus components and composables on demand. Existing Zod schemas, `createAdminFormValidation`, TanStack Query clients, Pinia draft stores, and view handlers remain the behavioral source of truth; Element Plus replaces only admin presentation and interaction primitives.

**Tech Stack:** Vue 3, TypeScript, Vite, Element Plus, `@element-plus/icons-vue`, unplugin auto import/components, Vitest, Playwright.

---

### Task 1: Element Plus Foundation

**Files:**
- Modify: `web/package.json`
- Modify: `web/pnpm-lock.yaml`
- Modify: `web/vite.config.ts`
- Create: `web/src/features/admin/admin-element-theme.css`
- Create: `web/element-plus-plugins.ts`
- Create: `web/src/auto-imports.d.ts`
- Create: `web/src/components.d.ts`
- Test: `web/src/features/admin/admin-element-plus.spec.ts`

- [ ] Add a failing foundation test asserting the Element Plus components and services used by admin are available.
- [ ] Run `pnpm test -- src/features/admin/admin-element-plus.spec.ts` and verify the foundation assertion fails before installing Element Plus.
- [ ] Install `element-plus`, `@element-plus/icons-vue`, `unplugin-auto-import`, and `unplugin-vue-components` with pnpm.
- [ ] Configure `AutoImport({ resolvers: [ElementPlusResolver()] })` and `Components({ resolvers: [ElementPlusResolver()] })` in `vite.config.ts`.
- [ ] Define admin-scoped Element Plus color, radius, typography, table, and focus variables in `admin-element-theme.css` and import it from `App.vue`.
- [ ] Re-run the foundation test and `pnpm typecheck`.

### Task 2: Feedback And Confirmation Services

**Files:**
- Create: `web/src/features/admin/admin-operation-feedback.ts`
- Create: `web/src/features/admin/admin-operation-feedback.spec.ts`
- Create: `web/src/features/admin/admin-confirmation.ts`
- Create: `web/src/features/admin/admin-confirmation.spec.ts`
- Create: `web/src/shared/feedback/operation-feedback.ts`
- Create: `web/src/shared/feedback/operation-feedback.spec.ts`
- Modify: `web/src/App.vue`
- Modify: `web/src/features/auth/auth-view.spec.ts`
- Modify: `web/src/views/admin/AdminArticleEditView.vue`
- Modify: `web/src/features/admin/article/article-views.spec.ts`

- [ ] Add failing tests asserting admin operations publish explicit success/error messages and confirmation outcomes distinguish confirm, cancel, and close.
- [ ] Implement `admin-confirmation.ts` with `ElMessageBox.confirm`, explicit Chinese action labels, and a typed `confirmed | cancelled | closed` result.
- [ ] Replace the new-article native confirm with the typed confirmation service; confirm clears the form, cancel routes to `/admin/articles`, and close stays on the current form.
- [ ] Route admin operation and public logout results through the shared Pinia feedback store, which records the message and invokes `ElMessage`.
- [ ] Run operation feedback, confirmation, and article view tests.

### Task 3: Shared Admin Components

**Files:**
- Create: `web/src/features/admin/components/AdminFormField.vue`
- Create: `web/src/features/admin/components/AdminFormField.spec.ts`
- Modify: `web/src/features/admin/components/AdminPageState.vue`
- Modify: `web/src/features/admin/admin-layout.spec.ts`
- Modify: `web/src/features/admin/components/MarkdownEditor.vue`
- Modify: `web/src/features/admin/article/article-views.spec.ts`

- [ ] Add failing tests for `ElFormItem` validation state, Element Plus retry actions, and Element Plus editor tabs/input.
- [ ] Refactor `AdminFormField` to render `ElFormItem` while retaining hints, first-error rendering, `aria-describedby`, and `aria-invalid` slot data.
- [ ] Refactor `AdminPageState` actions to `ElButton` and `MarkdownEditor` to `ElTabs` plus textarea `ElInput` without changing safe preview behavior.
- [ ] Run the focused component and article tests.

### Task 4: Content Administration Views

**Files:**
- Modify: `web/src/views/admin/AdminArticleListView.vue`
- Modify: `web/src/views/admin/AdminArticleEditView.vue`
- Modify: `web/src/views/admin/AdminTaxonomyView.vue`
- Modify: `web/src/features/admin/article/article-views.spec.ts`
- Modify: `web/src/features/admin/taxonomy/taxonomy-view.spec.ts`

- [ ] Add failing assertions for Element Plus tables, pagination, form controls, action buttons, and confirmation flows.
- [ ] Migrate article list filters/table/pagination, article edit controls, and taxonomy CRUD controls to Element Plus.
- [ ] Preserve every `name`, `data-testid`, field-error mapping, pending guard, and query invalidation contract used by tests.
- [ ] Run article and taxonomy view tests.

### Task 5: Portfolio Administration And Shell

**Files:**
- Modify: `web/src/layouts/AdminLayout.vue`
- Modify: `web/src/views/admin/AdminProjectListView.vue`
- Modify: `web/src/views/admin/AdminProjectEditView.vue`
- Modify: `web/src/views/admin/AdminProfileView.vue`
- Modify: `web/src/views/admin/AdminSkillsView.vue`
- Modify: `web/src/views/admin/AdminExperiencesView.vue`
- Modify: `web/src/features/admin/project/project-views.spec.ts`
- Modify: `web/src/features/admin/portfolio/portfolio-views.spec.ts`
- Modify: `web/src/features/admin/admin-layout.spec.ts`

- [ ] Add failing assertions for Element Plus navigation, portfolio forms, lists, pagination, buttons, and deletion confirmations.
- [ ] Migrate the admin menu and all portfolio controls while preserving responsive behavior, draft guards, visible/sort rules, and error paths.
- [ ] Run project, portfolio, layout, and routing tests.

### Task 6: Aggregate Verification And Runtime Refresh

**Files:**
- Modify only files required by failures caused by this migration.

- [ ] Run `pnpm lint`, `pnpm typecheck`, and `pnpm test`.
- [ ] Run `pnpm build` and verify on-demand Element Plus output compiles.
- [ ] Run `git diff --check`.
- [ ] Rebuild only the `caddy` service with `docker compose --env-file .env -f deploy/compose.yml up -d --build --no-deps caddy`.
- [ ] Verify `http://127.0.0.1:18080/` returns HTTP 200 and the caddy container is running.
