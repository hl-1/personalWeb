import { queryOptions, useQuery } from '@tanstack/vue-query'
import { parseOptionalPublicSlug } from '../../shared/api/slug-schema'
import type { ContentClient } from './content-client'
import {
  parseArticleListParams,
  parseArticleSlug,
  type ArticleListParams,
} from './content-schema'

export const contentQueryKey = ['content'] as const

export function articleListQueryKey(params: ArticleListParams = {}) {
  const normalized = parseArticleListParams(params)
  return [
    ...contentQueryKey,
    'articles',
    {
      page: normalized.page,
      size: normalized.size,
      category: normalized.category ?? null,
      tag: normalized.tag ?? null,
    },
  ] as const
}

export function articleDetailQueryKey(input: string) {
  return [...contentQueryKey, 'article', parseOptionalPublicSlug(input) ?? input] as const
}

export const categoryListQueryKey = [...contentQueryKey, 'categories'] as const
export const tagListQueryKey = [...contentQueryKey, 'tags'] as const

export function createArticleListQueryOptions(
  client: ContentClient,
  params: ArticleListParams = {},
) {
  const normalized = parseArticleListParams(params)
  return queryOptions({
    queryKey: articleListQueryKey(normalized),
    queryFn: () => client.getArticles(normalized),
    retry: false,
  })
}

export function createArticleDetailQueryOptions(client: ContentClient, input: string) {
  const slug = parseOptionalPublicSlug(input)
  return queryOptions({
    queryKey: articleDetailQueryKey(input),
    queryFn: () => client.getArticle(parseArticleSlug(input)),
    enabled: slug !== undefined,
    retry: false,
  })
}

export function createCategoryListQueryOptions(client: ContentClient) {
  return queryOptions({
    queryKey: categoryListQueryKey,
    queryFn: () => client.getCategories(),
    retry: false,
  })
}

export function createTagListQueryOptions(client: ContentClient) {
  return queryOptions({
    queryKey: tagListQueryKey,
    queryFn: () => client.getTags(),
    retry: false,
  })
}

export function useArticleListQuery(client: ContentClient, params: ArticleListParams = {}) {
  return useQuery(createArticleListQueryOptions(client, params))
}

export function useArticleDetailQuery(client: ContentClient, slug: string) {
  return useQuery(createArticleDetailQueryOptions(client, slug))
}

export function useCategoryListQuery(client: ContentClient) {
  return useQuery(createCategoryListQueryOptions(client))
}

export function useTagListQuery(client: ContentClient) {
  return useQuery(createTagListQueryOptions(client))
}
