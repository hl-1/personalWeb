<script setup lang="ts">
import { ref } from 'vue'
import SafeMarkdownView from '../../../shared/content/SafeMarkdownView.vue'
import { parseSafeHtml, type SafeHtml } from '../../../shared/content/safe-html'
import type { AdminPreview } from '../admin-schema'

const props = withDefaults(defineProps<{
  modelValue: string
  preview: (markdown: string) => Promise<AdminPreview>
  name?: string
  controlId?: string
  describedBy?: string
  invalid?: boolean
}>(), {
  name: 'bodyMarkdown',
  controlId: undefined,
  describedBy: undefined,
  invalid: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  blur: []
}>()
const mode = ref<'edit' | 'preview'>('edit')
const previewHtml = ref<SafeHtml | undefined>()
const previewError = ref(false)
const previewing = ref(false)

async function showPreview() {
  mode.value = 'preview'
  previewError.value = false
  previewing.value = true
  try {
    const result = await props.preview(props.modelValue)
    previewHtml.value = parseSafeHtml(result.html)
  } catch {
    previewHtml.value = undefined
    previewError.value = true
  } finally {
    previewing.value = false
  }
}

async function changeMode(name: string | number) {
  if (name === 'preview') await showPreview()
}
</script>

<template>
  <div class="markdown-editor">
    <el-tabs
      v-model="mode"
      aria-label="Markdown mode"
      @tab-change="changeMode"
    >
      <el-tab-pane name="edit">
        <template #label>
          <span data-testid="edit-tab">Edit</span>
        </template>
        <el-input
          :id="controlId"
          :name="name"
          type="textarea"
          :rows="18"
          resize="vertical"
          :model-value="modelValue"
          :aria-describedby="describedBy"
          :aria-invalid="invalid"
          @update:model-value="emit('update:modelValue', $event)"
          @blur="emit('blur')"
        />
      </el-tab-pane>
      <el-tab-pane name="preview">
        <template #label>
          <span data-testid="preview-tab">Preview</span>
        </template>
        <div class="preview-surface">
          <p v-if="previewing">
            Loading preview
          </p>
          <p v-else-if="previewError">
            Preview unavailable
          </p>
          <SafeMarkdownView
            v-else-if="previewHtml"
            :html="previewHtml"
          />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.markdown-editor { min-width: 0; }
.markdown-editor :deep(.el-tabs__header) { margin-bottom: 8px; }
.markdown-editor :deep(.el-textarea__inner) { min-height: 320px !important; font: 14px/1.55 ui-monospace, monospace; }
.preview-surface { min-height: 320px; padding: 16px 0; border-top: 1px solid #d8dfda; border-bottom: 1px solid #d8dfda; overflow-wrap: anywhere; }
</style>
