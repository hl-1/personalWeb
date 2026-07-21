import { parsePublicEnv } from '../../config/env'
import {
  SessionApiError,
  createSessionClient,
} from '../../shared/api/session-client'
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

export function createAuthClient(options: AuthClientOptions): AuthClient {
  const validatedBaseUrl = parsePublicEnv({ VITE_API_BASE_URL: options.apiBaseUrl })
    .VITE_API_BASE_URL
  const apiBaseUrl = validatedBaseUrl.replace(/\/+$/, '')
  const session = createSessionClient({ apiBaseUrl, fetch: options.fetch })

  function toAuthError(error: unknown): AuthApiError {
    if (error instanceof SessionApiError) {
      const kind: AuthApiErrorKind = error.kind === 'network'
        ? 'network'
        : error.kind === 'unauthorized'
          ? 'unauthorized'
          : error.kind === 'forbidden'
            ? 'forbidden'
            : 'invalid_response'
      return new AuthApiError(kind, error.status)
    }
    return new AuthApiError('invalid_response')
  }

  return {
    async getAuthState(): Promise<AuthState> {
      try {
        return (await session.request('/v1/auth/me', {
          method: 'GET',
          parse: parseAuthState,
        })).data
      } catch (error) {
        throw toAuthError(error)
      }
    },

    async getCsrfToken(): Promise<CsrfToken> {
      try {
        const token = await session.getCsrfToken()
        return parseCsrfToken(token)
      } catch (error) {
        throw toAuthError(error)
      }
    },

    async logout(): Promise<void> {
      try {
        await session.request('/v1/auth/logout', {
          method: 'POST',
          expectedStatus: 204,
          retryCsrf: false,
        })
      } catch (error) {
        throw toAuthError(error)
      }
    },
  }
}
