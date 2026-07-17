<script setup lang="ts">
import { computed } from 'vue'
import { parseSafeHtml, type SafeHtml } from './safe-html'

const props = defineProps<{ html: SafeHtml }>()
const verifiedHtml = computed(() => parseSafeHtml(props.html))
</script>

<template>
  <!-- eslint-disable vue/no-v-html -->
  <div
    class="safe-markdown"
    v-html="verifiedHtml"
  />
  <!-- eslint-enable vue/no-v-html -->
</template>

<style scoped>
.safe-markdown {
  min-width: 0;
  color: var(--ink);
  line-height: 1.75;
  overflow-wrap: anywhere;
}

.safe-markdown :deep(h1),
.safe-markdown :deep(h2),
.safe-markdown :deep(h3) {
  margin: 1.8em 0 0.65em;
  line-height: 1.25;
}

.safe-markdown :deep(p),
.safe-markdown :deep(ul),
.safe-markdown :deep(ol),
.safe-markdown :deep(blockquote) {
  margin: 0 0 1.1em;
}

.safe-markdown :deep(pre) {
  max-width: 100%;
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: #171b19;
  color: #f3f6f4;
  overflow-x: auto;
}

.safe-markdown :deep(code) {
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  overflow-wrap: normal;
}

.safe-markdown :deep(table) {
  display: block;
  width: 100%;
  border-collapse: collapse;
  overflow-x: auto;
}

.safe-markdown :deep(th),
.safe-markdown :deep(td) {
  padding: 9px 12px;
  border: 1px solid var(--line);
  text-align: left;
}

.safe-markdown :deep(blockquote) {
  padding-left: 18px;
  border-left: 3px solid var(--accent);
  color: var(--muted);
}
</style>
