import { expect, test, type Page, type Route } from '@playwright/test'

type Identity = 'anonymous' | 'user' | 'admin' | 'invalid'

const user = {
  id: '2d65e30a-f450-4f8e-8ed9-5f36b2f7c322',
  login: 'octocat',
  displayName: 'The Octocat',
  avatarUrl: null,
  roles: ['USER'],
}

function authResponse(identity: Identity): unknown {
  if (identity === 'anonymous') {
    return { authenticated: false, user: null }
  }
  if (identity === 'invalid') {
    return {
      authenticated: true,
      user: { ...user, roles: ['OWNER'], sensitiveMarker: 'DO_NOT_RENDER_THIS_VALUE' },
    }
  }
  return {
    authenticated: true,
    user: { ...user, roles: identity === 'admin' ? ['USER', 'ADMIN'] : ['USER'] },
  }
}

async function fulfillJson(route: Route, body: unknown, status = 200): Promise<void> {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  })
}

async function mockAuthentication(page: Page, identity: Identity): Promise<void> {
  await page.route('**/api/v1/auth/me', (route) => fulfillJson(route, authResponse(identity)))
  await page.route('**/api/v1/auth/csrf', (route) => fulfillJson(route, {
    token: 'E2E_CSRF_TOKEN_NOT_A_SECRET',
    headerName: 'X-CSRF-TOKEN',
  }))
  await page.route('**/api/v1/auth/logout', (route) => {
    const csrfHeader = route.request().headers()['x-csrf-token']
    return route.fulfill({ status: csrfHeader === 'E2E_CSRF_TOKEN_NOT_A_SECRET' ? 204 : 403 })
  })
}

async function diagnostic(
  page: Page,
  route: string,
  expectedIdentity: Identity,
  apiCategory: string,
): Promise<string> {
  const visibleMarkers = await page.locator('[data-testid]:visible').evaluateAll((elements) =>
    elements.slice(0, 12).map((element) => element.getAttribute('data-testid')),
  )
  return [
    `route=${route}`,
    `expected-identity=${expectedIdentity}`,
    `actual-url=${page.url()}`,
    `actual-markers=${visibleMarkers.join(',') || '<none>'}`,
    `api-category=${apiCategory}`,
  ].join(' ')
}

test('anonymous visitors are redirected from admin to login', async ({ page }) => {
  await mockAuthentication(page, 'anonymous')

  await page.goto('/admin')

  const details = await diagnostic(page, '/admin', 'anonymous', 'valid-anonymous')
  await expect(page, details).toHaveURL(/\/login$/)
  await expect(page.getByTestId('login-view'), details).toBeVisible()
})

test('authenticated users without ADMIN are sent to forbidden', async ({ page }) => {
  await mockAuthentication(page, 'user')

  await page.goto('/admin')

  const details = await diagnostic(page, '/admin', 'user', 'valid-user')
  await expect(page, details).toHaveURL(/\/forbidden$/)
  await expect(page.getByTestId('forbidden-view'), details).toBeVisible()
})

test('admins enter the dashboard and can log out with CSRF', async ({ page }) => {
  await mockAuthentication(page, 'admin')

  await page.goto('/admin')

  const details = await diagnostic(page, '/admin', 'admin', 'valid-admin')
  await expect(page.getByTestId('admin-dashboard-view'), details).toBeVisible()
  await page.getByTestId('admin-exit-link').click()
  await expect(page.getByTestId('logout-button'), details).toBeVisible()
  await page.getByTestId('logout-button').click()
  await expect(page.getByTestId('login-link'), details).toBeVisible()
})

test('an expired Session is treated as anonymous after reload', async ({ page }) => {
  let identity: Identity = 'admin'
  await page.route('**/api/v1/auth/me', (route) => fulfillJson(route, authResponse(identity)))

  await page.goto('/admin')
  await expect(page.getByTestId('admin-dashboard-view')).toBeVisible()

  identity = 'anonymous'
  await page.reload()

  const details = await diagnostic(page, '/admin', 'anonymous', 'session-expired')
  await expect(page, details).toHaveURL(/\/login$/)
  await expect(page.getByTestId('login-view'), details).toBeVisible()
})

test('invalid auth responses never enter navigation state', async ({ page }) => {
  await mockAuthentication(page, 'invalid')

  await page.goto('/')

  const details = await diagnostic(page, '/', 'invalid', 'invalid-response')
  await expect(page.getByTestId('login-link'), details).toBeVisible()
  await expect(page.getByTestId('admin-link'), details).toHaveCount(0)
  await expect(page.getByText('DO_NOT_RENDER_THIS_VALUE'), details).toHaveCount(0)
})
