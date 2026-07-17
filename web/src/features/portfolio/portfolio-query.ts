import { queryOptions, useQuery } from '@tanstack/vue-query'
import { parseOptionalPublicSlug } from '../../shared/api/slug-schema'
import type { PortfolioClient } from './portfolio-client'
import {
  parseProjectListParams,
  parseProjectSlug,
  type ProjectListParams,
} from './portfolio-schema'

export const portfolioQueryKey = ['portfolio'] as const
export const portfolioProfileQueryKey = [...portfolioQueryKey, 'profile'] as const
export const skillListQueryKey = [...portfolioQueryKey, 'skills'] as const
export const experienceListQueryKey = [...portfolioQueryKey, 'experiences'] as const

export function projectListQueryKey(params: ProjectListParams = {}) {
  const normalized = parseProjectListParams(params)
  return [
    ...portfolioQueryKey,
    'projects',
    {
      page: normalized.page,
      size: normalized.size,
      featured: normalized.featured ?? null,
    },
  ] as const
}

export function projectDetailQueryKey(input: string) {
  return [...portfolioQueryKey, 'project', parseOptionalPublicSlug(input) ?? input] as const
}

export function createPortfolioProfileQueryOptions(client: PortfolioClient) {
  return queryOptions({
    queryKey: portfolioProfileQueryKey,
    queryFn: () => client.getProfile(),
    retry: false,
  })
}

export function createProjectListQueryOptions(
  client: PortfolioClient,
  params: ProjectListParams = {},
) {
  const normalized = parseProjectListParams(params)
  return queryOptions({
    queryKey: projectListQueryKey(normalized),
    queryFn: () => client.getProjects(normalized),
    retry: false,
  })
}

export function createProjectDetailQueryOptions(client: PortfolioClient, input: string) {
  const slug = parseOptionalPublicSlug(input)
  return queryOptions({
    queryKey: projectDetailQueryKey(input),
    queryFn: () => client.getProject(parseProjectSlug(input)),
    enabled: slug !== undefined,
    retry: false,
  })
}

export function createSkillListQueryOptions(client: PortfolioClient) {
  return queryOptions({
    queryKey: skillListQueryKey,
    queryFn: () => client.getSkills(),
    retry: false,
  })
}

export function createExperienceListQueryOptions(client: PortfolioClient) {
  return queryOptions({
    queryKey: experienceListQueryKey,
    queryFn: () => client.getExperiences(),
    retry: false,
  })
}

export function usePortfolioProfileQuery(client: PortfolioClient) {
  return useQuery(createPortfolioProfileQueryOptions(client))
}

export function useProjectListQuery(client: PortfolioClient, params: ProjectListParams = {}) {
  return useQuery(createProjectListQueryOptions(client, params))
}

export function useProjectDetailQuery(client: PortfolioClient, slug: string) {
  return useQuery(createProjectDetailQueryOptions(client, slug))
}

export function useSkillListQuery(client: PortfolioClient) {
  return useQuery(createSkillListQueryOptions(client))
}

export function useExperienceListQuery(client: PortfolioClient) {
  return useQuery(createExperienceListQueryOptions(client))
}
