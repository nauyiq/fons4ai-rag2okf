import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import ConfirmDialog from '../ConfirmDialog.vue'

/**
 * ConfirmDialog 测试。
 * 验证点：
 * - danger 样式默认启用
 * - persistent 默认启用（破坏性操作不允许误关）
 * - 确认按钮触发 confirm 事件并关闭
 * - 取消按钮触发 cancel 事件并关闭
 * - 标题和描述正确渲染
 */
describe('ConfirmDialog', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  it('默认含 danger 和 persistent 属性', () => {
    const wrapper = mount(ConfirmDialog, {
      props: { modelValue: true, title: '确认删除' },
    })
    const panel = wrapper.find('.modal-panel')
    expect(panel.classes()).toContain('danger-modal')
    // persistent 时点击遮罩不关闭
    expect(wrapper.find('.drawer-backdrop').exists()).toBe(true)
  })

  it('渲染标题和描述', () => {
    const wrapper = mount(ConfirmDialog, {
      props: { modelValue: true, title: '确认删除', description: '此操作不可撤销' },
    })
    expect(wrapper.text()).toContain('确认删除')
    expect(wrapper.text()).toContain('此操作不可撤销')
  })

  it('点击确认按钮触发 confirm 事件并关闭', async () => {
    const wrapper = mount(ConfirmDialog, {
      props: { modelValue: true, title: '确认删除' },
    })
    await wrapper.find('[data-testid="confirm-btn"]').trigger('click')
    expect(wrapper.emitted('confirm')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')![0]).toEqual([false])
  })

  it('点击取消按钮触发 cancel 事件并关闭', async () => {
    const wrapper = mount(ConfirmDialog, {
      props: { modelValue: true, title: '确认删除' },
    })
    await wrapper.find('[data-testid="cancel-btn"]').trigger('click')
    expect(wrapper.emitted('cancel')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')![0]).toEqual([false])
  })

  it('确认按钮文案可通过 confirmText prop 自定义', () => {
    const wrapper = mount(ConfirmDialog, {
      props: { modelValue: true, title: '确认', confirmText: '确定删除' },
    })
    expect(wrapper.find('[data-testid="confirm-btn"]').text()).toContain('确定删除')
  })

  it('取消按钮文案可通过 cancelText prop 自定义', () => {
    const wrapper = mount(ConfirmDialog, {
      props: { modelValue: true, title: '确认', cancelText: '再想想' },
    })
    expect(wrapper.find('[data-testid="cancel-btn"]').text()).toContain('再想想')
  })

  it('persistent 时点击遮罩不关闭', async () => {
    const wrapper = mount(ConfirmDialog, {
      props: { modelValue: true, title: '确认删除' },
    })
    await wrapper.find('.drawer-backdrop').trigger('click')
    expect(wrapper.emitted('update:modelValue')).toBeFalsy()
  })
})
