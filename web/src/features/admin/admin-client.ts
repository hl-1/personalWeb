import {
  SessionApiError,
  createSessionClient,
  type SessionClient,
} from '../../shared/api/session-client'
import {
  parseAdminArticle,
  parseAdminArticleListParams,
  parseAdminArticlePage,
  parseAdminExperience,
  parseAdminExperienceList,
  parseAdminId,
  parseAdminPreview,
  parseAdminProfile,
  parseAdminProject,
  parseAdminProjectPage,
  parseAdminSkill,
  parseAdminSkillList,
  parseAdminTaxonomyList,
  parseAdminVersion,
  type AdminArticle,
  type AdminArticleListParams,
  type AdminArticlePage,
  type AdminExperience,
  type AdminPreview,
  type AdminProfile,
  type AdminProject,
  type AdminProjectPage,
  type AdminSkill,
  type AdminStatus,
  type AdminTaxonomy,
} from './admin-schema'

export interface AdminArticleInput {
  slug: string
  title: string
  summary: string
  bodyMarkdown: string
  categoryId: string | null
  tagIds: string[]
  seoTitle: string | null
  seoDescription: string | null
}

export interface AdminArticleUpdateInput extends AdminArticleInput { version: number }
export interface AdminTaxonomyInput { name: string; slug: string }
export interface AdminTaxonomyUpdateInput extends AdminTaxonomyInput { version: number }
export interface AdminProjectInput {
  slug: string; title: string; summary: string; descriptionMarkdown: string
  projectUrl: string | null; repositoryUrl: string | null; featured: boolean; sortOrder: number
}
export interface AdminProjectUpdateInput extends AdminProjectInput { version: number }
export interface AdminProfileInput {
  displayName: string; headline: string; bioMarkdown: string
  seoDescription: string | null; version: number | null
}
export interface AdminSkillInput {
  name: string; category: string; summary: string | null; sortOrder: number; visible: boolean
}
export interface AdminSkillUpdateInput extends AdminSkillInput { version: number }
export interface AdminExperienceInput {
  organization: string; role: string; startDate: string; endDate: string | null
  summaryMarkdown: string; sortOrder: number; visible: boolean
}
export interface AdminExperienceUpdateInput extends AdminExperienceInput { version: number }

export interface CreatedAdminResource<Result> { data: Result; location: string }

export interface AdminClient {
  listArticles(params?: AdminArticleListParams): Promise<AdminArticlePage>
  getArticle(id: string): Promise<AdminArticle>
  createArticle(input: AdminArticleInput): Promise<CreatedAdminResource<AdminArticle>>
  updateArticle(id: string, input: AdminArticleUpdateInput): Promise<AdminArticle>
  deleteArticle(id: string, version: number): Promise<void>
  publishArticle(id: string, version: number, publishAt: string | null): Promise<AdminArticle>
  archiveArticle(id: string, version: number): Promise<AdminArticle>
  previewArticle(markdown: string): Promise<AdminPreview>
  listCategories(): Promise<AdminTaxonomy[]>
  listTags(): Promise<AdminTaxonomy[]>
  createCategory(input: AdminTaxonomyInput): Promise<CreatedAdminResource<AdminTaxonomy>>
  createTag(input: AdminTaxonomyInput): Promise<CreatedAdminResource<AdminTaxonomy>>
  updateCategory(id: string, input: AdminTaxonomyUpdateInput): Promise<AdminTaxonomy>
  updateTag(id: string, input: AdminTaxonomyUpdateInput): Promise<AdminTaxonomy>
  deleteCategory(id: string, version: number): Promise<void>
  deleteTag(id: string, version: number): Promise<void>
  listProjects(params?: AdminArticleListParams): Promise<AdminProjectPage>
  getProject(id: string): Promise<AdminProject>
  createProject(input: AdminProjectInput): Promise<CreatedAdminResource<AdminProject>>
  updateProject(id: string, input: AdminProjectUpdateInput): Promise<AdminProject>
  deleteProject(id: string, version: number): Promise<void>
  publishProject(id: string, version: number, publishAt: string | null): Promise<AdminProject>
  archiveProject(id: string, version: number): Promise<AdminProject>
  previewProject(markdown: string): Promise<AdminPreview>
  getProfile(): Promise<AdminProfile>
  upsertProfile(input: AdminProfileInput): Promise<AdminProfile>
  listSkills(): Promise<AdminSkill[]>
  createSkill(input: AdminSkillInput): Promise<CreatedAdminResource<AdminSkill>>
  updateSkill(id: string, input: AdminSkillUpdateInput): Promise<AdminSkill>
  deleteSkill(id: string, version: number): Promise<void>
  listExperiences(): Promise<AdminExperience[]>
  createExperience(input: AdminExperienceInput): Promise<CreatedAdminResource<AdminExperience>>
  updateExperience(id: string, input: AdminExperienceUpdateInput): Promise<AdminExperience>
  deleteExperience(id: string, version: number): Promise<void>
}

export interface AdminClientOptions {
  apiBaseUrl: string
  fetch?: typeof globalThis.fetch
}

function requireLocation(location: string | null, prefix: string): string {
  if (location === null || !location.startsWith(prefix)) {
    throw new SessionApiError('invalid_response')
  }
  return location
}

function queryFor(params: AdminArticleListParams = {}) {
  const normalized = parseAdminArticleListParams(params)
  const query = new URLSearchParams({ page: String(normalized.page), size: String(normalized.size) })
  if (normalized.status !== undefined) query.set('status', normalized.status)
  if (normalized.query !== undefined) query.set('query', normalized.query)
  return query
}

function createClient(session: SessionClient): AdminClient {
  const get = async <Result>(path: string, parse: (input: unknown) => Result) =>
    (await session.request(path, { method: 'GET', parse })).data
  const write = async <Result>(
    path: string,
    method: 'POST' | 'PUT',
    body: unknown,
    parse: (input: unknown) => Result,
    expectedStatus?: number,
  ) => session.request(path, { method, body, parse, expectedStatus })
  const remove = async (path: string) => {
    await session.request(path, { method: 'DELETE', expectedStatus: 204 })
  }
  const created = async <Result>(
    path: string,
    body: unknown,
    parse: (input: unknown) => Result,
    prefix: string,
  ): Promise<CreatedAdminResource<Result>> => {
    const result = await write(path, 'POST', body, parse, 201)
    return { data: result.data, location: requireLocation(result.location, prefix) }
  }
  const resourcePath = (base: string, input: string) => `${base}/${encodeURIComponent(parseAdminId(input))}`
  const versionQuery = (version: number) => `version=${encodeURIComponent(String(parseAdminVersion(version)))}`

  return {
    listArticles: (params = {}) => get(`/v1/admin/articles?${queryFor(params)}`, parseAdminArticlePage),
    getArticle: (id) => get(resourcePath('/v1/admin/articles', id), parseAdminArticle),
    createArticle: (input) => created('/v1/admin/articles', input, parseAdminArticle, '/api/v1/admin/articles/'),
    updateArticle: async (id, input) => (await write(resourcePath('/v1/admin/articles', id), 'PUT', input, parseAdminArticle)).data,
    deleteArticle: (id, version) => remove(`${resourcePath('/v1/admin/articles', id)}?${versionQuery(version)}`),
    publishArticle: async (id, version, publishAt) => (await write(
      `${resourcePath('/v1/admin/articles', id)}/publish`, 'POST', { version, publishAt }, parseAdminArticle,
    )).data,
    archiveArticle: async (id, version) => (await write(
      `${resourcePath('/v1/admin/articles', id)}/archive`, 'POST', { version }, parseAdminArticle,
    )).data,
    previewArticle: async (markdown) => (await write(
      '/v1/admin/articles/preview', 'POST', { markdown }, parseAdminPreview,
    )).data,
    listCategories: () => get('/v1/admin/categories', parseAdminTaxonomyList),
    listTags: () => get('/v1/admin/tags', parseAdminTaxonomyList),
    createCategory: (input) => created('/v1/admin/categories', input, (value) => parseAdminTaxonomyList([value])[0], '/api/v1/admin/categories/'),
    createTag: (input) => created('/v1/admin/tags', input, (value) => parseAdminTaxonomyList([value])[0], '/api/v1/admin/tags/'),
    updateCategory: async (id, input) => (await write(resourcePath('/v1/admin/categories', id), 'PUT', input, (value) => parseAdminTaxonomyList([value])[0])).data,
    updateTag: async (id, input) => (await write(resourcePath('/v1/admin/tags', id), 'PUT', input, (value) => parseAdminTaxonomyList([value])[0])).data,
    deleteCategory: (id, version) => remove(`${resourcePath('/v1/admin/categories', id)}?${versionQuery(version)}`),
    deleteTag: (id, version) => remove(`${resourcePath('/v1/admin/tags', id)}?${versionQuery(version)}`),
    listProjects: (params = {}) => get(`/v1/admin/portfolio/projects?${queryFor(params)}`, parseAdminProjectPage),
    getProject: (id) => get(resourcePath('/v1/admin/portfolio/projects', id), parseAdminProject),
    createProject: (input) => created('/v1/admin/portfolio/projects', input, parseAdminProject, '/api/v1/admin/portfolio/projects/'),
    updateProject: async (id, input) => (await write(resourcePath('/v1/admin/portfolio/projects', id), 'PUT', input, parseAdminProject)).data,
    deleteProject: (id, version) => remove(`${resourcePath('/v1/admin/portfolio/projects', id)}?${versionQuery(version)}`),
    publishProject: async (id, version, publishAt) => (await write(`${resourcePath('/v1/admin/portfolio/projects', id)}/publish`, 'POST', { version, publishAt }, parseAdminProject)).data,
    archiveProject: async (id, version) => (await write(`${resourcePath('/v1/admin/portfolio/projects', id)}/archive`, 'POST', { version }, parseAdminProject)).data,
    previewProject: async (markdown) => (await write('/v1/admin/portfolio/projects/preview', 'POST', { markdown }, parseAdminPreview)).data,
    getProfile: () => get('/v1/admin/portfolio/profile', parseAdminProfile),
    upsertProfile: async (input) => (await write('/v1/admin/portfolio/profile', 'PUT', input, parseAdminProfile)).data,
    listSkills: () => get('/v1/admin/portfolio/skills', parseAdminSkillList),
    createSkill: (input) => created('/v1/admin/portfolio/skills', input, parseAdminSkill, '/api/v1/admin/portfolio/skills/'),
    updateSkill: async (id, input) => (await write(resourcePath('/v1/admin/portfolio/skills', id), 'PUT', input, parseAdminSkill)).data,
    deleteSkill: (id, version) => remove(`${resourcePath('/v1/admin/portfolio/skills', id)}?${versionQuery(version)}`),
    listExperiences: () => get('/v1/admin/portfolio/experiences', parseAdminExperienceList),
    createExperience: (input) => created('/v1/admin/portfolio/experiences', input, parseAdminExperience, '/api/v1/admin/portfolio/experiences/'),
    updateExperience: async (id, input) => (await write(resourcePath('/v1/admin/portfolio/experiences', id), 'PUT', input, parseAdminExperience)).data,
    deleteExperience: (id, version) => remove(`${resourcePath('/v1/admin/portfolio/experiences', id)}?${versionQuery(version)}`),
  }
}

export function createAdminClient(options: AdminClientOptions): AdminClient {
  return createClient(createSessionClient(options))
}

export { SessionApiError as AdminApiError }
export type { AdminStatus }
