import { computed, defineComponent, inject, provide, type Ref } from 'vue'

const Slot = defineComponent({ template: '<div><slot /><slot name="footer" /></div>' })
const dropdownCommand = Symbol('dropdown-command')
const Dialog = defineComponent({
  props: ['modelValue'],
  template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>',
})
export const componentStubs = {
  ElButton: defineComponent({
    props: ['nativeType'],
    emits: ['click'],
    template:
      '<button :type="nativeType || \'button\'" @click="$emit(\'click\')"><slot /></button>',
  }),
  ElInput: defineComponent({
    props: ['modelValue', 'ariaLabel'],
    emits: ['update:modelValue'],
    setup(_props, { emit }) {
      return {
        change: (event: Event) =>
          emit('update:modelValue', (event.target as HTMLInputElement).value),
      }
    },
    template: '<input :value="modelValue" :aria-label="ariaLabel" @input="change" />',
  }),
  ElSelect: defineComponent({
    props: ['modelValue', 'ariaLabel'],
    emits: ['update:modelValue'],
    setup(_props, { emit }) {
      return {
        change: (event: Event) =>
          emit('update:modelValue', (event.target as HTMLSelectElement).value),
      }
    },
    template:
      '<select :value="modelValue" :aria-label="ariaLabel" @change="change"><slot /></select>',
  }),
  ElOption: defineComponent({
    props: ['value', 'label'],
    template: '<option :value="value">{{ label }}</option>',
  }),
  ElDropdown: defineComponent({
    emits: ['command'],
    setup(_props, { emit, slots }) {
      provide(dropdownCommand, (command: string) => emit('command', command))
      return { slots }
    },
    template: '<div><slot /><div class="dropdown-stub"><slot name="dropdown" /></div></div>',
  }),
  ElDropdownMenu: Slot,
  ElDropdownItem: defineComponent({
    props: ['command', 'disabled'],
    setup(props) {
      const emitCommand = inject<(command: string) => void>(dropdownCommand)
      return { props, emitCommand }
    },
    template: '<button :disabled="disabled" @click="emitCommand?.(command)"><slot /></button>',
  }),
  ElTable: defineComponent({
    props: { data: { type: Array, default: () => [] } },
    setup(props, { expose }) {
      provide(
        'tableRows',
        computed(() => props.data),
      )
      expose({ clearSelection: () => {} })
    },
    template: '<div><slot /></div>',
  }),
  ElTableColumn: defineComponent({
    props: ['type'],
    setup() {
      return { rows: inject<Ref<unknown[]>>('tableRows') }
    },
    template:
      '<div v-if="type !== \'selection\'"><div v-for="(row, index) in rows" :key="index"><slot :row="row" /></div></div>',
  }),
  ElDrawer: Dialog,
  ElDialog: Dialog,
  ElAlert: defineComponent({ props: ['title'], template: '<p role="alert">{{ title }}</p>' }),
  ElTag: Slot,
  ElAvatar: Slot,
  ElForm: Slot,
  ElFormItem: Slot,
  ElDescriptions: Slot,
  ElDescriptionsItem: Slot,
  ElPagination: true,
}
