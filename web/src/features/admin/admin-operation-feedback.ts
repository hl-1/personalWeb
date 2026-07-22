import { useOperationFeedbackStore, type OperationFeedbackTone } from '../../shared/feedback/operation-feedback'

export type AdminOperation =
  | 'article.create' | 'article.update' | 'article.delete' | 'article.publish' | 'article.archive'
  | 'project.create' | 'project.update' | 'project.delete' | 'project.publish' | 'project.archive'
  | 'category.create' | 'category.update' | 'category.delete'
  | 'tag.create' | 'tag.update' | 'tag.delete'
  | 'profile.create' | 'profile.update'
  | 'skill.create' | 'skill.update' | 'skill.delete'
  | 'experience.create' | 'experience.update' | 'experience.delete'

const operationLabels: Record<AdminOperation, string> = {
  'article.create': '文章添加',
  'article.update': '文章保存',
  'article.delete': '文章删除',
  'article.publish': '文章发布',
  'article.archive': '文章归档',
  'project.create': '项目添加',
  'project.update': '项目保存',
  'project.delete': '项目删除',
  'project.publish': '项目发布',
  'project.archive': '项目归档',
  'category.create': '分类添加',
  'category.update': '分类保存',
  'category.delete': '分类删除',
  'tag.create': '标签添加',
  'tag.update': '标签保存',
  'tag.delete': '标签删除',
  'profile.create': '简介添加',
  'profile.update': '简介保存',
  'skill.create': '技能添加',
  'skill.update': '技能保存',
  'skill.delete': '技能删除',
  'experience.create': '经历添加',
  'experience.update': '经历保存',
  'experience.delete': '经历删除',
}

export function adminOperationFeedbackMessage(
  operation: AdminOperation,
  tone: OperationFeedbackTone,
): string {
  return `${operationLabels[operation]}${tone === 'success' ? '成功' : '失败'}`
}

export function useAdminOperationFeedback() {
  const feedback = useOperationFeedbackStore()
  return {
    succeeded(operation: AdminOperation) {
      feedback.success(adminOperationFeedbackMessage(operation, 'success'))
    },
    failed(operation: AdminOperation) {
      feedback.error(adminOperationFeedbackMessage(operation, 'error'))
    },
  }
}
