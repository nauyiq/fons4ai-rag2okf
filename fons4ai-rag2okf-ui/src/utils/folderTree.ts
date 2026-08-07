/**
 * 目录树构建工具。
 *
 * <p>从文档的 folderPath 扁平路径聚合 ∪ localStorage 暂存路径，
 * 构建为嵌套树结构供 DocumentsView 树状展示。
 */

/** 目录树节点。 */
export interface FolderNode {
  /** 目录名（最后一级，不含路径前缀）。 */
  name: string
  /** 完整路径（如 '/合规材料/2024年报'）。 */
  path: string
  /** 子目录。 */
  children: FolderNode[]
  /** 该目录直接子文档数（不含子目录的文档）。 */
  documentCount: number
}

/**
 * 将扁平路径数组构建为嵌套树。
 *
 * @param folderPaths 扁平路径数组（如 ['/合规材料', '/合规材料/2024年报', '/技术文档']）
 * @returns 根级目录节点数组
 */
export function buildFolderTree(folderPaths: string[]): FolderNode[] {
  const root: FolderNode[] = []

  for (const rawPath of folderPaths) {
    // 标准化路径：确保以 / 开头，去除尾部 /
    const normalized = normalizePath(rawPath)
    if (!normalized || normalized === '/') continue

    const segments = normalized.split('/').filter(Boolean)
    if (segments.length === 0) continue

    insertPath(root, segments, normalized)
  }

  // 按名称排序，保持稳定展示顺序
  sortTree(root)
  return root
}

/**
 * 统计每个目录节点的文档数。
 *
 * @param nodes 目录树节点
 * @param folderPathCounts 路径到文档数的映射
 */
export function annotateDocumentCounts(nodes: FolderNode[], folderPathCounts: Map<string, number>): void {
  for (const node of nodes) {
    node.documentCount = folderPathCounts.get(node.path) ?? 0
    annotateDocumentCounts(node.children, folderPathCounts)
  }
}

/** 标准化路径：确保以 / 开头，去除尾部 /。 */
function normalizePath(path: string): string {
  let normalized = path.trim()
  if (!normalized) return ''
  if (!normalized.startsWith('/')) normalized = '/' + normalized
  if (normalized.length > 1 && normalized.endsWith('/')) normalized = normalized.slice(0, -1)
  return normalized
}

/** 递归插入路径段到树中。parentPath 为父级路径前缀。 */
function insertPath(nodes: FolderNode[], segments: string[], fullPath: string, parentPath = ''): void {
  const [first, ...rest] = segments
  const currentPath = parentPath + '/' + first
  let node = nodes.find((n) => n.name === first)

  if (!node) {
    node = { name: first, path: currentPath, children: [], documentCount: 0 }
    nodes.push(node)
  }

  if (rest.length === 0) {
    // 叶子节点使用完整路径（可能与 currentPath 一致）
    node.path = fullPath
  } else {
    insertPath(node.children, rest, fullPath, currentPath)
  }
}

/** 按名称递归排序树节点。 */
function sortTree(nodes: FolderNode[]): void {
  nodes.sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
  for (const node of nodes) {
    sortTree(node.children)
  }
}

/**
 * 展平目录树为路径数组（用于调试或验证）。
 */
export function flattenTree(nodes: FolderNode[]): string[] {
  const paths: string[] = []
  for (const node of nodes) {
    paths.push(node.path)
    paths.push(...flattenTree(node.children))
  }
  return paths
}
