<script setup lang="ts">
import { computed, useId, type VNode } from 'vue'

const props = withDefaults(defineProps<{
  name: string
  label: string
  required?: boolean
  hint?: string
  errors?: string[]
}>(), {
  required: false,
  hint: undefined,
  errors: () => [],
})

defineSlots<{
  default(props: {
    controlId: string
    describedBy: string | undefined
    invalid: boolean
  }): VNode | VNode[] | undefined
}>()

const instanceId = useId().replaceAll(':', '')
const controlId = `admin-field-${props.name}-${instanceId}`
const hintId = `${controlId}-hint`
const errorId = `${controlId}-error`
const invalid = computed(() => props.errors.length > 0)
const describedBy = computed(() => [
  ...(props.hint ? [hintId] : []),
  ...(invalid.value ? [errorId] : []),
].join(' ') || undefined)
</script>

<template>
  <el-form-item
    class="admin-form-field"
    :data-field="name"
    :validate-status="invalid ? 'error' : ''"
  >
    <template #label>
      <label :for="controlId">
        <span>{{ label }}</span>
        <span
          v-if="required"
          data-testid="field-required"
          class="required"
          aria-hidden="true"
        >*</span>
      </label>
    </template>
    <slot
      :control-id="controlId"
      :described-by="describedBy"
      :invalid="invalid"
    />
    <p
      v-if="hint"
      :id="hintId"
      data-testid="field-hint"
      class="hint"
    >
      {{ hint }}
    </p>
    <p
      v-if="invalid"
      :id="errorId"
      data-testid="field-error"
      class="error"
      role="alert"
    >
      {{ errors[0] }}
    </p>
  </el-form-item>
</template>

<style scoped>
.admin-form-field { min-width: 0; margin-bottom: 0; align-content: start; }
.admin-form-field :deep(.el-form-item__label) { height: auto; padding: 0 0 5px; line-height: 1.35; }
.admin-form-field :deep(.el-form-item__content) { display: grid; min-width: 0; gap: 5px; line-height: 1.35; }
label { display: flex; gap: 4px; color: #59645d; font-size: 12px; font-weight: 700; }
.required { color: #9b3029; }
.hint, .error { margin: 0; font-size: 12px; font-weight: 500; line-height: 1.35; }
.hint { color: #68736c; }
.error { color: #9b3029; }
</style>
