import { describe, expect, it } from 'vitest'
import { ElButton, ElForm, ElMessage, ElMessageBox, ElTable } from 'element-plus'

describe('Element Plus admin foundation', () => {
  it('provides the components and services used by the admin workspace', () => {
    expect(ElButton.name).toBe('ElButton')
    expect(ElForm.name).toBe('ElForm')
    expect(ElTable.name).toBe('ElTable')
    expect(ElMessage).toBeTypeOf('function')
    expect(ElMessageBox.confirm).toBeTypeOf('function')
  })
})
