import { describe, it, expect, beforeEach } from 'vitest'
import { mount, DOMWrapper } from '@vue/test-utils'
import { Modal } from 'ant-design-vue'
import { nextTick } from 'vue'
import ConfirmDialog from '../ConfirmDialog.vue'

/**
 * ConfirmDialog 测试（基于 ant-design-vue a-modal）。
 *
 * <p>a-modal 将内容 teleport 到 document.body，弹窗内按钮（data-testid）和标题需通过
 * document.body 检索；danger / persistent 通过 a-modal 的 wrapClassName / maskClosable / keyboard
 * props 体现，因此使用 findComponent(Modal) 校验。
 */
describe('ConfirmDialog', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  /** a-modal 内容 teleport 到 body，需要等待渲染后从 body 检索 */
  const body = () => new DOMWrapper(document.body)
  const findModal = (wrapper: ReturnType<typeof mount>) => wrapper.findComponent(Modal)

  it('默认含 danger 和 persistent 属性', () => {
    const wrapper = mount(ConfirmDialog, {
      props: { modelValue: true, title: '确认删除' },
    })
    expect(findModal(wrapper).props('wrapClassName')).toContain('app-dialog-danger')
    // persistent 时遮罩与 Esc 均不可关闭
    expect(findModal(wrapper).props('maskClosable')).toBe(false)
    expect(findModal(wrapper).props('keyboard')).toBe(false)
  })

  it('渲染标题和描述', async () => {
    const wrapper = mount(ConfirmDialog, {
      props: { modelValue: true, title: '确认删除', description: '此操作不可撤销' },
    })
    await nextTick()
    expect(findModal(wrapper).props('title')).toBe('确认删除')
    expect(body().text()).toContain('此操作不可撤销')
  })

  it('点击确认按钮触发 confirm 事件并关闭', async () => {
    const wrapper = mount(ConfirmDialog, {
      props: { modelValue: true, title: '确认删除' },
    })
    await nextTick()
    await body().get('[data-testid="confirm-btn"]').trigger('click')
    expect(wrapper.emitted('confirm')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')![0]).toEqual([false])
  })

  it('点击取消按钮触发 cancel 事件并关闭', async () => {
    const wrapper = mount(ConfirmDialog, {
      props: { modelValue: true, title: '确认删除' },
    })
    await nextTick()
    await body().get('[data-testid="cancel-btn"]').trigger('click')
    expect(wrapper.emitted('cancel')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')![0]).toEqual([false])
  })

  it('确认按钮文案可通过 confirmText prop 自定义', async () => {
    mount(ConfirmDialog, {
      props: { modelValue: true, title: '确认', confirmText: '确定删除' },
    })
    await nextTick()
    expect(body().get('[data-testid="confirm-btn"]').text()).toContain('确定删除')
  })

  it('取消按钮文案可通过 cancelText prop 自定义', async () => {
    mount(ConfirmDialog, {
      props: { modelValue: true, title: '确认', cancelText: '再想想' },
    })
    await nextTick()
    expect(body().get('[data-testid="cancel-btn"]').text()).toContain('再想想')
  })

  it('persistent 时点击遮罩不关闭', async () => {
    const wrapper = mount(ConfirmDialog, {
      props: { modelValue: true, title: '确认删除' },
    })
    const modal = findModal(wrapper)
    // persistent 屏蔽 maskClosable；即便触发 cancel（安全网），handleModalCancel 也应阻止关闭
    modal.vm.$emit('cancel')
    await nextTick()
    expect(wrapper.emitted('update:modelValue')).toBeFalsy()
  })
})
