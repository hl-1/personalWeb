import {
  mutationOptions,
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
} from '@tanstack/vue-query'
import type {
  AdminArticleInput,
  AdminArticleUpdateInput,
  AdminClient,
} from './admin-client'
import {
  parseAdminArticleListParams,
  type AdminArticleListParams,
} from './admin-schema'

export const adminQueryKey = ['admin'] as const
const articleQueryKey = [...adminQueryKey, 'articles'] as const
const articleListsQueryKey = [...articleQueryKey, 'list'] as const

export function articleListQueryKey(params: AdminArticleListParams = {}) {
  const normalized = parseAdminArticleListParams(params)
  return [
    ...articleListsQueryKey,
    {
      page: normalized.page,
      size: normalized.size,
      status: normalized.status ?? null,
      query: normalized.query ?? null,
    },
  ] as const
}

export function articleDetailQueryKey(id: string) {
  return [...articleQueryKey, id] as const
}

export const categoryListQueryKey = [...adminQueryKey, 'categories'] as const
export const tagListQueryKey = [...adminQueryKey, 'tags'] as const

export function createArticleListQueryOptions(
  client: AdminClient,
  params: AdminArticleListParams = {},
) {
  const normalized = parseAdminArticleListParams(params)
  return queryOptions({
    queryKey: articleListQueryKey(normalized),
    queryFn: () => client.listArticles(normalized),
    retry: false,
  })
}

export function createArticleQueryOptions(client: AdminClient, id: string) {
  return queryOptions({
    queryKey: articleDetailQueryKey(id),
    queryFn: () => client.getArticle(id),
    retry: false,
  })
}

export function createCategoryQueryOptions(client: AdminClient) {
  return queryOptions({ queryKey: categoryListQueryKey, queryFn: () => client.listCategories(), retry: false })
}

export function createTagQueryOptions(client: AdminClient) {
  return queryOptions({ queryKey: tagListQueryKey, queryFn: () => client.listTags(), retry: false })
}

export interface UpdateArticleVariables { id: string; input: AdminArticleUpdateInput }

export function createArticleMutationOptions(
  client: AdminClient,
  queryClient: QueryClient,
  id: string,
) {
  return mutationOptions({
    mutationFn: (variables: UpdateArticleVariables) => client.updateArticle(variables.id, variables.input),
    retry: 0,
    async onSuccess() {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: articleDetailQueryKey(id), exact: true }),
        queryClient.invalidateQueries({ queryKey: articleListsQueryKey }),
      ])
    },
  })
}

export function createNewArticleMutationOptions(client: AdminClient, queryClient: QueryClient) {
  return mutationOptions({
    mutationFn: (input: AdminArticleInput) => client.createArticle(input),
    retry: 0,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: articleListsQueryKey }),
  })
}

export function useAdminArticleList(client: AdminClient, params: AdminArticleListParams = {}) {
  return useQuery(createArticleListQueryOptions(client, params))
}

export function useAdminArticle(client: AdminClient, id: string) {
  return useQuery(createArticleQueryOptions(client, id))
}

export function useAdminCategories(client: AdminClient) {
  return useQuery(createCategoryQueryOptions(client))
}

export function useAdminTags(client: AdminClient) {
  return useQuery(createTagQueryOptions(client))
}

export function useUpdateArticle(client: AdminClient, id: string) {
  return useMutation(createArticleMutationOptions(client, useQueryClient(), id))
}

export function useCreateArticle(client: AdminClient) {
  return useMutation(createNewArticleMutationOptions(client, useQueryClient()))
}
