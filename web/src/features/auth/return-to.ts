const returnToStorageKey = 'studystack.auth.returnTo'
const sameOriginBase = new URL('https://studystack.invalid')
const sensitiveQueryKeys = new Set([
  'access_token',
  'code',
  'role',
  'roles',
  'session',
  'session_id',
  'state',
  'token',
  'user',
])

function containsControlCharacter(value: string): boolean {
  return Array.from(value).some((character) => {
    const code = character.charCodeAt(0)
    return code <= 31 || code === 127
  })
}

export interface ReturnToStorage {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
  removeItem(key: string): void
}

export function isSafeReturnTo(value: unknown): value is string {
  if (typeof value !== 'string'
    || !value.startsWith('/')
    || value.startsWith('//')
    || value.includes('\\')
    || containsControlCharacter(value)) {
    return false
  }

  let decoded: string
  try {
    decoded = decodeURIComponent(value)
  } catch {
    return false
  }
  if (decoded.startsWith('//') || decoded.includes('\\') || containsControlCharacter(decoded)) {
    return false
  }

  const parsed = new URL(value, sameOriginBase)
  if (parsed.origin !== sameOriginBase.origin || parsed.username || parsed.password) {
    return false
  }

  return !Array.from(parsed.searchParams.keys())
    .some((key) => sensitiveQueryKeys.has(key.toLowerCase()))
}

export function rememberReturnTo(storage: ReturnToStorage, value: unknown): boolean {
  if (!isSafeReturnTo(value)) {
    return false
  }
  storage.setItem(returnToStorageKey, value)
  return true
}

export function consumeReturnTo(storage: ReturnToStorage): string {
  const value = storage.getItem(returnToStorageKey)
  storage.removeItem(returnToStorageKey)
  return isSafeReturnTo(value) ? value : '/'
}
