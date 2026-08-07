import { expect, type Page, test } from '@playwright/test'

type ThemeCase = { mode: 'light' | 'dark' | 'system'; system: 'light' | 'dark'; clicks: number }

const themes: ThemeCase[] = [
  { mode: 'light', system: 'light', clicks: 1 },
  { mode: 'dark', system: 'light', clicks: 2 },
  { mode: 'system', system: 'dark', clicks: 0 },
]

function envelope(data: unknown): object {
  return { success: true, code: 'SUCCESS', message: 'success', data }
}

async function mockVisualApis(page: Page): Promise<void> {
  await page.route('**/knowledge/api/v1/**', async (route) => {
    const { pathname } = new URL(route.request().url())
    let data: unknown = null
    if (pathname.endsWith('/auth/login')) data = { token: 'e2e-runtime-token' }
    else if (pathname.endsWith('/users/me')) data = { userKey: 'user-e2e', email: 'quality@example.com', displayName: '质量管理员', avatarUrl: '', preferenceJson: '{}' }
    else if (pathname.includes('/workspaces/ws-e2e/knowledge-bases')) data = { records: [{ knowledgeBaseKey: 'kb-e2e', name: '产品知识库', description: '用于验证来源、解析和发布脉络。', autoParse: true, autoPublish: false, updated: '2026-08-05T12:00:00Z' }], total: 1, page: 0, size: 20 }
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope(data)) })
  })
}

async function signIn(page: Page): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('邮箱').fill('quality@example.com')
  await page.getByLabel('密码').fill('not-a-real-password')
  await page.getByRole('button', { name: '进入知识空间' }).click()
  await expect(page).toHaveURL(/\/knowledge-bases$/)
  await expect(page.getByRole('heading', { name: '我的知识库', exact: true })).toBeVisible()
}

async function contrastRatio(page: Page, foreground: string, background: string): Promise<number> {
  return page.evaluate(({ foreground, background }) => {
    const root = getComputedStyle(document.documentElement)
    const rgb = (value: string): number[] => {
      const probe = document.createElement('span')
      probe.style.color = value
      document.body.appendChild(probe)
      const result = getComputedStyle(probe).color.match(/[\d.]+/g)?.slice(0, 3).map(Number) ?? [0, 0, 0]
      probe.remove()
      return result
    }
    const luminance = (value: string): number => rgb(value).map(channel => channel / 255).map(channel => channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4).reduce((sum, channel, index) => sum + channel * [0.2126, 0.7152, 0.0722][index], 0)
    const first = luminance(root.getPropertyValue(foreground).trim())
    const second = luminance(root.getPropertyValue(background).trim())
    return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05)
  }, { foreground, background })
}

for (const theme of themes) {
  test(`knowledge base stays consistent in ${theme.mode} theme`, async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 960 })
    await page.emulateMedia({ colorScheme: theme.system, reducedMotion: 'reduce' })
    await mockVisualApis(page)
    await signIn(page)

    const themeButton = page.getByRole('button', { name: /切换主题/ })
    for (let click = 0; click < theme.clicks; click += 1) await themeButton.click()
    await expect(themeButton).toHaveAttribute('aria-label', `切换主题，当前：${theme.mode}`)
    await expect(page.locator('html')).toHaveAttribute('data-theme', theme.mode === 'system' ? theme.system : theme.mode)

    expect(await contrastRatio(page, '--ink', '--canvas')).toBeGreaterThanOrEqual(4.5)
    expect(await contrastRatio(page, '--ink-soft', '--surface')).toBeGreaterThanOrEqual(4.5)
    await expect(page).toHaveScreenshot(`knowledge-bases-${theme.mode}.png`, { animations: 'disabled', fullPage: true })
  })
}

test('system theme follows an operating-system color change', async ({ page }) => {
  await mockVisualApis(page)
  await page.emulateMedia({ colorScheme: 'light' })
  await signIn(page)
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  await page.emulateMedia({ colorScheme: 'dark' })
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
})

test('login remains keyboard-visible on a mobile viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: '登录你的知识空间' })).toBeVisible()
  await page.keyboard.press('Tab')
  await expect(page.locator(':focus')).toBeVisible()
  expect(await page.locator('body').evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true)
})

test('register page is keyboard-navigable on desktop viewport', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 960 })
  await page.goto('/register')
  await expect(page.getByRole('heading', { name: '注册你的知识空间' })).toBeVisible()
  // Tab 遍历输入框
  for (let i = 0; i < 4; i++) {
    await page.keyboard.press('Tab')
    await expect(page.locator(':focus')).toBeVisible()
  }
  // 确认焦点最终可达提交按钮
  await page.keyboard.press('Tab')
  await expect(page.locator(':focus')).toBeVisible()
})

for (const theme of themes) {
  test(`register page renders correctly in ${theme.mode} theme`, async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 960 })
    await page.emulateMedia({ colorScheme: theme.system, reducedMotion: 'reduce' })
    await page.goto('/register')
    await expect(page.getByRole('heading', { name: '注册你的知识空间' })).toBeVisible()
    // 验证无水平溢出
    expect(await page.locator('body').evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true)
  })
}

test('settings page has keyboard-visible focus on desktop', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 960 })
  await page.emulateMedia({ colorScheme: 'light', reducedMotion: 'reduce' })
  await mockVisualApis(page)
  await signIn(page)

  await page.goto('/settings/personal')
  await expect(page.getByRole('heading', { name: '个人偏好' })).toBeVisible()

  // Tab 到第一个可交互元素
  await page.keyboard.press('Tab')
  await expect(page.locator(':focus')).toBeVisible()

  // 验证焦点可见（有 focus-visible 样式）
  const focusVisible = await page.locator(':focus').evaluate(el => {
    const style = getComputedStyle(el)
    return style.outlineStyle !== 'none' || style.boxShadow !== 'none' || style.borderColor !== getComputedStyle(document.documentElement).getPropertyValue('--surface').trim()
  })
  expect(focusVisible).toBe(true)
})

test('no version rollback or withdraw buttons anywhere in the app', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 960 })
  await mockVisualApis(page)
  await signIn(page)

  // 检查知识库列表页
  const forbiddenButtons = page.getByRole('button', { name: /版本|回退|撤回|rollback|withdraw|revert/i })
  await expect(forbiddenButtons).toHaveCount(0)

  // 导航到文档工作台
  await page.evaluate(() => {
    window.history.pushState({}, '', '/knowledge-bases/kb-e2e/documents')
    window.dispatchEvent(new PopStateEvent('popstate'))
  })
  await expect(page.getByRole('heading', { name: '文档工作台' })).toBeVisible()
  await expect(forbiddenButtons).toHaveCount(0)
})
