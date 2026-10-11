import { ref, shallowRef } from 'vue'
import { type LocalizedMessage } from '@/i18n'

export interface LocalizedMessageBoxOptions {
  confirmButtonText?: LocalizedMessage
  cancelButtonText?: LocalizedMessage
  type?: 'warning' | 'error' | 'info' | 'success'
  distinguishCancelAndClose?: boolean
  inputValue?: string
  inputPlaceholder?: LocalizedMessage
  inputValidator?: (value: string) => boolean | LocalizedMessage
}
interface DialogRequest {
  mode: 'confirm' | 'prompt'
  message: LocalizedMessage
  title: LocalizedMessage
  options: LocalizedMessageBoxOptions
  resolve: (value: { value: string; action: 'confirm' }) => void
  reject: (reason: 'cancel' | 'close') => void
}

export const activeMessageBox = shallowRef<DialogRequest | null>(null)
export const messageBoxInput = ref('')
export const messageBoxValidationAttempted = ref(false)
const queue: DialogRequest[] = []
let closing = false

const openNext = () => {
  if (activeMessageBox.value || closing) return
  const next = queue.shift() || null
  messageBoxInput.value = next?.options.inputValue || ''
  messageBoxValidationAttempted.value = false
  activeMessageBox.value = next
}
const open = (
  mode: DialogRequest['mode'],
  message: LocalizedMessage,
  title: LocalizedMessage,
  options: LocalizedMessageBoxOptions = {},
) =>
  new Promise<{ value: string; action: 'confirm' }>((resolve, reject) => {
    queue.push({ mode, message, title, options, resolve, reject })
    openNext()
  })

/** App-owned dialogs render descriptors through the active locale without replacing input state. */
export const localizedMessageBox = {
  confirm: (
    message: LocalizedMessage,
    title: LocalizedMessage,
    options?: LocalizedMessageBoxOptions,
  ) => open('confirm', message, title, options),
  prompt: (
    message: LocalizedMessage,
    title: LocalizedMessage,
    options?: LocalizedMessageBoxOptions,
  ) => open('prompt', message, title, options),
}
export const confirmMessageBox = () => {
  const request = activeMessageBox.value
  if (!request) return
  request.resolve({ value: messageBoxInput.value, action: 'confirm' })
  closing = true
  activeMessageBox.value = null
}
export const cancelMessageBox = (action: 'cancel' | 'close' = 'cancel') => {
  const request = activeMessageBox.value
  if (!request) return
  request.reject(
    action === 'close' && request.options.distinguishCancelAndClose ? 'close' : 'cancel',
  )
  closing = true
  activeMessageBox.value = null
}
/** Advance only after ElDialog completes closing, so its close callback cannot hide the next request. */
export const messageBoxClosed = () => {
  closing = false
  openNext()
}
