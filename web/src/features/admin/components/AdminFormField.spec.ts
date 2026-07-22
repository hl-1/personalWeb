import { h } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AdminFormField from './AdminFormField.vue'

describe('AdminFormField', () => {
  it('connects its label, persistent hint and error to the slotted control', () => {
    const wrapper = mount(AdminFormField, {
      props: {
        name: 'slug',
        label: 'Slug',
        required: true,
        hint: '3-120 lowercase letters, numbers, and hyphens',
        errors: ['Use at least 3 characters'],
      },
      slots: {
        default: ({ controlId, describedBy, invalid }: {
          controlId: string
          describedBy: string | undefined
          invalid: boolean
        }) => h('input', {
          id: controlId,
          'aria-describedby': describedBy,
          'aria-invalid': String(invalid),
        }),
      },
    })

    const input = wrapper.get('input')
    expect(wrapper.find('.el-form-item').exists()).toBe(true)
    expect(wrapper.get('label').attributes('for')).toBe(input.attributes('id'))
    expect(wrapper.get('[data-testid="field-required"]').text()).toBe('*')
    expect(wrapper.get('[data-testid="field-hint"]').text())
      .toBe('3-120 lowercase letters, numbers, and hyphens')
    expect(wrapper.get('[data-testid="field-error"]').text()).toBe('Use at least 3 characters')
    expect(input.attributes('aria-describedby')).toContain(wrapper.get('[data-testid="field-hint"]').attributes('id'))
    expect(input.attributes('aria-describedby')).toContain(wrapper.get('[data-testid="field-error"]').attributes('id'))
    expect(input.attributes('aria-invalid')).toBe('true')
  })

})
