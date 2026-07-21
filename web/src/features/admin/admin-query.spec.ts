import { QueryClient } from '@tanstack/vue-query'
import { describe, expect, it, vi } from 'vitest'
import {
  adminQueryKey,
  articleDetailQueryKey,
  articleListQueryKey,
  createArticleMutationOptions,
  createArticleQueryOptions,
} from './admin-query'

describe('admin query contracts', () => {
  it('creates stable normalized list and detail keys', () => {
    expect(articleListQueryKey({ status: 'DRAFT', query: ' value ' })).toEqual([
      ...adminQueryKey, 'articles', 'list',
      { page: 0, size: 20, status: 'DRAFT', query: 'value' },
    ])
    expect(articleDetailQueryKey('2d65e30a-f450-4f8e-8ed9-5f36b2f7c322'))
      .toEqual([...adminQueryKey, 'articles', '2d65e30a-f450-4f8e-8ed9-5f36b2f7c322'])
  })

  it('disables retries and precisely invalidates article queries after mutation', async () => {
    const client = {
      getArticle: vi.fn(),
      updateArticle: vi.fn().mockResolvedValue({ id: 'id' }),
    }
    const queryClient = new QueryClient()
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
    expect(createArticleQueryOptions(client as never, 'id').retry).toBe(false)

    const options = createArticleMutationOptions(client as never, queryClient, 'id')
    expect(options.retry).toBe(0)
    await options.mutationFn?.({ id: 'id', input: {} } as never, {} as never)
    await options.onSuccess?.({ id: 'id' } as never, { id: 'id', input: {} } as never, undefined, undefined as never)

    expect(invalidate).toHaveBeenCalledWith({ queryKey: articleDetailQueryKey('id'), exact: true })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: [...adminQueryKey, 'articles', 'list'] })
  })
})
