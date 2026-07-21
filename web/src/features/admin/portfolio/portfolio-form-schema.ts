import { z } from 'zod'

const version = z.number().int().min(0).nullable()
const profile = z.strictObject({ displayName:z.string().trim().min(1).max(120),
  headline:z.string().trim().min(1).max(180),bioMarkdown:z.string().max(50_000),
  seoDescription:z.string().max(160).nullable(),version })
const skill = z.strictObject({ name:z.string().trim().min(1).max(120),category:z.string().trim().min(1).max(120),
  summary:z.string().max(500).nullable(),sortOrder:z.number().int().min(0),visible:z.boolean(),version })
const date=z.string().regex(/^\d{4}-\d{2}-\d{2}$/)
const experience=z.strictObject({organization:z.string().trim().min(1).max(180),role:z.string().trim().min(1).max(180),
  startDate:date,endDate:date.nullable(),summaryMarkdown:z.string().max(20_000),sortOrder:z.number().int().min(0),
  visible:z.boolean(),version}).superRefine((value,context)=>{
    if(value.endDate!==null&&value.endDate<value.startDate)context.addIssue({code:'custom',path:['endDate'],message:'must not precede startDate'})
  })
export type ProfileForm=z.infer<typeof profile>;export type SkillForm=z.infer<typeof skill>;export type ExperienceForm=z.infer<typeof experience>
export const parseProfileForm=(input:unknown):ProfileForm=>profile.parse(input)
export const parseSkillForm=(input:unknown):SkillForm=>skill.parse(input)
export const parseExperienceForm=(input:unknown):ExperienceForm=>experience.parse(input)
