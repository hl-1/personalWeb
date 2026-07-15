import { describe, expect, it } from 'vitest'
import { parsePublicEnv } from './env'

describe('parsePublicEnv', () => {
  it('accepts a same-origin absolute API path', () => {
    expect(parsePublicEnv({ VITE_API_BASE_URL: '/api' })).toEqual({
      VITE_API_BASE_URL: '/api',
    })
  })

  it.each([
    ['missing value', undefined],
    ['absolute URL', 'https://example.com/api'],
    ['protocol-relative URL', '//example.com/api'],
    ['URL with credentials', 'https://user:password@example.com/api'],
    ['path with a query string', '/api?token=value'],
    ['path without a leading slash', 'api'],
  ])('rejects %s', (_case, value) => {
    expect(() => parsePublicEnv({ VITE_API_BASE_URL: value })).toThrowError(
      /VITE_API_BASE_URL/,
    )
  })

  it('does not echo the rejected value', () => {
    const secret = 'TOP_SECRET_VALUE'

    expect(() =>
      parsePublicEnv({
        VITE_API_BASE_URL: `https://user:${secret}@example.com/api`,
      }),
    ).toThrowError(/VITE_API_BASE_URL/)

    try {
      parsePublicEnv({
        VITE_API_BASE_URL: `https://user:${secret}@example.com/api`,
      })
    } catch (error) {
      expect(String(error)).not.toContain(secret)
    }
  })
})
