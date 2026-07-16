import { parsePublicEnv } from '../../config/env'
import {
  parseAuthState,
  parseCsrfToken,
  type AuthState,
  type CsrfToken,
} from './auth-schema'

export type AuthApiErrorKind =
  | 'network'
  | 'unauthorized'
  | 'forbidden'
  | 'invalid_response'

const errorMessages: Record<AuthApiErrorKind, string> = {
  network: 'Authentication request failed',
  unauthorized: 'Authentication is required',
  forbidden: 'Authentication request was forbidden',
  invalid_response: 'Authentication response was invalid',
}

export class AuthApiError extends Error {
  readonly kind: AuthApiErrorKind
  readonly status: number | undefined

  constructor(kind: AuthApiErrorKind, status?: number) {
    super(errorMessages[kind])
    this.name = 'AuthApiError'
    this.kind = kind
    this.status = status
  }
}

export interface AuthClient {
  getAuthState(): Promise<AuthState>
  getCsrfToken(): Promise<CsrfToken>
  logout(): Promise<void>
}

export interface AuthClientOptions {
  apiBaseUrl: string
  fetch?: typeof globalThis.fetch
}

function responseError(response: Response): AuthApiError | undefined {
  if (response.status === 401) {
    return new AuthApiError('unauthorized', response.status)
  }
  if (response.status === 403) {
    return new AuthApiError('forbidden', response.status)
  }
  if (!response.ok) {
    return new AuthApiError('invalid_response', response.status)
  }
  return undefined
}

export function createAuthClient(options: AuthClientOptions): AuthClient {
  const validatedBaseUrl = parsePublicEnv({ VITE_API_BASE_URL: options.apiBaseUrl })
    .VITE_API_BASE_URL
  const apiBaseUrl = validatedBaseUrl.replace(/\/+$/, '')
  const fetchImplementation = options.fetch ?? globalThis.fetch
  let cachedCsrfToken: CsrfToken | undefined

  async function fetchResponse(path: string, init: RequestInit): Promise<Response> {
    try {
      return await fetchImplementation(`${apiBaseUrl}${path}`, init)
    } catch {
      throw new AuthApiError('network')
    }
  }

  async function fetchJson<T>(
    path: string,
    parse: (input: unknown) => T,
  ): Promise<T> {
    const response = await fetchResponse(path, {
      headers: { Accept: 'application/json' },
      credentials: 'same-origin',
    })
    const error = responseError(response)
    if (error) {
      throw error
    }

    try {
      return parse(await response.json())
    } catch {
      throw new AuthApiError('invalid_response', response.status)
    }
  }

  return {
    getAuthState(): Promise<AuthState> {
      return fetchJson('/v1/auth/me', parseAuthState)
    },

    async getCsrfToken(): Promise<CsrfToken> {
      if (!cachedCsrfToken) {
        cachedCsrfToken = await fetchJson('/v1/auth/csrf', parseCsrfToken)
      }
      return cachedCsrfToken
    },

    async logout(): Promise<void> {
      const csrfToken = await this.getCsrfToken()
      const response = await fetchResponse('/v1/auth/logout', {
        method: 'POST',
        headers: {
          Accept: 'application/json',
          [csrfToken.headerName]: csrfToken.token,
        },
        credentials: 'same-origin',
      })
      const error = responseError(response)
      if (error) {
        if (error.kind === 'forbidden') {
          cachedCsrfToken = undefined
        }
        throw error
      }
      if (response.status !== 204) {
        throw new AuthApiError('invalid_response', response.status)
      }
      cachedCsrfToken = undefined
    },
  }
}
