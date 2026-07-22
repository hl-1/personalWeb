import { createPinia, setActivePinia } from 'pinia'
import { ElMessage } from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useOperationFeedbackStore } from './operation-feedback'

describe('operation feedback', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    ElMessage.closeAll()
    vi.useRealTimers()
  })

  it('renders successful and failed backend operation results with Element Plus', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const feedback = useOperationFeedbackStore()

    feedback.success('文章发布成功')
    feedback.error('文章删除失败')

    expect(document.body.querySelector('.el-message--success')?.textContent)
      .toContain('文章发布成功')
    expect(document.body.querySelector('.el-message--error')?.textContent)
      .toContain('文章删除失败')

    await vi.advanceTimersByTimeAsync(5_000)

    expect(document.body.querySelector('.el-message--success')).toBeNull()
    expect(document.body.querySelector('.el-message--error')).toBeNull()
  })
})
