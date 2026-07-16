import { describe, expect, it } from 'vitest'
import {
  consumeReturnTo,
  isSafeReturnTo,
  rememberReturnTo,
  type ReturnToStorage,
} from './return-to'

function memoryStorage(): ReturnToStorage & { values: Map<string, string> } {
  const values = new Map<string, string>()
  return {
    values,
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
  }
}

describe('returnTo', () => {
  it.each([
    '/',
    '/admin',
    '/foundation?tab=notes#today',
  ])('accepts the same-origin path %s', (path) => {
    expect(isSafeReturnTo(path)).toBe(true)
  })

  it.each([
    '',
    'admin',
    '//example.com/admin',
    'https://example.com/admin',
    '\\example.com\\admin',
    '/\\example.com/admin',
    '/admin\nnext',
    '/%5Cexample.com/admin',
    '/admin?access_token=secret',
    '/admin?roles=ADMIN',
    '/admin?user=octocat',
  ])('rejects the unsafe return path %j', (path) => {
    expect(isSafeReturnTo(path)).toBe(false)
  })

  it('stores and consumes only a validated path', () => {
    const storage = memoryStorage()

    expect(rememberReturnTo(storage, '/admin')).toBe(true)
    expect(consumeReturnTo(storage)).toBe('/admin')
    expect(storage.values.size).toBe(0)
  })

  it('does not write an invalid path and falls back to home', () => {
    const storage = memoryStorage()

    expect(rememberReturnTo(storage, '//example.com')).toBe(false)
    expect(storage.values.size).toBe(0)
    expect(consumeReturnTo(storage)).toBe('/')
  })

  it('removes an invalid value that was already present', () => {
    const storage = memoryStorage()
    storage.values.set('studystack.auth.returnTo', 'https://example.com')

    expect(consumeReturnTo(storage)).toBe('/')
    expect(storage.values.size).toBe(0)
  })
})
