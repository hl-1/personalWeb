import { expect, test, type APIRequestContext, type Page, type Response, type Route } from '@playwright/test'

interface ResponseDetails {
  contentType: string
  status: number
  url: string
}

const timestamp = '2026-07-17T06:00:00Z'
const articleSummary = {
  id: '00000000-0000-4000-8000-000000000001',
  slug: 'public-article',
  title: 'Reliable public article',
  summary: 'A published article rendered through the public content client.',
  category: 'java',
  tags: ['spring'],
  publishedAt: timestamp,
  updatedAt: timestamp,
}
const projectSummary = {
  id: '00000000-0000-4000-8000-000000000010',
  slug: 'public-project',
  title: 'Reliable public project',
  summary: 'A published project rendered through the public portfolio client.',
  featured: true,
  publishedAt: timestamp,
  updatedAt: timestamp,
}

async function fulfillJson(route: Route, body: unknown, status = 200): Promise<void> {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

async function mockPublicApis(page: Page): Promise<void> {
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/auth/me') {
      return fulfillJson(route, { authenticated: false, user: null })
    }
    if (path === '/api/v1/articles/public-article') {
      return fulfillJson(route, {
        ...articleSummary,
        contentHtml: '<h2>Article body</h2><p>Safe published content.</p>',
        seoTitle: 'Reliable article',
        seoDescription: 'Reliable article description',
        canonicalPath: '/blog/public-article',
      })
    }
    if (path === '/api/v1/articles') {
      return fulfillJson(route, pageOf([articleSummary]))
    }
    if (path === '/api/v1/categories') {
      return fulfillJson(route, [{ name: 'Java', slug: 'java', publishedArticleCount: 1 }])
    }
    if (path === '/api/v1/tags') {
      return fulfillJson(route, [{ name: 'Spring', slug: 'spring', publishedArticleCount: 1 }])
    }
    if (path === '/api/v1/portfolio/profile') {
      return fulfillJson(route, {
        displayName: 'StudyStack Author',
        headline: 'Platform engineer',
        bioHtml: '<p>Public profile biography.</p>',
        seoDescription: 'Public profile description',
      })
    }
    if (path === '/api/v1/portfolio/projects/public-project') {
      return fulfillJson(route, {
        ...projectSummary,
        descriptionHtml: '<h2>Project body</h2><p>Safe project content.</p>',
        projectUrl: 'https://example.com/project',
        repositoryUrl: null,
        canonicalPath: '/projects/public-project',
      })
    }
    if (path === '/api/v1/portfolio/projects') {
      return fulfillJson(route, pageOf([projectSummary]))
    }
    if (path === '/api/v1/portfolio/skills') {
      return fulfillJson(route, [{
        id: '00000000-0000-4000-8000-000000000011',
        name: 'Java',
        category: 'Backend',
        summary: 'Reliable services',
      }])
    }
    if (path === '/api/v1/portfolio/experiences') {
      return fulfillJson(route, [{
        id: '00000000-0000-4000-8000-000000000012',
        organization: 'StudyStack',
        role: 'Engineer',
        startDate: '2024-01-01',
        endDate: null,
        summaryHtml: '<p>Built dependable systems.</p>',
      }])
    }
    return route.fulfill({ status: 404 })
  })
}

async function mockEmptyPublicApis(page: Page): Promise<void> {
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/auth/me') {
      return fulfillJson(route, { authenticated: false, user: null })
    }
    if (path === '/api/v1/portfolio/profile') {
      return fulfillJson(route, {
        type: 'urn:studystack:problem:portfolio-not-found',
        title: 'Portfolio resource not found',
        status: 404,
        detail: 'The requested portfolio resource is unavailable',
        instance: path,
        code: 'portfolio_not_found',
      }, 404)
    }
    if (path === '/api/v1/articles' || path === '/api/v1/portfolio/projects') {
      return fulfillJson(route, pageOf([]))
    }
    if (path === '/api/v1/categories' || path === '/api/v1/tags'
      || path === '/api/v1/portfolio/skills' || path === '/api/v1/portfolio/experiences') {
      return fulfillJson(route, [])
    }
    return route.fulfill({ status: 404 })
  })
}

function pageOf(items: unknown[]) {
  return { items, page: 0, size: 10, totalElements: items.length, totalPages: 1 }
}

async function pageDiagnostic(page: Page, path: string, response: Response | null): Promise<string> {
  const markers = await page.locator('[data-testid]:visible').evaluateAll((elements) =>
    elements.slice(0, 12).map((element) => element.getAttribute('data-testid')),
  )
  return [
    `route=${path}`,
    `url=${page.url()}`,
    `status=${response?.status() ?? '<missing>'}`,
    `content-type=${response?.headers()['content-type'] ?? '<missing>'}`,
    `page-state=${markers.join(',') || '<none>'}`,
  ].join(' ')
}

async function fetchDetails(request: APIRequestContext, path: string): Promise<ResponseDetails> {
  const response = await request.get(path, { failOnStatusCode: false })
  return {
    contentType: response.headers()['content-type'] ?? '',
    status: response.status(),
    url: response.url(),
  }
}

function responseDiagnostic(details: ResponseDetails): string {
  return `url=${details.url} status=${details.status} content-type=${details.contentType || '<missing>'} page-state=not-spa`
}

test('route mocks render all public pages with published content', async ({ page }) => {
  await mockPublicApis(page)
  const routes = [
    { path: '/', marker: 'home-view', text: 'Reliable public article' },
    { path: '/about', marker: 'about-view', text: 'Platform engineer' },
    { path: '/blog', marker: 'blog-list-view', text: 'Reliable public article' },
    { path: '/blog/public-article', marker: 'article-detail-view', text: 'Safe published content.' },
    { path: '/projects', marker: 'project-list-view', text: 'Reliable public project' },
    { path: '/projects/public-project', marker: 'project-detail-view', text: 'Safe project content.' },
  ]

  for (const route of routes) {
    await test.step(route.path, async () => {
      const response = await page.goto(route.path)
      const details = await pageDiagnostic(page, route.path, response)
      expect(response?.status(), details).toBe(200)
      await expect(page.getByTestId(route.marker), details).toContainText(route.text)
    })
  }
})

test('public pages expose stable service error and not-found states', async ({ page }) => {
  await page.route('**/api/v1/auth/me', (route) => fulfillJson(route, {
    authenticated: false,
    user: null,
  }))
  await page.route('**/api/v1/articles?**', (route) => fulfillJson(route, {
    type: 'about:blank', title: 'Unavailable', status: 503, detail: 'Unavailable',
    instance: '/api/v1/articles', code: 'service_unavailable',
  }, 503))
  let response = await page.goto('/blog')
  let details = await pageDiagnostic(page, '/blog', response)
  await expect(page.getByTestId('error-state'), details).toContainText('could not be loaded')

  await page.route('**/api/v1/articles/missing-article', (route) => fulfillJson(route, {
    type: 'about:blank', title: 'Not Found', status: 404, detail: 'Not found',
    instance: '/api/v1/articles/missing-article', code: 'article_not_found',
  }, 404))
  response = await page.goto('/blog/missing-article')
  details = await pageDiagnostic(page, '/blog/missing-article', response)
  await expect(page.getByTestId('not-found-state'), details).toContainText('Article not found')
  await expect(page.getByText('article_not_found'), details).toHaveCount(0)
})

test('mobile empty-data pages stay usable without horizontal overflow', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await mockEmptyPublicApis(page)
  const routes = [
    { path: '/', text: 'Public profile coming soon' },
    { path: '/about', text: 'Public profile coming soon' },
    { path: '/blog', text: 'No published articles' },
    { path: '/projects', text: 'No published projects' },
  ]

  for (const route of routes) {
    await test.step(route.path, async () => {
      const response = await page.goto(route.path)
      const details = await pageDiagnostic(page, route.path, response)
      expect(response?.status(), details).toBe(200)
      await expect(page.getByText(route.text, { exact: false }).first(), details).toBeVisible()
      expect(await page.evaluate(() =>
        document.documentElement.scrollWidth <= document.documentElement.clientWidth,
      ), details).toBe(true)
    })
  }
})

test('Caddy proxies sitemap and robots instead of returning SPA HTML', async ({ request }) => {
  for (const path of ['/sitemap.xml', '/robots.txt']) {
    const details = await fetchDetails(request, path)
    const diagnostic = responseDiagnostic(details)
    expect(details.status, diagnostic).toBe(200)
    expect(details.contentType, diagnostic).not.toContain('text/html')
    expect(details.contentType, diagnostic).toMatch(
      path.endsWith('.xml') ? /application\/xml/ : /text\/plain/,
    )
  }
})
