import { expect, type Page, test } from '@playwright/test'

// --- 响应封装与 Mock 辅助 ---

/** 统一成功信封，与后端 R<T> 结构一致。 */
function envelope(data: unknown): object {
  return { success: true, code: 'SUCCESS', message: 'success', data }
}

/** 统一错误信封。 */
function errorBody(code: string, message: string): object {
  return { success: false, code, message, data: null }
}

const defaultProfile = {
  userKey: 'user-e2e',
  email: 'admin@example.com',
  displayName: '文档管理员',
  avatarUrl: '',
  preferenceJson: '{}',
}

type LoginMock = { status: number; body: object }

/**
 * 拦截全部 /knowledge/api/v1/ 请求并返回 Mock 响应。
 * loginMock 不为空时登录接口返回指定错误状态。
 */
async function mockApis(page: Page, loginMock?: LoginMock): Promise<void> {
  await page.route('**/knowledge/api/v1/**', async (route) => {
    const request = route.request()
    const { pathname } = new URL(request.url())

    if (pathname.endsWith('/auth/login')) {
      if (loginMock) {
        await route.fulfill({ status: loginMock.status, contentType: 'application/json', body: JSON.stringify(loginMock.body) })
        return
      }
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope({ token: 'e2e-runtime-token' })) })
      return
    }

    if (pathname.endsWith('/auth/logout')) {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope(null)) })
      return
    }

    if (pathname.endsWith('/users/me')) {
      if (request.method() === 'PATCH') {
        const body = JSON.parse(request.postData() ?? '{}')
        await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope({ ...defaultProfile, ...body })) })
        return
      }
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope(defaultProfile)) })
      return
    }

    if (pathname.includes('/knowledge-bases')) {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope({ records: [], total: 0, page: 0, size: 20 })) })
      return
    }

    await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope(null)) })
  })
}

/**
 * 模拟会话过期场景：首次 GET /users/me 成功（signIn 时），
 * 后续 GET /users/me 返回 401 触发会话过期处理。
 */
async function mockApisWithSessionExpiry(page: Page): Promise<void> {
  let meCount = 0
  await page.route('**/knowledge/api/v1/**', async (route) => {
    const request = route.request()
    const { pathname } = new URL(request.url())

    if (pathname.endsWith('/auth/login')) {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope({ token: 'e2e-runtime-token' })) })
      return
    }

    if (pathname.endsWith('/auth/logout')) {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope(null)) })
      return
    }

    if (pathname.endsWith('/users/me') && request.method() === 'GET') {
      meCount += 1
      if (meCount > 1) {
        await route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify(errorBody('UNAUTHORIZED', '会话已过期')) })
        return
      }
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope(defaultProfile)) })
      return
    }

    if (pathname.endsWith('/users/me') && request.method() === 'PATCH') {
      const body = JSON.parse(request.postData() ?? '{}')
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope({ ...defaultProfile, ...body })) })
      return
    }

    if (pathname.includes('/knowledge-bases')) {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope({ records: [], total: 0, page: 0, size: 20 })) })
      return
    }

    await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope(null)) })
  })
}

// --- 登录与导航辅助 ---

/** 通过 UI 执行邮箱密码登录并等待跳转到知识库列表。 */
async function signIn(page: Page): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('邮箱').fill('admin@example.com')
  await page.getByLabel('密码').fill('not-a-real-password')
  await page.getByRole('button', { name: '进入知识空间' }).click()
  await expect(page).toHaveURL(/\/knowledge-bases$/)
}

/** 在不触发整页刷新的前提下进行应用内导航（保持运行时会话）。 */
async function navigateWithinApp(page: Page, path: string): Promise<void> {
  const escaped = path.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  await page.evaluate((nextPath) => {
    window.history.pushState({}, '', nextPath)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }, path)
  await expect(page).toHaveURL(new RegExp(escaped + '$'))
}

// ================================================================
// 登录流程 E2E
// ================================================================

test('邮箱密码登录成功后跳转到知识库', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await mockApis(page)
  await signIn(page)
  await expect(page.getByRole('heading', { name: '我的知识库', exact: true })).toBeVisible()
})

test('邮箱或密码错误时显示统一错误且不暴露账号是否存在', async ({ page }) => {
  await mockApis(page, { status: 401, body: errorBody('AUTH_FAILED', '邮箱或密码不正确。') })
  await page.goto('/login')
  await page.getByLabel('邮箱').fill('unknown@example.com')
  await page.getByLabel('密码').fill('wrong-password')
  await page.getByRole('button', { name: '进入知识空间' }).click()
  await expect(page.getByRole('alert')).toHaveText('邮箱或密码不正确。')
  await expect(page).toHaveURL(/\/login$/)
})

test('登录频控时提示稍后重试', async ({ page }) => {
  await mockApis(page, { status: 429, body: errorBody('RATE_LIMITED', '登录尝试过于频繁。') })
  await page.goto('/login')
  await page.getByLabel('邮箱').fill('admin@example.com')
  await page.getByLabel('密码').fill('not-a-real-password')
  await page.getByRole('button', { name: '进入知识空间' }).click()
  await expect(page.getByRole('alert')).toHaveText('登录尝试过于频繁，请稍后再试。')
})

test('未登录访问受保护页面时跳转登录页并携带 redirect 参数', async ({ page }) => {
  await mockApis(page)
  await page.goto('/profile')
  await expect(page).toHaveURL(/\/login\?redirect=/)
})

test('已登录用户访问登录页时重定向到知识库', async ({ page }) => {
  await mockApis(page)
  await signIn(page)
  await page.evaluate(() => {
    window.history.pushState({}, '', '/login')
    window.dispatchEvent(new PopStateEvent('popstate'))
  })
  await expect(page).toHaveURL(/\/knowledge-bases$/)
})

test('会话过期后跳转登录页并提示', async ({ page }) => {
  await mockApisWithSessionExpiry(page)
  await signIn(page)
  // 导航到个人中心，第二次 GET /users/me 返回 401 触发会话过期
  await page.evaluate(() => {
    window.history.pushState({}, '', '/profile')
    window.dispatchEvent(new PopStateEvent('popstate'))
  })
  await expect(page).toHaveURL(/\/login\?expired=1$/)
  await expect(page.getByRole('alert')).toHaveText('会话已结束，请重新登录。')
})

// ================================================================
// 退出登录 E2E
// ================================================================

test('退出登录后跳转到登录页', async ({ page }) => {
  await mockApis(page)
  await signIn(page)
  // 悬停头像以展开账号菜单
  await page.getByRole('button', { name: '打开个人中心' }).hover()
  await page.getByRole('button', { name: '退出登录' }).click()
  await expect(page).toHaveURL(/\/login$/)
})

// ================================================================
// 个人中心 E2E
// ================================================================

test('个人中心展示本人只读邮箱且不含密码或令牌字段', async ({ page }) => {
  await mockApis(page)
  await signIn(page)
  await navigateWithinApp(page, '/profile')
  await expect(page.getByRole('heading', { name: '个人中心' })).toBeVisible()
  // 登录邮箱只读
  const emailInput = page.locator('input[type="email"][readonly]')
  await expect(emailInput).toHaveValue('admin@example.com')
  // 无密码输入框
  await expect(page.locator('input[type="password"]')).toHaveCount(0)
  // 无邮箱验证状态标识
  const bodyText = await page.locator('body').textContent()
  expect(bodyText ?? '').not.toMatch(/邮箱已验证|邮箱未验证|email.?verified/i)
})

test('个人中心编辑展示名并保存成功', async ({ page }) => {
  await mockApis(page)
  await signIn(page)
  await navigateWithinApp(page, '/profile')
  await expect(page.getByRole('heading', { name: '个人中心' })).toBeVisible()
  const nameInput = page.locator('input[autocomplete="nickname"]')
  await nameInput.fill('洪启阳')
  await page.getByRole('button', { name: '保存资料' }).click()
  await expect(page.getByRole('status')).toHaveText('个人资料已保存。')
})

// ================================================================
// 个人偏好设置 E2E
// ================================================================

test('个人偏好页面展示配置层级地图', async ({ page }) => {
  await mockApis(page)
  await signIn(page)
  await navigateWithinApp(page, '/settings/personal')
  await expect(page.getByRole('heading', { name: '个人偏好' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '配置在正确的层级发生' })).toBeVisible()
  const settingsMap = page.locator('.settings-map')
  await expect(settingsMap.getByText('个人偏好')).toBeVisible()
  await expect(settingsMap.getByText('知识库设置')).toBeVisible()
  await expect(settingsMap.getByText('模型设置')).toBeVisible()
})

test('切换主题后 data-theme 属性同步变化', async ({ page }) => {
  await mockApis(page)
  await signIn(page)
  await navigateWithinApp(page, '/settings/personal')
  await expect(page.getByRole('heading', { name: '个人偏好' })).toBeVisible()
  // 切换到暗色
  await page.locator('select').selectOption('dark')
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  // 切换到明亮
  await page.locator('select').selectOption('light')
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  // 切换到跟随系统
  await page.locator('select').selectOption('system')
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
})

test('保存默认分块偏好成功', async ({ page }) => {
  await mockApis(page)
  await signIn(page)
  await navigateWithinApp(page, '/settings/personal')
  await expect(page.getByRole('heading', { name: '个人偏好' })).toBeVisible()
  const sizeInput = page.locator('input[type="number"]').nth(0)
  await sizeInput.fill('1200')
  await page.getByRole('button', { name: '保存个人偏好' }).click()
  await expect(page.getByRole('status')).toContainText('个人偏好已保存')
})

test('分块重叠量不小于分块大小时提示错误', async ({ page }) => {
  await mockApis(page)
  await signIn(page)
  await navigateWithinApp(page, '/settings/personal')
  await expect(page.getByRole('heading', { name: '个人偏好' })).toBeVisible()
  const sizeInput = page.locator('input[type="number"]').nth(0)
  const overlapInput = page.locator('input[type="number"]').nth(1)
  await sizeInput.fill('500')
  await overlapInput.fill('600')
  await page.getByRole('button', { name: '保存个人偏好' }).click()
  await expect(page.getByRole('alert')).toHaveText('分块重叠量必须小于分块大小。')
})

// ================================================================
// 敏感信息门禁 E2E
// ================================================================

test('登录后浏览器存储只含 token，不含密码或摘要', async ({ page }) => {
  await mockApis(page)
  await signIn(page)
  const storage = await page.evaluate(() => ({
    lsKeys: Object.keys(localStorage),
    lsValues: Object.values(localStorage),
    ssKeys: Object.keys(sessionStorage),
    ssValues: Object.values(sessionStorage),
  }))
  const allKeys = storage.lsKeys.concat(storage.ssKeys).join(',')
  const allValues = storage.lsValues.concat(storage.ssValues).join(',')
  // token 持久化到 localStorage，值中包含运行时 token
  expect(allValues).toContain('e2e-runtime-token')
  // 不含密码或摘要相关键名
  expect(allKeys.toLowerCase()).not.toMatch(/password|hash|sa-token/)
  // 不含密码摘要值
  expect(allValues.toLowerCase()).not.toMatch(/passwordhash|sa-token/)
})

test('登录页不出现忘记密码或邮箱验证状态', async ({ page }) => {
  await mockApis(page)
  await page.goto('/login')
  const bodyText = await page.locator('body').textContent() ?? ''
  expect(bodyText).not.toMatch(/忘记密码|找回密码|邮箱验证|已验证|verified|sa-token/i)
  // 注册入口链接存在
  await expect(page.getByRole('link', { name: '注册新账号' })).toBeVisible()
})

// ================================================================
// 响应式 E2E
// ================================================================

test('移动端登录页键盘可操作且无横向溢出', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await mockApis(page)
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: '登录你的知识空间' })).toBeVisible()
  await page.keyboard.press('Tab')
  await expect(page.locator(':focus')).toBeVisible()
  expect(await page.locator('body').evaluate((el) => el.scrollWidth <= el.clientWidth)).toBe(true)
})

test('移动端个人中心无横向溢出', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await mockApis(page)
  await signIn(page)
  await navigateWithinApp(page, '/profile')
  await expect(page.getByRole('heading', { name: '个人中心' })).toBeVisible()
  expect(await page.locator('body').evaluate((el) => el.scrollWidth <= el.clientWidth)).toBe(true)
})

// ================================================================
// 三主题 E2E
// ================================================================

test('暗色主题下个人中心保持视觉一致', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.emulateMedia({ colorScheme: 'light', reducedMotion: 'reduce' })
  await mockApis(page)
  await signIn(page)
  // 默认 system -> 点击 1 次 -> light -> 点击 2 次 -> dark
  const themeButton = page.getByRole('button', { name: /切换主题/ })
  await themeButton.click()
  await themeButton.click()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await navigateWithinApp(page, '/profile')
  await expect(page.getByRole('heading', { name: '个人中心' })).toBeVisible()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
})
