import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
} from '@tanstack/vue-query'
import type { AuthClient } from './auth-client'

export const authQueryKey = ['auth', 'me'] as const

export function createAuthQueryOptions(client: AuthClient) {
  return queryOptions({
    queryKey: authQueryKey,
    queryFn: () => client.getAuthState(),
    retry: false,
  })
}

export function useAuthQuery(client: AuthClient) {
  return useQuery(createAuthQueryOptions(client))
}

export async function logoutWithQueryClient(
  client: AuthClient,
  queryClient: QueryClient,
): Promise<void> {
  await client.logout()
  queryClient.removeQueries({ queryKey: authQueryKey, exact: true })
}

export function useAuthLogout(client: AuthClient) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => logoutWithQueryClient(client, queryClient),
    retry: false,
  })
}
