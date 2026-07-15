import { expect, test, type APIRequestContext } from '@playwright/test'

interface ResponseDetails {
  body: string
  contentType: string
  status: number
  url: string
}

async function fetchDetails(request: APIRequestContext, path: string): Promise<ResponseDetails> {
  const response = await request.get(path, { failOnStatusCode: false })
  return {
    body: await response.text(),
    contentType: response.headers()['content-type'] ?? '',
    status: response.status(),
    url: response.url(),
  }
}

function diagnostic(details: ResponseDetails): string {
  return [
    `url=${details.url}`,
    `status=${details.status}`,
    `content-type=${details.contentType || '<missing>'}`,
    `body=${JSON.stringify(details.body.slice(0, 200))}`,
  ].join(' ')
}

function isSpaHtml(details: ResponseDetails): boolean {
  return details.contentType.includes('text/html') && details.body.includes('<div id="app"></div>')
}

test('frontend routes return the SPA and render stable route markers', async ({ page, request }) => {
  const routes = [
    { path: '/', marker: 'home-view', text: 'StudyStack' },
    { path: '/foundation', marker: 'foundation-view', text: 'StudyStack foundation' },
    { path: '/unknown-page', marker: 'not-found-view', text: 'Page not found' },
  ]

  for (const route of routes) {
    await test.step(route.path, async () => {
      const details = await fetchDetails(request, route.path)
      expect(details.status, diagnostic(details)).toBe(200)
      expect(isSpaHtml(details), diagnostic(details)).toBe(true)

      await page.goto(route.path)
      await expect(page.getByTestId(route.marker)).toContainText(route.text)
    })
  }
})

test('built static asset is served as a real file', async ({ request }) => {
  const home = await fetchDetails(request, '/')
  const assetPath = home.body.match(/<script[^>]+src="([^"]+\.js)"/)?.[1]
  expect(assetPath, diagnostic(home)).toBeTruthy()

  const asset = await fetchDetails(request, assetPath!)
  expect(asset.status, diagnostic(asset)).toBe(200)
  expect(asset.contentType, diagnostic(asset)).toMatch(/javascript/)
  expect(isSpaHtml(asset), diagnostic(asset)).toBe(false)
})

test('backend prefixes and actuator paths never fall through to the SPA', async ({ request }) => {
  const backendPaths = [
    '/api',
    '/api/foundation-check',
    '/oauth2',
    '/oauth2/foundation-check',
    '/login/oauth2',
    '/login/oauth2/foundation-check',
  ]

  for (const path of backendPaths) {
    const details = await fetchDetails(request, path)
    expect(details.status, diagnostic(details)).toBe(404)
    expect(isSpaHtml(details), diagnostic(details)).toBe(false)
  }

  for (const path of ['/actuator', '/actuator/health']) {
    const details = await fetchDetails(request, path)
    expect(details.status, diagnostic(details)).toBe(404)
    expect(isSpaHtml(details), diagnostic(details)).toBe(false)
  }
})
