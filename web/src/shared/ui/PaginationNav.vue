<script setup lang="ts">
const props = defineProps<{ page: number, totalPages: number }>()
const emit = defineEmits<{ change: [page: number] }>()
</script>

<template>
  <nav
    class="pagination-nav"
    aria-label="Pagination"
  >
    <button
      data-testid="previous-page"
      type="button"
      :disabled="props.page <= 0"
      @click="emit('change', props.page - 1)"
    >
      Previous
    </button>
    <span>Page {{ props.page + 1 }} of {{ Math.max(props.totalPages, 1) }}</span>
    <button
      data-testid="next-page"
      type="button"
      :disabled="props.totalPages === 0 || props.page + 1 >= props.totalPages"
      @click="emit('change', props.page + 1)"
    >
      Next
    </button>
  </nav>
</template>

<style scoped>
.pagination-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-top: 24px;
  color: var(--muted);
  font-size: 14px;
}

.pagination-nav button {
  min-width: 92px;
  min-height: 40px;
  padding: 8px 14px;
  border: 1px solid var(--line-strong);
  border-radius: 5px;
  background: var(--surface);
  color: var(--ink);
  cursor: pointer;
}

.pagination-nav button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

@media (max-width: 460px) {
  .pagination-nav {
    align-items: stretch;
    flex-direction: column;
    text-align: center;
  }
}
</style>
