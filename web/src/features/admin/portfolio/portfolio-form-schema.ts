import { z } from 'zod'
import {
  dateSchema,
  limitedTextSchema,
  nonNegativeIntegerSchema,
  nullableVersionSchema,
  optionalDateSchema,
  optionalTextSchema,
  requiredTextSchema,
} from '../admin-form-rules'

export const profileFormSchema = z.strictObject({ displayName:requiredTextSchema(120),
  headline:requiredTextSchema(180),bioMarkdown:limitedTextSchema(50_000),
  seoDescription:optionalTextSchema(160),version:nullableVersionSchema })
export const skillFormSchema = z.strictObject({ name:requiredTextSchema(120),category:requiredTextSchema(120),
  summary:optionalTextSchema(500),sortOrder:nonNegativeIntegerSchema,visible:z.boolean(),version:nullableVersionSchema })
export const experienceFormSchema=z.strictObject({organization:requiredTextSchema(180),role:requiredTextSchema(180),
  startDate:dateSchema,endDate:optionalDateSchema,summaryMarkdown:limitedTextSchema(20_000),sortOrder:nonNegativeIntegerSchema,
  visible:z.boolean(),version:nullableVersionSchema}).superRefine((value,context)=>{
    if(value.endDate!==null&&value.endDate<value.startDate)context.addIssue({code:'custom',path:['endDate'],message:'End date cannot be before start date'})
  })
export type ProfileForm=z.infer<typeof profileFormSchema>;export type SkillForm=z.infer<typeof skillFormSchema>;export type ExperienceForm=z.infer<typeof experienceFormSchema>
export const parseProfileForm=(input:unknown):ProfileForm=>profileFormSchema.parse(input)
export const parseSkillForm=(input:unknown):SkillForm=>skillFormSchema.parse(input)
export const parseExperienceForm=(input:unknown):ExperienceForm=>experienceFormSchema.parse(input)
