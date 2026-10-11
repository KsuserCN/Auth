import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, nextTick, ref, watch } from 'vue'
import LocalizedMessageBox from '@/components/LocalizedMessageBox.vue'
import { setLanguagePreference, t, uiMessage } from '@/i18n'
import { localizedMessageBox } from './localizedMessageBox'

const render = () =>
  mount(LocalizedMessageBox, {
    global: {
      stubs: {
        'el-dialog': defineComponent({
          props: ['modelValue', 'title', 'beforeClose'],
          emits: ['closed'],
          setup(props, { emit }) {
            const visible = ref(Boolean(props.modelValue))
            watch(
              () => props.modelValue,
              (value) => {
                visible.value = Boolean(value)
                if (!value) void nextTick(() => emit('closed'))
              },
            )
            const close = () =>
              props.beforeClose(() => {
                visible.value = false
              })
            return { visible, close }
          },
          template:
            '<section v-if="visible"><button class="close-dialog" @click="close">×</button><h2>{{ title }}</h2><slot /><slot name="footer" /></section>',
        }),
        'el-input': {
          props: ['modelValue', 'placeholder'],
          emits: ['update:modelValue', 'input'],
          template:
            '<input :value="modelValue" :placeholder="placeholder" @input="$emit(\'update:modelValue\', $event.target.value); $emit(\'input\', $event.target.value)" />',
        },
        'el-button': { template: '<button><slot /></button>' },
        'el-icon': true,
      },
    },
  })

describe('live confirmation and prompt localization', () => {
  it('updates an open prompt and its validation while preserving the typed passkey name', async () => {
    const wrapper = render()
    const pending = localizedMessageBox.prompt(
      uiMessage('ui.loginOptions.message061'),
      uiMessage('ui.loginOptions.message062'),
      {
        inputValue: '我的 MacBook',
        inputPlaceholder: uiMessage('ui.addPasskey.message013'),
        confirmButtonText: uiMessage('ui.devices.message034'),
        cancelButtonText: uiMessage('ui.addPasskey.message009'),
        inputValidator: (value) => (value.trim() ? true : t('ui.addPasskey.message016')),
      },
    )
    await nextTick()
    await wrapper.find('input').setValue('typed name')
    setLanguagePreference('en')
    await nextTick()
    expect(wrapper.find('h2').text()).toBe('Rename passkey')
    expect(wrapper.text()).toContain('Enter a new passkey name')
    expect(wrapper.find('input').element.value).toBe('typed name')
    expect(wrapper.find('input').attributes('placeholder')).toBe('For example: My MacBook Pro')
    await wrapper.find('input').setValue('')
    expect(wrapper.text()).toContain('Please enter a passkey name')
    setLanguagePreference('zh-Hans')
    await nextTick()
    expect(wrapper.text()).toContain('请输入 Passkey 名称')
    await wrapper.find('input').setValue('保留这个名称')
    await wrapper.findAll('.dialog-actions button')[1]!.trigger('click')
    await expect(pending).resolves.toEqual({ value: '保留这个名称', action: 'confirm' })
    wrapper.unmount()
  })

  it('updates an open confirmation and keeps cancellation behavior', async () => {
    const wrapper = render()
    const pending = localizedMessageBox.confirm(
      uiMessage('ui.loginOptions.message065', { arg0: '我的 Passkey' }),
      uiMessage('ui.loginOptions.message066'),
    )
    const rejection = expect(pending).rejects.toBe('cancel')
    setLanguagePreference('en')
    await nextTick()
    expect(wrapper.text()).toContain('Delete passkey “我的 Passkey”?')
    expect(wrapper.find('h2').text()).toBe('Confirm deletion')
    await wrapper.findAll('.dialog-actions button')[0]!.trigger('click')
    await rejection
    wrapper.unmount()
  })

  it('shows the next queued prompt after closing the current dialog', async () => {
    const wrapper = render()
    const first = localizedMessageBox.confirm(
      uiMessage('ui.loginOptions.message065', { arg0: 'One' }),
      uiMessage('ui.loginOptions.message066'),
      { distinguishCancelAndClose: true },
    )
    const firstClosed = expect(first).rejects.toBe('close')
    const second = localizedMessageBox.prompt(
      uiMessage('ui.loginOptions.message061'),
      uiMessage('ui.loginOptions.message062'),
      { inputValue: 'Second passkey' },
    )
    await nextTick()
    await wrapper.find('.close-dialog').trigger('click')
    await firstClosed
    await nextTick()
    expect(wrapper.find('h2').text()).toBe(t('ui.loginOptions.message062'))
    expect(wrapper.find('input').element.value).toBe('Second passkey')
    await wrapper.findAll('.dialog-actions button')[1]!.trigger('click')
    await expect(second).resolves.toEqual({ value: 'Second passkey', action: 'confirm' })
    wrapper.unmount()
  })
})
