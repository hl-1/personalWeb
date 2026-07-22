import { nextTick, reactive, ref } from 'vue'

export type AdminFieldErrors = Record<string, string[]>

interface ValidationIssue {
  readonly path: readonly PropertyKey[]
  readonly message: string
}

interface FormSchema<Result> {
  safeParse(input: unknown):
    | { success: true; data: Result }
    | { success: false; error: { issues: readonly ValidationIssue[] } }
}

export type SubmitValidation<Result> =
  | { success: true; data: Result }
  | { success: false; firstInvalidField: string | undefined }

export async function focusAdminField(field: string | undefined) {
  if (!field) return
  await nextTick()
  document.querySelector<HTMLElement>(`[name="${field}"]`)?.focus()
}

function issuesToErrors(issues: readonly ValidationIssue[]): AdminFieldErrors {
  const errors: AdminFieldErrors = {}
  for (const issue of issues) {
    const field = typeof issue.path[0] === 'string' ? issue.path[0] : '_form'
    const messages = errors[field] ?? []
    if (!messages.includes(issue.message)) messages.push(issue.message)
    errors[field] = messages
  }
  return errors
}

function withoutField(errors: AdminFieldErrors, field: string): AdminFieldErrors {
  return Object.fromEntries(Object.entries(errors).filter(([key]) => key !== field))
}

export function createAdminFormValidation<Result>(schema: FormSchema<Result>) {
  const touched = reactive(new Set<string>())
  const submitted = ref(false)
  const clientErrors = ref<AdminFieldErrors>({})
  const serverErrors = ref<AdminFieldErrors>({})

  function validate(input: unknown) {
    const result = schema.safeParse(input)
    clientErrors.value = result.success ? {} : issuesToErrors(result.error.issues)
    return result
  }

  function errorsFor(field: string): string[] {
    const client = submitted.value || touched.has(field) ? clientErrors.value[field] ?? [] : []
    return [...client, ...(serverErrors.value[field] ?? [])]
  }

  function formErrors(): string[] {
    const client = submitted.value ? clientErrors.value._form ?? [] : []
    return [...client, ...(serverErrors.value._form ?? [])]
  }

  function touch(field: string, input: unknown) {
    touched.add(field)
    validate(input)
  }

  function change(field: string, input: unknown) {
    serverErrors.value = withoutField(serverErrors.value, field)
    if (submitted.value || touched.has(field)) validate(input)
  }

  function validateForSubmit(input: unknown, fieldOrder: readonly string[]): SubmitValidation<Result> {
    submitted.value = true
    const result = validate(input)
    if (result.success) return { success: true, data: result.data }
    return {
      success: false,
      firstInvalidField: fieldOrder.find((field) => (clientErrors.value[field]?.length ?? 0) > 0),
    }
  }

  function applyServerErrors(errors: AdminFieldErrors, aliases: Record<string, string> = {}) {
    const mapped: AdminFieldErrors = {}
    for (const [field, messages] of Object.entries(errors)) {
      const target = aliases[field] ?? field
      mapped[target] = [...(mapped[target] ?? []), ...messages]
      if (target !== '_form') touched.add(target)
    }
    serverErrors.value = mapped
    return Object.keys(mapped).find((field) => field !== '_form')
  }

  function resetValidation() {
    touched.clear()
    submitted.value = false
    clientErrors.value = {}
    serverErrors.value = {}
  }

  return {
    errorsFor,
    formErrors,
    touch,
    change,
    validateForSubmit,
    applyServerErrors,
    resetValidation,
  }
}
