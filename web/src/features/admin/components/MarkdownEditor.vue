<script setup lang="ts">
import { ref } from 'vue'
import SafeMarkdownView from '../../../shared/content/SafeMarkdownView.vue'
import { parseSafeHtml, type SafeHtml } from '../../../shared/content/safe-html'
import type { AdminPreview } from '../admin-schema'

const props = defineProps<{
  modelValue: string
  preview: (markdown: string) => Promise<AdminPreview>
}>()

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
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
</script>

<template>
  <div class="markdown-editor">
    <div
      class="editor-tabs"
      role="tablist"
      aria-label="Markdown mode"
    >
      <button
        data-testid="edit-tab"
        type="button"
        :aria-selected="mode === 'edit'"
        @click="mode = 'edit'"
      >
        Edit
      </button>
      <button
        data-testid="preview-tab"
        type="button"
        :aria-selected="mode === 'preview'"
        :disabled="previewing"
        @click="showPreview"
      >
        Preview
      </button>
    </div>
    <textarea
      v-if="mode === 'edit'"
      name="bodyMarkdown"
      :value="modelValue"
      rows="18"
      @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
    />
    <div
      v-else
      class="preview-surface"
    >
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
  </div>
</template>

<style scoped>
.markdown-editor { min-width: 0; }
.editor-tabs { display: flex; gap: 2px; margin-bottom: 8px; }
.editor-tabs button { min-height: 36px; padding: 7px 14px; border: 1px solid #aeb8b1; background: #ffffff; }
.editor-tabs button[aria-selected='true'] { background: #202622; color: #ffffff; }
textarea { width: 100%; min-height: 320px; resize: vertical; padding: 12px; border: 1px solid #aeb8b1; font: 14px/1.55 ui-monospace, monospace; }
.preview-surface { min-height: 320px; padding: 16px 0; border-top: 1px solid #d8dfda; border-bottom: 1px solid #d8dfda; overflow-wrap: anywhere; }
</style>
