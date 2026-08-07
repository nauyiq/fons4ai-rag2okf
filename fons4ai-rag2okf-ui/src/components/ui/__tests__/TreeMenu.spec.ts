import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import TreeMenu from '../TreeMenu.vue'
import type { TreeNode } from '../TreeMenu.vue'

/**
 * TreeMenu 测试。
 * 验证点（对应 T005 Verification 和 Quality）：
 * - 递归渲染多层节点
 * - 点击节点触发 select 事件
 * - 有子节点可展开/收起
 * - 选中节点高亮
 * - documentCount 展示
 * - 节点按层级缩进
 */
describe('TreeMenu', () => {
  const nodes: TreeNode[] = [
    {
      name: '全部文件',
      path: '/',
      children: [
        {
          name: '合规材料',
          path: '/合规材料',
          children: [
            { name: '2024年报', path: '/合规材料/2024年报', documentCount: 5 },
          ],
        },
        { name: '产品文档', path: '/产品文档', documentCount: 3 },
      ],
    },
  ]

  beforeEach(() => {
    document.body.innerHTML = ''
  })

  it('渲染树节点', () => {
    const wrapper = mount(TreeMenu, {
      props: { nodes },
    })
    expect(wrapper.find('.tree-menu').exists()).toBe(true)
    expect(wrapper.findAll('.tree-node')).toHaveLength(4)
  })

  it('节点名称正确渲染', () => {
    const wrapper = mount(TreeMenu, {
      props: { nodes },
    })
    const nodeTexts = wrapper.findAll('.tree-node-label').map(n => n.text())
    expect(nodeTexts).toContain('全部文件')
    expect(nodeTexts).toContain('合规材料')
    expect(nodeTexts).toContain('2024年报')
    expect(nodeTexts).toContain('产品文档')
  })

  it('点击节点触发 select 事件', async () => {
    const wrapper = mount(TreeMenu, {
      props: { nodes },
    })
    const productNode = wrapper.findAll('.tree-node-label').find(n => n.text() === '产品文档')!
    await productNode.trigger('click')
    expect(wrapper.emitted('select')).toBeTruthy()
    expect(wrapper.emitted('select')![0]).toEqual(['/产品文档'])
  })

  it('有子节点的节点含展开图标', () => {
    const wrapper = mount(TreeMenu, {
      props: { nodes },
    })
    const rootNode = wrapper.findAll('.tree-node')[0]
    expect(rootNode.find('.toggle-icon').exists()).toBe(true)
  })

  it('叶子节点不含展开图标', () => {
    const wrapper = mount(TreeMenu, {
      props: { nodes },
    })
    // 2024年报 是叶子节点（无 children）
    const leafNode = wrapper.findAll('.tree-node').find(n =>
      n.find('.tree-node-label').text() === '2024年报'
    )
    expect(leafNode?.find('.toggle-icon').exists()).toBe(false)
  })

  it('点击展开图标收起子节点', async () => {
    const wrapper = mount(TreeMenu, {
      props: { nodes },
    })
    // 默认展开，4 个节点可见
    expect(wrapper.findAll('.tree-node')).toHaveLength(4)

    // 点击根节点的展开图标收起
    const toggle = wrapper.findAll('.toggle-icon')[0]
    await toggle.trigger('click')

    // 收起后只剩根节点
    expect(wrapper.findAll('.tree-node')).toHaveLength(1)
  })

  it('收起后再点击展开恢复子节点', async () => {
    const wrapper = mount(TreeMenu, {
      props: { nodes },
    })
    const toggle = wrapper.findAll('.toggle-icon')[0]

    // 收起
    await toggle.trigger('click')
    expect(wrapper.findAll('.tree-node')).toHaveLength(1)

    // 展开
    await toggle.trigger('click')
    expect(wrapper.findAll('.tree-node')).toHaveLength(4)
  })

  it('选中节点含 active 类', () => {
    const wrapper = mount(TreeMenu, {
      props: { nodes, selectedPath: '/产品文档' },
    })
    const productNode = wrapper.findAll('.tree-node').find(n =>
      n.find('.tree-node-label').text() === '产品文档'
    )
    expect(productNode?.classes()).toContain('active')
  })

  it('documentCount 大于 0 时展示计数', () => {
    const wrapper = mount(TreeMenu, {
      props: { nodes },
    })
    const productNode = wrapper.findAll('.tree-node').find(n =>
      n.find('.tree-node-label').text() === '产品文档'
    )
    expect(productNode?.text()).toContain('3')
  })

  it('节点按层级缩进（depth 属性）', () => {
    const wrapper = mount(TreeMenu, {
      props: { nodes },
    })
    const allNodes = wrapper.findAll('.tree-node')
    // 根节点 depth=0, 一级 depth=1, 二级 depth=2
    expect(allNodes[0].attributes('data-depth')).toBe('0')
    expect(allNodes[1].attributes('data-depth')).toBe('1')
    expect(allNodes[2].attributes('data-depth')).toBe('2')
  })

  it('空数组渲染空状态', () => {
    const wrapper = mount(TreeMenu, {
      props: { nodes: [] },
    })
    expect(wrapper.find('.tree-menu-empty').exists()).toBe(true)
  })
})
