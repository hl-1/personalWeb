<script setup lang="ts">
withDefaults(defineProps<{
  state: 'loading' | 'empty' | 'error' | 'unauthorized' | 'forbidden' | 'ready'
  emptyLabel?: string
}>(), { emptyLabel: 'No records' })

defineEmits<{ retry: [] }>()
</script>

<template>
  <div
    v-if="state === 'loading'"
    class="admin-state"
    role="status"
  >
    Loading
  </div>
  <div
    v-else-if="state === 'empty'"
    class="admin-state"
  >
    {{ emptyLabel }}
  </div>
  <div
    v-else-if="state === 'error'"
    class="admin-state admin-state-error"
  >
    <span>Unable to load this page</span>
    <button
      type="button"
      @click="$emit('retry')"
    >
      Try again
    </button>
  </div>
  <div
    v-else-if="state === 'unauthorized'"
    class="admin-state admin-state-error"
  >
    <RouterLink to="/login">
      Sign in
    </RouterLink>
  </div>
  <div
    v-else-if="state === 'forbidden'"
    class="admin-state admin-state-error"
  >
    <RouterLink to="/forbidden">
      Access denied
    </RouterLink>
  </div>
  <slot v-else />
</template>

<style scoped>
.admin-state {
  display: flex;
  min-height: 160px;
  align-items: center;
  justify-content: center;
  gap: 12px;
  border-top: 1px solid #d8dfda;
  border-bottom: 1px solid #d8dfda;
  color: #59645d;
}

.admin-state-error {
  color: #842f29;
}

button {
  min-height: 36px;
  padding: 7px 12px;
  border: 1px solid #9da9a1;
  border-radius: 4px;
  background: #ffffff;
  cursor: pointer;
}
</style>
