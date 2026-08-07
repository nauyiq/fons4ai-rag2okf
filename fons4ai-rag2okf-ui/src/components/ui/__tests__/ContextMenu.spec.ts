import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import ContextMenu from '../ContextMenu.vue'
import type { ContextMenuItem } from '../ContextMenu.vue'

/**
 * ContextMenu 测试。
 * 验证点（对应 T005 Verification 和 Quality）：
 * - visible 控制显示/隐藏
 * - 菜单定位跟随 x/y 坐标
 * - 渲染传入的菜单项
 * - 点击菜单项触发 select 事件并关闭
 * - 点击外部关闭
 * - disabled 菜单项不触发 select
 */
describe('ContextMenu', () => {
  const items: ContextMenuItem[] = [
    { key: 'create-folder', label: '新建目录' },
    { key: 'rename', label: '重命名' },
    { key: 'delete', label: '删除', danger: true },
    { key: 'disabled-item', label: '不可用', disabled: true },
  ]

  beforeEach(() => {
    document.body.innerHTML = ''
  })

  it('visible 为 true 时渲染菜单', () => {
    const wrapper = mount(ContextMenu, {
      props: { visible: true, x: 100, y: 200, items },
    })
    expect(wrapper.find('.context-menu').exists()).toBe(true)
  })

  it('visible 为 false 时不渲染', () => {
    const wrapper = mount(ContextMenu, {
      props: { visible: false, x: 100, y: 200, items },
    })
    expect(wrapper.find('.context-menu').exists()).toBe(false)
  })

  it('渲染所有菜单项', () => {
    const wrapper = mount(ContextMenu, {
      props: { visible: true, x: 100, y: 200, items },
    })
    const menuItems = wrapper.findAll('.context-menu-item')
    expect(menuItems).toHaveLength(4)
    expect(menuItems[0].text()).toContain('新建目录')
    expect(menuItems[1].text()).toContain('重命名')
    expect(menuItems[2].text()).toContain('删除')
  })

  it('点击菜单项触发 select 事件并关闭', async () => {
    const wrapper = mount(ContextMenu, {
      props: { visible: true, x: 100, y: 200, items },
    })
    await wrapper.findAll('.context-menu-item')[0].trigger('click')
    expect(wrapper.emitted('select')).toBeTruthy()
    expect(wrapper.emitted('select')![0]).toEqual(['create-folder'])
    expect(wrapper.emitted('update:visible')).toBeTruthy()
    expect(wrapper.emitted('update:visible')![0]).toEqual([false])
  })

  it('disabled 菜单项不触发 select', async () => {
    const wrapper = mount(ContextMenu, {
      props: { visible: true, x: 100, y: 200, items },
    })
    const disabledItem = wrapper.findAll('.context-menu-item')[3]
    expect(disabledItem.classes()).toContain('disabled')
    await disabledItem.trigger('click')
    expect(wrapper.emitted('select')).toBeFalsy()
  })

  it('danger 菜单项含 danger 样式', () => {
    const wrapper = mount(ContextMenu, {
      props: { visible: true, x: 100, y: 200, items },
    })
    const deleteItem = wrapper.findAll('.context-menu-item')[2]
    expect(deleteItem.classes()).toContain('danger')
  })

  it('菜单定位跟随 x/y 坐标', () => {
    const wrapper = mount(ContextMenu, {
      props: { visible: true, x: 300, y: 400, items },
    })
    const menu = wrapper.find('.context-menu')
    expect(menu.attributes('style')).toContain('left: 300px')
    expect(menu.attributes('style')).toContain('top: 400px')
  })

  it('点击外部关闭菜单', async () => {
    const wrapper = mount(ContextMenu, {
      props: { visible: true, x: 100, y: 200, items },
    })
    // 模拟点击遮罩层（菜单外部）
    await wrapper.find('.context-menu-backdrop').trigger('click')
    expect(wrapper.emitted('update:visible')).toBeTruthy()
    expect(wrapper.emitted('update:visible')![0]).toEqual([false])
  })
})
