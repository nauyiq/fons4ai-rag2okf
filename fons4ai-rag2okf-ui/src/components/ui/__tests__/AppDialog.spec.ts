import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { Modal } from 'ant-design-vue'
import { nextTick } from 'vue'
import AppDialog from '../AppDialog.vue'

/**
 * AppDialog 升级测试（基于 ant-design-vue a-modal）。
 *
 * <p>迁移后 size 不再映射 CSS 类，而是映射 a-modal 的 width；
 * danger 不再追加 .danger-modal 类，而是通过 wrapClassName 追加 app-dialog-danger；
 * 遮罩点击 / Esc 关闭由 a-modal 的 maskClosable / keyboard 控制，并通过 @cancel 回调到 handleCancel。
 * 因此这里通过 findComponent(Modal) 校验下传给 a-modal 的 props 与 cancel 接线。
 */
describe('AppDialog', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  const findModal = (wrapper: ReturnType<typeof mount>) => wrapper.findComponent(Modal)

  it('默认 size 为 md（a-modal width=560）', () => {
    const wrapper = mount(AppDialog, {
      props: { modelValue: true, title: '测试' },
    })
    expect(findModal(wrapper).props('width')).toBe(560)
  })

  it('传入 size="lg" 时 a-modal width=820', () => {
    const wrapper = mount(AppDialog, {
      props: { modelValue: true, size: 'lg' },
    })
    expect(findModal(wrapper).props('width')).toBe(820)
  })

  it('传入 size="sm" 时 a-modal width=420', () => {
    const wrapper = mount(AppDialog, {
      props: { modelValue: true, size: 'sm' },
    })
    expect(findModal(wrapper).props('width')).toBe(420)
  })

  it('非 persistent 时遮罩可关闭（maskClosable=true）且 cancel 关闭弹窗', async () => {
    const wrapper = mount(AppDialog, {
      props: { modelValue: true },
    })
    const modal = findModal(wrapper)
    expect(modal.props('maskClosable')).toBe(true)
    // 模拟 a-modal 取消（遮罩点击 / Esc 由 antd 内部触发 cancel 事件）
    modal.vm.$emit('cancel')
    await nextTick()
    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')![0]).toEqual([false])
    expect(wrapper.emitted('cancel')).toBeTruthy()
  })

  it('persistent 时遮罩不可关闭（maskClosable=false）', async () => {
    const wrapper = mount(AppDialog, {
      props: { modelValue: true, persistent: true },
    })
    const modal = findModal(wrapper)
    expect(modal.props('maskClosable')).toBe(false)
    // 即便 a-modal 触发 cancel（安全网），persistent 仍应阻止关闭
    modal.vm.$emit('cancel')
    await nextTick()
    expect(wrapper.emitted('update:modelValue')).toBeFalsy()
    expect(wrapper.emitted('cancel')).toBeFalsy()
  })

  it('非 persistent 时 Esc 可关闭（keyboard=true）', async () => {
    const wrapper = mount(AppDialog, {
      props: { modelValue: true },
    })
    const modal = findModal(wrapper)
    expect(modal.props('keyboard')).toBe(true)
    modal.vm.$emit('cancel')
    await nextTick()
    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')![0]).toEqual([false])
  })

  it('persistent 时 Esc 不关闭（keyboard=false）', async () => {
    const wrapper = mount(AppDialog, {
      props: { modelValue: true, persistent: true },
    })
    const modal = findModal(wrapper)
    expect(modal.props('keyboard')).toBe(false)
    modal.vm.$emit('cancel')
    await nextTick()
    expect(wrapper.emitted('update:modelValue')).toBeFalsy()
  })

  it('danger 样式时 wrapClassName 含 app-dialog-danger', () => {
    const wrapper = mount(AppDialog, {
      props: { modelValue: true, danger: true },
    })
    expect(findModal(wrapper).props('wrapClassName')).toContain('app-dialog-danger')
  })
})
