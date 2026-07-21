import { expect, test, type Page, type Route } from '@playwright/test'

type Identity = 'anonymous' | 'user' | 'admin'
const csrf = 'LOCAL_E2E_CSRF_NOT_A_SECRET'

async function json(route: Route, body: unknown, status = 200, headers?: Record<string, string>) {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body), headers })
}

async function mockIdentity(page: Page, identity: Identity) {
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/auth/me') {
      return json(route, identity === 'anonymous'
        ? { authenticated: false, user: null }
        : { authenticated: true, user: { id: '00000000-0000-4000-8000-000000000001', login: 'local-e2e', displayName: 'Local E2E', avatarUrl: null, roles: identity === 'admin' ? ['USER', 'ADMIN'] : ['USER'] } })
    }
    if (path === '/api/v1/auth/csrf') return json(route, { token: csrf, headerName: 'X-CSRF-TOKEN' })
    if (path === '/api/v1/admin/categories' && route.request().method() === 'GET') return json(route, [])
    if (path === '/api/v1/admin/categories' && route.request().method() === 'POST') {
      expect(route.request().headers()['x-csrf-token']).toBe(csrf)
      expect(route.request().postData()).not.toContain(csrf)
      return json(route, { id: '00000000-0000-4000-8000-000000000010', name: 'Java', slug: 'java', articleCount: 0, createdAt: '2026-07-20T00:00:00Z', updatedAt: '2026-07-20T00:00:00Z', version: 0 }, 201, { Location: '/api/v1/admin/categories/00000000-0000-4000-8000-000000000010' })
    }
    return route.fulfill({ status: 404 })
  })
}

for (const scenario of [
  { identity: 'anonymous' as const, url: /\/login$/, marker: 'login-view' },
  { identity: 'user' as const, url: /\/forbidden$/, marker: 'forbidden-view' },
  { identity: 'admin' as const, url: /\/admin\/portfolio\/skills$/, text: 'Skills' },
]) {
  test(`${scenario.identity} receives the expected admin route`, async ({ page }) => {
    await mockIdentity(page, scenario.identity)
    await page.goto('/admin/portfolio/skills')
    await expect(page).toHaveURL(scenario.url)
    if ('marker' in scenario) await expect(page.getByTestId(scenario.marker)).toBeVisible()
    else await expect(page.getByRole('heading', { name: scenario.text })).toBeVisible()
  })
}

test('admin writes use the local CSRF fixture without a GitHub call', async ({ page }) => {
  await mockIdentity(page, 'admin')
  await page.goto('/admin/categories')
  await page.getByLabel('Name').fill('Java')
  await page.getByLabel('Slug').fill('java')
  await page.getByRole('button', { name: 'Add category' }).click()
  await expect(page.getByLabel('Name')).toHaveValue('')
})
