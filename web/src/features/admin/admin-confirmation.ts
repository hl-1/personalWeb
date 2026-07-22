import { ElMessageBox } from 'element-plus'

export type AdminConfirmationResult = 'confirmed' | 'cancelled' | 'closed'

export interface AdminConfirmationOptions {
  title: string
  message: string
  confirmButtonText: string
  cancelButtonText: string
  type?: 'success' | 'warning' | 'info' | 'error'
}

export async function confirmAdminAction(
  options: AdminConfirmationOptions,
): Promise<AdminConfirmationResult> {
  try {
    await ElMessageBox.confirm(options.message, options.title, {
      confirmButtonText: options.confirmButtonText,
      cancelButtonText: options.cancelButtonText,
      type: options.type ?? 'warning',
      distinguishCancelAndClose: true,
      autofocus: true,
    })
    return 'confirmed'
  } catch (action) {
    return action === 'cancel' ? 'cancelled' : 'closed'
  }
}
