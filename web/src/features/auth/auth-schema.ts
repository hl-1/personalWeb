import { z } from 'zod'

const authRoleSchema = z.enum(['USER', 'ADMIN'])

const avatarUrlSchema = z.string().max(2048).superRefine((value, context) => {
  try {
    const url = new URL(value)
    if (url.protocol !== 'https:' || !url.hostname) {
      context.addIssue({ code: 'custom', message: 'must be an absolute HTTPS URL' })
    }
  } catch {
    context.addIssue({ code: 'custom', message: 'must be an absolute HTTPS URL' })
  }
})

const rolesSchema = z.array(authRoleSchema).min(1).superRefine((roles, context) => {
  if (new Set(roles).size !== roles.length) {
    context.addIssue({ code: 'custom', message: 'must not contain duplicate roles' })
  }
})

const authUserResponseSchema = z.strictObject({
  id: z.string().uuid(),
  login: z.string().min(1).max(255),
  displayName: z.string().min(1).max(255),
  avatarUrl: avatarUrlSchema.nullable(),
  roles: rolesSchema,
})

const authStateResponseSchema = z.discriminatedUnion('authenticated', [
  z.strictObject({
    authenticated: z.literal(false),
    user: z.null(),
  }),
  z.strictObject({
    authenticated: z.literal(true),
    user: authUserResponseSchema,
  }),
])

const csrfTokenResponseSchema = z.strictObject({
  token: z.string().min(1),
  headerName: z.literal('X-CSRF-TOKEN'),
})

export type AuthRole = z.infer<typeof authRoleSchema>

export interface AuthUser {
  id: string
  login: string
  displayName: string
  avatarUrl: string | null
  roles: AuthRole[]
}

export type AuthState =
  | { authenticated: false; user: null }
  | { authenticated: true; user: AuthUser }

export interface CsrfToken {
  token: string
  headerName: 'X-CSRF-TOKEN'
}

export function parseAuthState(input: unknown): AuthState {
  const parsed = authStateResponseSchema.parse(input)
  if (!parsed.authenticated) {
    return { authenticated: false, user: null }
  }

  return {
    authenticated: true,
    user: {
      id: parsed.user.id,
      login: parsed.user.login,
      displayName: parsed.user.displayName,
      avatarUrl: parsed.user.avatarUrl,
      roles: parsed.user.roles.map((role) => role),
    },
  }
}

export function parseCsrfToken(input: unknown): CsrfToken {
  const parsed = csrfTokenResponseSchema.parse(input)
  return { token: parsed.token, headerName: parsed.headerName }
}
