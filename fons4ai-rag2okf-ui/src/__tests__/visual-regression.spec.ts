import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

import { describe, expect, it } from 'vitest'

const sourceRoot = resolve(process.cwd(), 'src')

function read(relativePath: string): string {
  return readFileSync(resolve(sourceRoot, relativePath), 'utf8')
}

/**
 * T019 视觉契约回归。
 *
 * 像素截图由 Playwright 的 theme-visual.spec.ts 承担；本组测试锁定所有重塑页面
 * 必须依赖同一主题 token、保留窄屏降级和键盘焦点，避免局部样式绕过主题体系。
 */
describe('三主题、响应式与键盘视觉契约（T019）', () => {
  const tokens = read('styles/tokens.css')

  it('light、dark 与 system 共用语义 token，system 由运行时解析而非第三套硬编码颜色', () => {
    expect(tokens).toContain(':root {')
    expect(tokens).toContain(":root[data-theme='dark']")
    expect(tokens).toContain('--canvas:')
    expect(tokens).toContain('--surface:')
    expect(tokens).toContain('--ink:')
    expect(tokens).toContain('--danger:')
    expect(tokens).not.toContain(":root[data-theme='system']")
  })

  it('全局键盘焦点和减少动画偏好保持可见、可预测', () => {
    expect(tokens).toMatch(/button:focus-visible[\s\S]*outline:/)
    expect(tokens).toContain('a:focus-visible')
    expect(tokens).toContain('@media (prefers-reduced-motion: reduce)')
  })

  it('顶部导航、知识库列表和登录布局具有 760px 窄屏降级', () => {
    expect(tokens).toContain('@media (max-width: 760px)')
    expect(tokens).toMatch(/\.topbar\s*\{[^}]*height:\s*56px/)
    expect(tokens).toMatch(/\.knowledge-grid,[^}]*grid-template-columns:\s*1fr/)
    expect(tokens).toMatch(/\.login-stage\s*\{[^}]*grid-template-columns:\s*1fr/)
    expect(tokens).toMatch(/@media \(max-width: 600px\)[\s\S]*\.topbar-nav \.nav-item\.disabled\s*\{[^}]*display:\s*none/)
    expect(tokens).toMatch(/@media \(max-width: 600px\)[\s\S]*\.topbar-actions \.breadcrumb\s*\{[^}]*display:\s*none/)
  })

  it('设置中心在窄屏使用紧凑双项导航，避免侧栏挤占首屏', () => {
    const settingsCenter = read('views/settings/SettingsCenterView.vue')
    expect(settingsCenter).toMatch(/@media \(max-width: 760px\)[\s\S]*grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\)/)
    expect(settingsCenter).toMatch(/\.version-row\s*\{[^}]*display:\s*none/)
    expect(settingsCenter).toMatch(/\.settings-main\s*\{[^}]*padding:\s*20px 16px 32px/)
  })

  it.each([
    ['views/documents/DocumentsView.vue', '@media (max-width: 760px)', 'overflow-x: auto'],
    ['views/documents/DocumentDetailView.vue', '@media (max-width: 720px)', 'var(--surface)'],
    ['views/settings/ModelSettingsTab.vue', '@media (max-width: 992px)', 'var(--surface)'],
    ['views/knowledge-bases/KnowledgeBaseSettingsView.vue', '@media (max-width: 760px)', 'var(--ink)'],
  ])('%s 使用主题 token 并声明响应式降级', (file, breakpoint, themeToken) => {
    const source = read(file)
    expect(source).toContain(breakpoint)
    expect(source).toContain(themeToken)
  })

  it('目录树行保留 focus-visible，移动端目录区域允许横向滚动', () => {
    const documents = read('views/documents/DocumentsView.vue')
    expect(documents).toContain('.document-row:focus-visible')
    expect(documents).toContain('overflow-x: auto')
  })
})
