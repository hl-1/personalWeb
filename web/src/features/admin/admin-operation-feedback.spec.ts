import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import {
  adminOperationFeedbackMessage,
  useAdminOperationFeedback,
  type AdminOperation,
} from './admin-operation-feedback'
import { useOperationFeedbackStore } from '../../shared/feedback/operation-feedback'

const operations: Array<[AdminOperation, string]> = [
  ['article.create', '文章添加'],
  ['article.update', '文章保存'],
  ['article.delete', '文章删除'],
  ['article.publish', '文章发布'],
  ['article.archive', '文章归档'],
  ['project.create', '项目添加'],
  ['project.update', '项目保存'],
  ['project.delete', '项目删除'],
  ['project.publish', '项目发布'],
  ['project.archive', '项目归档'],
  ['category.create', '分类添加'],
  ['category.update', '分类保存'],
  ['category.delete', '分类删除'],
  ['tag.create', '标签添加'],
  ['tag.update', '标签保存'],
  ['tag.delete', '标签删除'],
  ['profile.create', '简介添加'],
  ['profile.update', '简介保存'],
  ['skill.create', '技能添加'],
  ['skill.update', '技能保存'],
  ['skill.delete', '技能删除'],
  ['experience.create', '经历添加'],
  ['experience.update', '经历保存'],
  ['experience.delete', '经历删除'],
]

describe('admin operation feedback', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it.each(operations)('maps %s to explicit success and failure copy', (operation, label) => {
    expect(adminOperationFeedbackMessage(operation, 'success')).toBe(`${label}成功`)
    expect(adminOperationFeedbackMessage(operation, 'error')).toBe(`${label}失败`)
  })

  it('publishes operation results to the global feedback store', () => {
    const feedback = useAdminOperationFeedback()
    const store = useOperationFeedbackStore()

    feedback.succeeded('article.publish')
    feedback.failed('article.delete')

    expect(store.messages.map(({ message }) => message))
      .toEqual(['文章发布成功', '文章删除失败'])
  })
})
