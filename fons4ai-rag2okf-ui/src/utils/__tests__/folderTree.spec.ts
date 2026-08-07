import { describe, expect, it } from 'vitest'

import { annotateDocumentCounts, buildFolderTree, flattenTree, type FolderNode } from '../folderTree'

describe('buildFolderTree', () => {
  it('空数组返回空树', () => {
    expect(buildFolderTree([])).toEqual([])
  })

  it('单个一级目录', () => {
    const tree = buildFolderTree(['/合规材料'])
    expect(tree).toHaveLength(1)
    expect(tree[0].name).toBe('合规材料')
    expect(tree[0].path).toBe('/合规材料')
    expect(tree[0].children).toEqual([])
  })

  it('多个一级目录按名称排序', () => {
    const tree = buildFolderTree(['/技术文档', '/合规材料', '/图片资料'])
    expect(tree).toHaveLength(3)
    // 验证三个目录都存在且按稳定顺序排列
    const names = tree.map((n) => n.name)
    expect(names).toContain('合规材料')
    expect(names).toContain('图片资料')
    expect(names).toContain('技术文档')
    // 验证排序稳定（localeCompare 顺序）
    const sorted = [...names].sort((a, b) => a.localeCompare(b, 'zh-CN'))
    expect(names).toEqual(sorted)
  })

  it('嵌套目录构建为树状结构', () => {
    const tree = buildFolderTree(['/合规材料', '/合规材料/2024年报', '/合规材料/2025Q1'])
    expect(tree).toHaveLength(1)
    expect(tree[0].name).toBe('合规材料')
    expect(tree[0].children).toHaveLength(2)
    expect(tree[0].children[0].name).toBe('2024年报')
    expect(tree[0].children[0].path).toBe('/合规材料/2024年报')
    expect(tree[0].children[1].name).toBe('2025Q1')
  })

  it('三级嵌套目录', () => {
    const tree = buildFolderTree(['/a/b/c', '/a/b', '/a'])
    expect(tree).toHaveLength(1)
    expect(tree[0].name).toBe('a')
    expect(tree[0].children).toHaveLength(1)
    expect(tree[0].children[0].name).toBe('b')
    expect(tree[0].children[0].children).toHaveLength(1)
    expect(tree[0].children[0].children[0].name).toBe('c')
  })

  it('路径不以 / 开头时自动补全', () => {
    const tree = buildFolderTree(['合规材料'])
    expect(tree[0].path).toBe('/合规材料')
  })

  it('路径尾部有 / 时自动去除', () => {
    const tree = buildFolderTree(['/合规材料/'])
    expect(tree[0].path).toBe('/合规材料')
  })

  it('根路径 / 被忽略', () => {
    const tree = buildFolderTree(['/', '/合规材料'])
    expect(tree).toHaveLength(1)
    expect(tree[0].name).toBe('合规材料')
  })

  it('空路径被忽略', () => {
    const tree = buildFolderTree(['', '  ', '/合规材料'])
    expect(tree).toHaveLength(1)
  })

  it('重复路径不创建重复节点', () => {
    const tree = buildFolderTree(['/合规材料', '/合规材料', '/合规材料/2024年报'])
    expect(tree).toHaveLength(1)
    expect(tree[0].children).toHaveLength(1)
  })
})

describe('annotateDocumentCounts', () => {
  it('标注各目录的文档数', () => {
    const tree = buildFolderTree(['/合规材料', '/合规材料/2024年报', '/技术文档'])
    const counts = new Map([
      ['/合规材料', 3],
      ['/合规材料/2024年报', 2],
      ['/技术文档', 5],
    ])
    annotateDocumentCounts(tree, counts)
    expect(tree[0].documentCount).toBe(3)
    expect(tree[0].children[0].documentCount).toBe(2)
    expect(tree[1].documentCount).toBe(5)
  })

  it('路径不在 map 中时默认为 0', () => {
    const tree = buildFolderTree(['/合规材料'])
    annotateDocumentCounts(tree, new Map())
    expect(tree[0].documentCount).toBe(0)
  })
})

describe('flattenTree', () => {
  it('展平嵌套树为路径数组', () => {
    const tree = buildFolderTree(['/合规材料/2024年报', '/技术文档'])
    const paths = flattenTree(tree)
    expect(paths).toContain('/合规材料')
    expect(paths).toContain('/合规材料/2024年报')
    expect(paths).toContain('/技术文档')
  })
})
