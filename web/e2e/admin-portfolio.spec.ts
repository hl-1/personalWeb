import { expect, test, type Page, type Route } from '@playwright/test'

const now = '2026-07-20T00:00:00Z'
const id = '00000000-0000-4000-8000-000000000201'
async function json(route: Route, body: unknown, status = 200, headers?: Record<string, string>) { await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body), headers }) }

async function mockPortfolio(page: Page) {
  let project = { id, slug: 'project', title: 'Project', summary: 'Summary', descriptionMarkdown: 'Body', projectUrl: null, repositoryUrl: null, status: 'DRAFT', featured: false, sortOrder: 0, publishedAt: null, createdAt: now, updatedAt: now, version: 0 }
  let skills: unknown[] = []; let experiences: unknown[] = []
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname; const method = route.request().method(); const body = () => JSON.parse(route.request().postData() ?? '{}')
    if (path === '/api/v1/auth/me') return json(route, { authenticated: true, user: { id, login: 'admin', displayName: 'Admin', avatarUrl: null, roles: ['ADMIN'] } })
    if (path === '/api/v1/auth/csrf') return json(route, { token: 'csrf', headerName: 'X-CSRF-TOKEN' })
    if (path === '/api/v1/admin/portfolio/projects' && method === 'POST') { project = { ...project, ...body() }; return json(route, project, 201, { Location: `/api/v1/admin/portfolio/projects/${id}` }) }
    if (path === `/api/v1/admin/portfolio/projects/${id}` && method === 'GET') return json(route, project)
    if (path.endsWith('/publish')) { project = { ...project, status: 'PUBLISHED', publishedAt: now, version: project.version + 1 }; return json(route, project) }
    if (path.endsWith('/archive')) { project = { ...project, status: 'ARCHIVED', version: project.version + 1 }; return json(route, project) }
    if (path.endsWith('/preview')) return json(route, { html: '<p>Safe preview</p>' })
    if (path === '/api/v1/admin/portfolio/profile' && method === 'GET') return json(route, { id: 1, displayName: 'Owner', headline: 'Engineer', bioMarkdown: 'Bio', seoDescription: null, createdAt: now, updatedAt: now, version: 0 })
    if (path === '/api/v1/admin/portfolio/profile' && method === 'PUT') return json(route, { id: 1, ...body(), createdAt: now, updatedAt: now, version: 1 })
    if (path === '/api/v1/admin/portfolio/skills' && method === 'GET') return json(route, skills)
    if (path === '/api/v1/admin/portfolio/skills' && method === 'POST') { skills = [{ id, ...body(), createdAt: now, updatedAt: now, version: 0 }]; return json(route, skills[0], 201, { Location: `/api/v1/admin/portfolio/skills/${id}` }) }
    if (path === '/api/v1/admin/portfolio/experiences' && method === 'GET') return json(route, experiences)
    if (path === '/api/v1/admin/portfolio/experiences' && method === 'POST') { experiences = [{ id, ...body(), createdAt: now, updatedAt: now, version: 0 }]; return json(route, experiences[0], 201, { Location: `/api/v1/admin/portfolio/experiences/${id}` }) }
    return route.fulfill({ status: 404 })
  })
}

test('project create, publish and archive flow', async ({ page }) => {
  await mockPortfolio(page); await page.goto('/admin/portfolio/projects/new')
  await page.getByLabel('Title').fill('Project'); await page.getByLabel('Slug').fill('project'); await page.getByLabel('Summary').fill('Summary')
  await page.getByLabel('Description', { exact: true }).fill('Body')
  await page.getByRole('button', { name: 'Save', exact: true }).click()
  await expect(page).toHaveURL(new RegExp(`/admin/portfolio/projects/${id}$`))
  await page.getByRole('button', { name: 'Publish', exact: true }).click()
  await page.getByRole('button', { name: 'Archive', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Archive', exact: true })).toHaveCount(0)
})

test('profile, skill and experience writes render immediately', async ({ page }) => {
  await mockPortfolio(page)
  await page.goto('/admin/portfolio/profile'); await page.getByLabel('Headline').fill('Principal Engineer'); await page.getByRole('button', { name: 'Save profile' }).click(); await expect(page.getByLabel('Headline')).toHaveValue('Principal Engineer')
  await page.goto('/admin/portfolio/skills'); await page.getByRole('textbox', { name: 'Name', exact: true }).fill('Java'); await page.getByRole('textbox', { name: 'Category', exact: true }).fill('Backend'); await page.getByRole('button', { name: 'Add skill' }).click(); await expect(page.getByText('Java')).toBeVisible()
  await page.goto('/admin/portfolio/experiences'); await page.getByLabel('Organization').fill('StudyStack'); await page.getByLabel('Role').fill('Engineer'); await page.getByLabel('Start date').fill('2024-01-01'); await page.getByLabel('Summary', { exact: true }).fill('Built systems'); await page.getByRole('button', { name: 'Add experience' }).click(); await expect(page.getByTestId('experience-list')).toContainText('StudyStack')
})
