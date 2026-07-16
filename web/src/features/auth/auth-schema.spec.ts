import { describe, expect, it } from 'vitest'
import { parseAuthState, parseCsrfToken } from './auth-schema'

const authenticatedResponse = {
  authenticated: true,
  user: {
    id: '2d65e30a-f450-4f8e-8ed9-5f36b2f7c322',
    login: 'octocat',
    displayName: 'The Octocat',
    avatarUrl: 'https://avatars.githubusercontent.com/u/1',
    roles: ['USER'],
  },
}

describe('parseAuthState', () => {
  it('parses the anonymous state', () => {
    expect(parseAuthState({ authenticated: false, user: null })).toEqual({
      authenticated: false,
      user: null,
    })
  })

  it.each([
    [['USER']],
    [['USER', 'ADMIN']],
  ])('parses an authenticated state with approved roles %j', (roles) => {
    expect(parseAuthState({
      ...authenticatedResponse,
      user: { ...authenticatedResponse.user, roles },
    })).toEqual({
      ...authenticatedResponse,
      user: { ...authenticatedResponse.user, roles },
    })
  })

  it.each([
    ['unknown role', { ...authenticatedResponse.user, roles: ['OWNER'] }],
    ['duplicate role', { ...authenticatedResponse.user, roles: ['USER', 'USER'] }],
    ['invalid UUID', { ...authenticatedResponse.user, id: 'not-a-uuid' }],
    ['malformed avatar URL', { ...authenticatedResponse.user, avatarUrl: '/avatar.png' }],
  ])('rejects %s', (_case, user) => {
    expect(() => parseAuthState({ authenticated: true, user })).toThrowError()
  })

  it.each([
    ['missing authenticated user', { authenticated: true, user: null }],
    ['anonymous state with a user', { ...authenticatedResponse, authenticated: false }],
    ['top-level sensitive field', {
      authenticated: false,
      user: null,
      accessToken: 'TOP_SECRET_TOKEN',
    }],
    ['nested sensitive field', {
      ...authenticatedResponse,
      user: { ...authenticatedResponse.user, githubId: '123456' },
    }],
  ])('rejects %s', (_case, response) => {
    expect(() => parseAuthState(response)).toThrowError()
  })

  it('returns a detached application value containing only approved fields', () => {
    const input = structuredClone(authenticatedResponse)
    const parsed = parseAuthState(input)

    input.user.login = 'changed-after-parse'

    expect(parsed).toEqual(authenticatedResponse)
    expect(parsed).not.toBe(input)
    expect(parsed.user).not.toBe(input.user)
  })
})

describe('parseCsrfToken', () => {
  it('accepts the fixed CSRF header contract', () => {
    expect(parseCsrfToken({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' })).toEqual({
      token: 'csrf-token',
      headerName: 'X-CSRF-TOKEN',
    })
  })

  it.each([
    { token: '', headerName: 'X-CSRF-TOKEN' },
    { token: 'csrf-token', headerName: 'X-OTHER-TOKEN' },
    { token: 'csrf-token', headerName: 'X-CSRF-TOKEN', secret: 'value' },
  ])('rejects an invalid CSRF response', (response) => {
    expect(() => parseCsrfToken(response)).toThrowError()
  })
})
