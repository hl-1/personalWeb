import { z } from 'zod'
import { parsePublicEnv } from '../../config/env'
import { problemDetailSchema } from './problem-schema'

const csrfTokenSchema = z.strictObject({
  token: z.string().min(1),
  headerName: z.literal('X-CSRF-TOKEN'),
})

export interface SessionCsrfToken {
  token: string
  headerName: 'X-CSRF-TOKEN'
}

export type SessionApiErrorKind =
  | 'network'
  | 'unauthorized'
  | 'forbidden'
  | 'conflict'
  | 'response_error'
  | 'invalid_response'

const messages: Record<SessionApiErrorKind, string> = {
  network: 'Session request failed',
  unauthorized: 'Authentication is required',
  forbidden: 'The request was forbidden',
  conflict: 'The resource changed on the server',
  response_error: 'The server rejected the request',
  invalid_response: 'The server response was invalid',
}

export class SessionApiError extends Error {
  readonly kind: SessionApiErrorKind
  readonly status: number | undefined
  readonly code: string | undefined
  readonly fieldErrors: Record<string, string[]> | undefined

  constructor(
    kind: SessionApiErrorKind,
    status?: number,
    code?: string,
    fieldErrors?: Record<string, string[]>,
  ) {
    super(messages[kind])
    this.name = 'SessionApiError'
    this.kind = kind
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

interface ParsedRequestOptions<Result> {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  parse: (input: unknown) => Result
  expectedStatus?: number
  retryCsrf?: boolean
}

interface EmptyRequestOptions {
  method: 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  expectedStatus: 204
  retryCsrf?: boolean
}

export interface SessionResponse<Result> {
  data: Result
  location: string | null
}

export interface SessionClient {
  getCsrfToken(): Promise<SessionCsrfToken>
  clearCsrfToken(): void
  request<Result>(path: string, options: ParsedRequestOptions<Result>): Promise<SessionResponse<Result>>
  request(path: string, options: EmptyRequestOptions): Promise<SessionResponse<undefined>>
}

export interface SessionClientOptions {
  apiBaseUrl: string
  fetch?: typeof globalThis.fetch
}

function errorKind(status: number): SessionApiErrorKind {
  if (status === 401) return 'unauthorized'
  if (status === 403) return 'forbidden'
  if (status === 409) return 'conflict'
  return 'response_error'
}

function copyFieldErrors(input: Record<string, string[]> | undefined) {
  return input === undefined
    ? undefined
    : Object.fromEntries(Object.entries(input).map(([field, values]) => [field, [...values]]))
}

export function createSessionClient(options: SessionClientOptions): SessionClient {
  const apiBaseUrl = parsePublicEnv({ VITE_API_BASE_URL: options.apiBaseUrl })
    .VITE_API_BASE_URL.replace(/\/+$/, '')
  const fetchImplementation = options.fetch ?? globalThis.fetch
  let csrfToken: SessionCsrfToken | undefined
  let csrfRequest: Promise<SessionCsrfToken> | undefined

  async function fetchResponse(path: string, init: RequestInit): Promise<Response> {
    try {
      return await fetchImplementation(`${apiBaseUrl}${path}`, init)
    } catch {
      throw new SessionApiError('network')
    }
  }

  async function readProblem(response: Response) {
    try {
      const parsed = problemDetailSchema.safeParse(await response.json())
      return parsed.success && parsed.data.status === response.status ? parsed.data : undefined
    } catch {
      return undefined
    }
  }

  async function getCsrfToken(): Promise<SessionCsrfToken> {
    if (csrfToken) return csrfToken
    if (!csrfRequest) {
      csrfRequest = (async () => {
        const response = await fetchResponse('/v1/auth/csrf', {
          headers: { Accept: 'application/json' },
          credentials: 'same-origin',
        })
        if (!response.ok) {
          throw new SessionApiError(errorKind(response.status), response.status)
        }
        try {
          const parsed = csrfTokenSchema.parse(await response.json())
          csrfToken = { token: parsed.token, headerName: parsed.headerName }
          return csrfToken
        } catch {
          throw new SessionApiError('invalid_response', response.status)
        }
      })().finally(() => { csrfRequest = undefined })
    }
    return csrfRequest
  }

  async function execute<Result>(
    path: string,
    requestOptions: ParsedRequestOptions<Result> | EmptyRequestOptions,
    retried = false,
  ): Promise<SessionResponse<Result | undefined>> {
    if (!path.startsWith('/') || path.startsWith('//')) {
      throw new Error('Session request path must be same-origin')
    }
    const method = requestOptions.method ?? 'GET'
    const headers: Record<string, string> = { Accept: 'application/json' }
    if (requestOptions.body !== undefined) headers['Content-Type'] = 'application/json'
    if (method !== 'GET') {
      const token = await getCsrfToken()
      headers[token.headerName] = token.token
    }
    const response = await fetchResponse(path, {
      ...(method === 'GET' ? {} : { method }),
      credentials: 'same-origin',
      headers,
      ...(requestOptions.body === undefined ? {} : { body: JSON.stringify(requestOptions.body) }),
    })
    if (!response.ok) {
      const problem = await readProblem(response)
      if (response.status === 401 || response.status === 403) csrfToken = undefined
      if (method !== 'GET'
          && response.status === 403
          && problem?.code === 'csrf_failed'
          && requestOptions.retryCsrf !== false
          && !retried) {
        return execute(path, requestOptions, true)
      }
      throw new SessionApiError(
        errorKind(response.status), response.status, problem?.code,
        copyFieldErrors(problem?.fieldErrors),
      )
    }
    if (requestOptions.expectedStatus !== undefined && response.status !== requestOptions.expectedStatus) {
      throw new SessionApiError('invalid_response', response.status)
    }
    if (response.status === 204) {
      csrfToken = method === 'POST' && path === '/v1/auth/logout' ? undefined : csrfToken
      return { data: undefined, location: response.headers.get('Location') }
    }
    if (!('parse' in requestOptions)) {
      throw new SessionApiError('invalid_response', response.status)
    }
    try {
      return {
        data: requestOptions.parse(await response.json()),
        location: response.headers.get('Location'),
      }
    } catch {
      throw new SessionApiError('invalid_response', response.status)
    }
  }

  function request<Result>(
    path: string,
    requestOptions: ParsedRequestOptions<Result>,
  ): Promise<SessionResponse<Result>>
  function request(
    path: string,
    requestOptions: EmptyRequestOptions,
  ): Promise<SessionResponse<undefined>>
  function request<Result>(
    path: string,
    requestOptions: ParsedRequestOptions<Result> | EmptyRequestOptions,
  ): Promise<SessionResponse<Result | undefined>> {
    return execute(path, requestOptions)
  }

  return {
    getCsrfToken,
    clearCsrfToken() { csrfToken = undefined },
    request,
  }
}
