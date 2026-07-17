import { z } from 'zod'
import { parsePublicEnv } from '../../config/env'

const problemDetailSchema = z.strictObject({
  type: z.string().min(1),
  title: z.string().min(1),
  status: z.number().int().min(400).max(599),
  detail: z.string(),
  instance: z.string().startsWith('/'),
  code: z.string().min(1),
})

export type PublicApiErrorKind =
  | 'network'
  | 'invalid_request'
  | 'not_found'
  | 'response_error'
  | 'invalid_response'

export interface PublicApiFailure {
  kind: PublicApiErrorKind
  status?: number
  code?: string
}

export type PublicApiErrorFactory = (failure: PublicApiFailure) => Error

export function normalizeApiBaseUrl(apiBaseUrl: string): string {
  return parsePublicEnv({ VITE_API_BASE_URL: apiBaseUrl })
    .VITE_API_BASE_URL
    .replace(/\/+$/, '')
}

export async function getPublicJson<Result>(
  url: string,
  fetchImplementation: typeof globalThis.fetch,
  parse: (input: unknown) => Result,
  createError: PublicApiErrorFactory,
): Promise<Result> {
  let response: Response
  try {
    response = await fetchImplementation(url, {
      headers: { Accept: 'application/json' },
      credentials: 'same-origin',
    })
  } catch {
    throw createError({ kind: 'network' })
  }

  if (!response.ok) {
    let code: string | undefined
    try {
      const parsed = problemDetailSchema.safeParse(await response.json())
      if (parsed.success && parsed.data.status === response.status) {
        code = parsed.data.code
      }
    } catch {
      code = undefined
    }
    const kind = response.status === 400
      ? 'invalid_request'
      : response.status === 404
        ? 'not_found'
        : 'response_error'
    throw createError({ kind, status: response.status, code })
  }

  try {
    return parse(await response.json())
  } catch {
    throw createError({ kind: 'invalid_response', status: response.status })
  }
}
