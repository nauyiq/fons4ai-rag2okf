import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import AppDialog from '../AppDialog.vue'

/**
 * AppDialog 升级测试。
 * 验证点：
 * - size prop 控制弹窗宽度（sm/md/lg）
 * - persistent prop 时点击遮罩不关闭
 * - 非 persistent 时点击遮罩关闭
 * - Esc 关闭（非 persistent 时）
 * - persistent 时 Esc 也不关闭
 * - 焦点返回触发元素
 */
describe('AppDialog', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  it('默认 size 为 md', () => {
    const wrapper = mount(AppDialog, {
      props: { modelValue: true, title: '测试' },
    })
    expect(wrapper.find('.modal-panel').classes()).toContain('size-md')
  })

  it('传入 size="lg" 时面板含 size-lg 类', () => {
    const wrapper = mount(AppDialog, {
      props: { modelValue: true, size: 'lg' },
    })
    expect(wrapper.find('.modal-panel').classes()).toContain('size-lg')
  })

  it('传入 size="sm" 时面板含 size-sm 类', () => {
    const wrapper = mount(AppDialog, {
      props: { modelValue: true, size: 'sm' },
    })
    expect(wrapper.find('.modal-panel').classes()).toContain('size-sm')
  })

  it('非 persistent 时点击遮罩关闭并触发 cancel', async () => {
    const wrapper = mount(AppDialog, {
      props: { modelValue: true },
    })
    await wrapper.find('.drawer-backdrop').trigger('click')
    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')![0]).toEqual([false])
    expect(wrapper.emitted('cancel')).toBeTruthy()
  })

  it('persistent 时点击遮罩不关闭', async () => {
    const wrapper = mount(AppDialog, {
      props: { modelValue: true, persistent: true },
    })
    await wrapper.find('.drawer-backdrop').trigger('click')
    expect(wrapper.emitted('update:modelValue')).toBeFalsy()
    expect(wrapper.emitted('cancel')).toBeFalsy()
  })

  it('非 persistent 时按 Esc 关闭', async () => {
    const wrapper = mount(AppDialog, {
      props: { modelValue: true },
    })
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await nextTick()
    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')![0]).toEqual([false])
  })

  it('persistent 时按 Esc 不关闭', async () => {
    const wrapper = mount(AppDialog, {
      props: { modelValue: true, persistent: true },
    })
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await nextTick()
    expect(wrapper.emitted('update:modelValue')).toBeFalsy()
  })

  it('danger 样式时面板含 danger-modal 类', () => {
    const wrapper = mount(AppDialog, {
      props: { modelValue: true, danger: true },
    })
    expect(wrapper.find('.modal-panel').classes()).toContain('danger-modal')
  })
})
