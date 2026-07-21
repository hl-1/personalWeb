import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { AdminApiError, type AdminClient } from '../admin-client'
import AdminProfileView from '../../../views/admin/AdminProfileView.vue'
import AdminSkillsView from '../../../views/admin/AdminSkillsView.vue'
import AdminExperiencesView from '../../../views/admin/AdminExperiencesView.vue'

const id='2d65e30a-f450-4f8e-8ed9-5f36b2f7c322';const instant='2026-07-20T10:00:00Z'
const skill={id,name:'Java',category:'Backend',summary:null,sortOrder:0,visible:true,createdAt:instant,updatedAt:instant,version:0}
const experience={id,organization:'A very long organization name that wraps safely',role:'Engineer',startDate:'2020-01-01',endDate:null,
  summaryMarkdown:'',sortOrder:0,visible:true,createdAt:instant,updatedAt:instant,version:0}
function mountWithQuery(component:object,adminClient:AdminClient){return mount(component,{props:{adminClient},global:{plugins:[[VueQueryPlugin,{queryClient:new QueryClient()}]],stubs:{RouterLink:{template:'<a><slot /></a>'}}}})}

describe('portfolio admin views',()=>{
  it('keeps the profile form saveable after a successful save',async()=>{
    const profile={id:1,displayName:'Owner',headline:'Engineer',bioMarkdown:'Bio',seoDescription:null,
      createdAt:instant,updatedAt:instant,version:0}
    const upsertProfile=vi.fn().mockResolvedValue({...profile,version:1})
    const client={getProfile:vi.fn().mockResolvedValue(profile),upsertProfile,
      previewArticle:vi.fn().mockResolvedValue({html:'<p>Safe</p>'})} as unknown as AdminClient
    const wrapper=mountWithQuery(AdminProfileView,client)
    await vi.waitFor(()=>expect(wrapper.get<HTMLInputElement>('input[name="displayName"]').element.value).toBe('Owner'))

    await wrapper.get('[data-testid="save-profile"]').trigger('submit')
    await vi.waitFor(()=>expect(upsertProfile).toHaveBeenCalledTimes(1))
    await vi.waitFor(()=>expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined())
    await wrapper.get('[data-testid="save-profile"]').trigger('submit')

    await vi.waitFor(()=>expect(upsertProfile).toHaveBeenCalledTimes(2))
  })

  it('creates a missing profile and retains input on a first-create race',async()=>{
    const upsertProfile=vi.fn().mockRejectedValue(new AdminApiError('conflict',409,'stale_version'))
    const client={getProfile:vi.fn().mockRejectedValue(new AdminApiError('response_error',404,'not_found')),
      upsertProfile,previewArticle:vi.fn().mockResolvedValue({html:'<p>Safe</p>'})} as unknown as AdminClient
    const wrapper=mountWithQuery(AdminProfileView,client)
    await vi.waitFor(()=>expect(wrapper.text()).toContain('Create profile'))
    await wrapper.get('input[name="displayName"]').setValue('Unsaved Name')
    await wrapper.get('input[name="headline"]').setValue('Platform engineer')
    await wrapper.get('[data-testid="save-profile"]').trigger('submit')
    await vi.waitFor(()=>expect(wrapper.text()).toContain('Reload server profile'))
    expect(wrapper.get<HTMLInputElement>('input[name="displayName"]').element.value).toBe('Unsaved Name')
    expect(upsertProfile).toHaveBeenCalledWith(expect.objectContaining({version:null}))
  })

  it('lists and creates visible skills with explicit order',async()=>{
    const createSkill=vi.fn().mockResolvedValue({data:skill,location:`/api/v1/admin/portfolio/skills/${id}`})
    const client={listSkills:vi.fn().mockResolvedValue([skill]),createSkill,updateSkill:vi.fn(),deleteSkill:vi.fn()} as unknown as AdminClient
    const wrapper=mountWithQuery(AdminSkillsView,client)
    await vi.waitFor(()=>expect(wrapper.text()).toContain('Java'))
    await wrapper.get('input[name="name"]').setValue('TypeScript')
    await wrapper.get('input[name="category"]').setValue('Frontend')
    await wrapper.get('[data-testid="create-skill"]').trigger('submit')
    await vi.waitFor(()=>expect(createSkill).toHaveBeenCalledWith(expect.objectContaining({sortOrder:0,visible:true})))
  })

  it('lists experiences without overflowing long organization names',async()=>{
    const client={listExperiences:vi.fn().mockResolvedValue([experience]),createExperience:vi.fn(),
      updateExperience:vi.fn(),deleteExperience:vi.fn(),previewArticle:vi.fn()} as unknown as AdminClient
    const wrapper=mountWithQuery(AdminExperiencesView,client)
    await vi.waitFor(()=>expect(wrapper.text()).toContain(experience.organization))
    expect(wrapper.get('[data-testid="experience-list"]').classes()).toContain('experience-list')
  })
})
