<script setup lang="ts">
const props = defineProps<{
  kind: 'loading' | 'empty' | 'error' | 'not-found'
  title: string
  message: string
  headingLevel?: 'h1' | 'h2'
}>()
</script>

<template>
  <section
    class="status-view"
    :class="`status-view--${props.kind}`"
    :data-testid="`${props.kind}-state`"
    :role="props.kind === 'error' ? 'alert' : undefined"
  >
    <component
      :is="props.headingLevel ?? 'h2'"
      class="status-view__title"
    >
      {{ props.title }}
    </component>
    <p>{{ props.message }}</p>
  </section>
</template>

<style scoped>
.status-view {
  padding: 28px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.status-view__title {
  margin: 0 0 6px;
  font-size: 18px;
}

.status-view p {
  margin: 0;
  color: var(--muted);
  line-height: 1.6;
}

.status-view--error {
  border-color: #d9aaa6;
}

.status-view--error .status-view__title {
  color: #8b2d28;
}
</style>
