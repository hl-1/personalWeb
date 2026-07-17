import { describe, expect, it } from 'vitest'
import { parseSafeHtml } from './safe-html'

describe('parseSafeHtml', () => {
  it('accepts sanitized markdown HTML without rewriting it', () => {
    const html = '<h2>Safe</h2><p><a href="https://example.com" rel="nofollow noopener noreferrer">Link</a></p>'

    expect(parseSafeHtml(html)).toBe(html)
  })

  it('accepts server-approved path-relative links', () => {
    const html = '<p><a href="docs/safe-page">Internal documentation</a></p>'

    expect(parseSafeHtml(html)).toBe(html)
  })

  it.each([
    '<script>alert(1)</script>',
    '<p onclick="alert(1)">Unsafe</p>',
    '<a href="javascript:alert(1)">Unsafe</a>',
    '<a href="data:text/html,unsafe">Unsafe</a>',
    '<iframe src="https://example.com"></iframe>',
    '<img src="https://example.com/image.png">',
    '<form><input name="secret"></form>',
  ])('rejects dangerous HTML %s', (html) => {
    expect(() => parseSafeHtml(html)).toThrowError(/safe HTML/i)
  })
})
