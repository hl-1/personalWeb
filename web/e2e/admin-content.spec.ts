import { expect, test, type Page, type Route } from '@playwright/test'

const now = '2026-07-20T00:00:00Z'
const articleId = '00000000-0000-4000-8000-000000000101'
const taxonomyId = '00000000-0000-4000-8000-000000000102'

async function json(route: Route, body: unknown, status = 200, headers?: Record<string, string>) {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body), headers })
}

function problem(code: string, status = 409) {
  return { type: 'about:blank', title: 'Conflict', status, detail: 'Conflict', instance: '/api/v1/admin', code }
}

async function mockContent(page: Page) {
  let article = { id: articleId, slug: 'safe-article', title: 'Safe article', summary: 'Summary', bodyMarkdown: 'Body', categoryId: null, tagIds: [], seoTitle: null, seoDescription: null, status: 'DRAFT', publishedAt: null, createdAt: now, updatedAt: now, version: 0 }
  let stale = false
  let previewMarkdown = ''
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url()); const path = url.pathname; const method = route.request().method()
    if (path === '/api/v1/auth/me') return json(route, { authenticated: true, user: { id: articleId, login: 'admin', displayName: 'Admin', avatarUrl: null, roles: ['ADMIN'] } })
    if (path === '/api/v1/auth/csrf') return json(route, { token: 'csrf', headerName: 'X-CSRF-TOKEN' })
    if (path === '/api/v1/admin/categories' && method === 'GET') return json(route, [{ id: taxonomyId, name: 'Java', slug: 'java', articleCount: 1, createdAt: now, updatedAt: now, version: 0 }])
    if (path === '/api/v1/admin/tags' && method === 'GET') return json(route, [])
    if (path === '/api/v1/admin/articles/preview') { previewMarkdown = JSON.parse(route.request().postData() ?? '{}').markdown; return json(route, { html: '<p>Safe preview</p>' }) }
    if (path === '/api/v1/admin/articles' && method === 'POST') { article = { ...article, ...JSON.parse(route.request().postData() ?? '{}') }; return json(route, article, 201, { Location: `/api/v1/admin/articles/${articleId}` }) }
    if (path === `/api/v1/admin/articles/${articleId}` && method === 'GET') return json(route, article)
    if (path === `/api/v1/admin/articles/${articleId}` && method === 'PUT') { if (stale) return json(route, problem('stale_version'), 409); article = { ...article, ...JSON.parse(route.request().postData() ?? '{}'), version: article.version + 1 }; return json(route, article) }
    if (path === `/api/v1/admin/articles/${articleId}/publish`) { article = { ...article, status: 'PUBLISHED', publishedAt: now, version: article.version + 1 }; return json(route, article) }
    if (path === `/api/v1/admin/categories/${taxonomyId}` && method === 'DELETE') return json(route, problem('taxonomy_in_use'), 409)
    if (path === '/api/v1/admin/articles' && method === 'GET') return json(route, { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
    return route.fulfill({ status: 404 })
  })
  return { markStale: () => { stale = true }, previewMarkdown: () => previewMarkdown }
}

test('article create, safe preview, publish and stale-version handling', async ({ page }) => {
  const state = await mockContent(page)
  await page.goto('/admin/articles/new')
  await page.getByLabel('Title', { exact: true }).fill('Safe article')
  await page.getByLabel('Slug').fill('safe-article')
  await page.getByLabel('Summary').fill('Summary')
  await page.locator('textarea[name="bodyMarkdown"]').fill('<img src=x onerror=alert(1)> **safe**')
  await page.getByTestId('preview-tab').click()
  await expect(page.getByText('Safe preview')).toBeVisible()
  await expect(page.locator('.preview-surface script, .preview-surface img')).toHaveCount(0)
  expect(state.previewMarkdown()).toContain('onerror')
  await page.getByRole('button', { name: 'Save', exact: true }).click()
  await expect(page).toHaveURL(new RegExp(`/admin/articles/${articleId}$`))
  await page.getByRole('button', { name: 'Publish', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Archive', exact: true })).toBeVisible()
  state.markStale()
  await page.getByTestId('edit-tab').click()
  await page.getByLabel('Title', { exact: true }).fill('Preserved title')
  await page.getByRole('button', { name: 'Save', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('server version changed')
  await expect(page.getByLabel('Title', { exact: true })).toHaveValue('Preserved title')
})

test('taxonomy deletion reports reference conflicts', async ({ page }) => {
  await mockContent(page); page.on('dialog', (dialog) => dialog.accept())
  await page.goto('/admin/categories')
  await page.getByTestId('delete-taxonomy').click()
  await expect(page.getByRole('alert')).toContainText('Remove article references first')
})
