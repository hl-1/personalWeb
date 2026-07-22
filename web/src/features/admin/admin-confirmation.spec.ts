import { ElMessageBox } from 'element-plus'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { confirmAdminAction } from './admin-confirmation'

describe('confirmAdminAction', () => {
  afterEach(() => {
    ElMessageBox.close()
    document.body.innerHTML = ''
  })

  async function clickMessageBoxButton(label: string) {
    await vi.waitFor(() => {
      expect(document.querySelector('.el-message-box')).not.toBeNull()
    })

    const button = Array.from(document.querySelectorAll<HTMLButtonElement>('.el-message-box button'))
      .find(candidate => candidate.textContent?.trim() === label)

    expect(button).toBeDefined()
    button?.click()
  }

  it('returns confirmed when the primary action is selected', async () => {
    const result = confirmAdminAction({
      title: '文章添加成功',
      message: '是否继续添加下一篇文章？',
      confirmButtonText: '继续添加',
      cancelButtonText: '返回列表',
      type: 'success',
    })

    await clickMessageBoxButton('继续添加')
    await expect(result).resolves.toBe('confirmed')
  })

  it('returns cancelled when the secondary action is selected', async () => {
    const result = confirmAdminAction({
      title: 'Confirm',
      message: 'Continue?',
      confirmButtonText: 'Continue',
      cancelButtonText: 'Cancel',
    })

    await clickMessageBoxButton('Cancel')
    await expect(result).resolves.toBe('cancelled')
  })

  it('returns closed when the close action is selected', async () => {
    const result = confirmAdminAction({
      title: 'Confirm',
      message: 'Continue?',
      confirmButtonText: 'Continue',
      cancelButtonText: 'Cancel',
    })

    await vi.waitFor(() => {
      expect(document.querySelector('.el-message-box__headerbtn')).not.toBeNull()
    })
    document.querySelector<HTMLButtonElement>('.el-message-box__headerbtn')?.click()

    await expect(result).resolves.toBe('closed')
  })
})
