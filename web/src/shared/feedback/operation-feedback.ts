import { ElMessage } from 'element-plus'
import { defineStore } from 'pinia'
import { ref } from 'vue'

export type OperationFeedbackTone = 'success' | 'error'

export interface OperationFeedbackMessage {
  id: number
  message: string
  tone: OperationFeedbackTone
}

const DISPLAY_DURATION_MS = 4_500

export const useOperationFeedbackStore = defineStore('operation-feedback', () => {
  const messages = ref<OperationFeedbackMessage[]>([])
  let nextId = 1

  function dismiss(id: number) {
    messages.value = messages.value.filter((message) => message.id !== id)
  }

  function show(message: string, tone: OperationFeedbackTone) {
    const id = nextId++
    messages.value.push({ id, message, tone })
    ElMessage({
      message,
      type: tone,
      duration: DISPLAY_DURATION_MS,
      showClose: true,
    })
    globalThis.setTimeout(() => dismiss(id), DISPLAY_DURATION_MS)
  }

  return {
    messages,
    success: (message: string) => show(message, 'success'),
    error: (message: string) => show(message, 'error'),
    dismiss,
  }
})
