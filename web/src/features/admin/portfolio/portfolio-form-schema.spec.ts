import { describe, expect, it } from 'vitest'
import { parseExperienceForm, parseProfileForm, parseSkillForm } from './portfolio-form-schema'

describe('portfolio form schemas', () => {
  it('parses valid profile, skill and experience values', () => {
    expect(parseProfileForm({ displayName:'Name',headline:'Headline',bioMarkdown:'',seoDescription:null,version:null }).version).toBeNull()
    expect(parseSkillForm({ name:'Java',category:'Backend',summary:null,sortOrder:0,visible:true,version:0 }).visible).toBe(true)
    expect(parseExperienceForm({ organization:'Org',role:'Role',startDate:'2020-01-01',endDate:null,
      summaryMarkdown:'',sortOrder:0,visible:true,version:0 }).organization).toBe('Org')
  })
  it.each([
    () => parseProfileForm({ displayName:'',headline:'H',bioMarkdown:'',seoDescription:null,version:null }),
    () => parseSkillForm({ name:'Java',category:'Backend',summary:null,sortOrder:-1,visible:true,version:0 }),
    () => parseExperienceForm({ organization:'Org',role:'Role',startDate:'2025-01-01',endDate:'2024-01-01',summaryMarkdown:'',sortOrder:0,visible:true,version:0 }),
    () => parseExperienceForm({ organization:'Org',role:'Role',startDate:'bad',endDate:null,summaryMarkdown:'',sortOrder:0,visible:true,version:0,unknown:true }),
  ])('rejects invalid and unknown values', (parse) => expect(parse).toThrow())
})
