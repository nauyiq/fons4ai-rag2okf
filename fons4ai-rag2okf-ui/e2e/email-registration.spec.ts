import { expect, type Page, test } from '@playwright/test'

// --- 响应封装与 Mock 辅助 ---

function envelope(data: unknown): object {
  return { success: true, code: 'SUCCESS', message: 'success', data }
}

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

type RegistrationMock = { status: number; body: object }

/**
 * 拦截全部 /knowledge/api/v1/ 请求并返回 Mock 响应。
 * registrationMock 不为空时注册接口返回指定错误状态。
 */
async function mockApis(page: Page, registrationMock?: RegistrationMock): Promise<void> {
  await page.route('**/knowledge/api/v1/**', async (route) => {
    const request = route.request()
    const { pathname } = new URL(request.url())

    if (pathname.endsWith('/auth/registration')) {
      if (registrationMock) {
        await route.fulfill({
          status: registrationMock.status,
          contentType: 'application/json',
          body: JSON.stringify(registrationMock.body),
        })
        return
      }
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(envelope({ token: 'e2e-registration-token' })),
      })
      return
    }

    if (pathname.endsWith('/auth/login')) {
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(envelope({ token: 'e2e-runtime-token' })),
      })
      return
    }

    if (pathname.endsWith('/auth/logout')) {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope(null)) })
      return
    }

    if (pathname.endsWith('/users/me')) {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope(defaultProfile)) })
      return
    }

    if (pathname.includes('/knowledge-bases')) {
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(envelope({ records: [], total: 0, page: 0, size: 20 })),
      })
      return
    }

    await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope(null)) })
  })
}

test.describe('邮箱注册 E2E（T029）', () => {
  test('注册成功后进入知识空间', async ({ page }) => {
    await mockApis(page)
    await page.goto('/register')

    await page.getByPlaceholder('name@example.com').fill('new@example.com')
    await page.getByPlaceholder('8～64 位密码').fill('secure-pass')
    await page.getByPlaceholder('再次输入密码').fill('secure-pass')
    await page.getByPlaceholder('输入你的展示名称').fill('新用户')
    await page.getByRole('button', { name: '创建账号' }).click()

    await expect(page).toHaveURL(/\/knowledge-bases/)
  })

  test('注册冲突时显示安全化错误，不暴露邮箱是否已注册', async ({ page }) => {
    await mockApis(page, {
      status: 400,
      body: errorBody('PARAMS_ERROR', '参数错误'),
    })
    await page.goto('/register')

    await page.getByPlaceholder('name@example.com').fill('existing@example.com')
    await page.getByPlaceholder('8～64 位密码').fill('secure-pass')
    await page.getByPlaceholder('再次输入密码').fill('secure-pass')
    await page.getByRole('button', { name: '创建账号' }).click()

    // 安全化错误提示
    await expect(page.locator('.inline-error')).toBeVisible()
    await expect(page.locator('.inline-error')).toContainText('注册失败')

    // 不暴露输入的邮箱
    const errorText = await page.locator('.inline-error').textContent()
    expect(errorText).not.toContain('existing@example.com')
    expect(errorText).not.toMatch(/已注册|已存在|already/i)
  })

  test('注册频控时提示稍后重试', async ({ page }) => {
    await mockApis(page, {
      status: 429,
      body: errorBody('TOO_MANY_REQUEST', '请求过多'),
    })
    await page.goto('/register')

    await page.getByPlaceholder('name@example.com').fill('new@example.com')
    await page.getByPlaceholder('8～64 位密码').fill('secure-pass')
    await page.getByPlaceholder('再次输入密码').fill('secure-pass')
    await page.getByRole('button', { name: '创建账号' }).click()

    await expect(page.locator('.inline-error')).toContainText('过于频繁')
  })

  test('系统暗色模式下注册页可正常操作', async ({ page }) => {
    await mockApis(page)
    await page.emulateMedia({ colorScheme: 'dark' })
    await page.goto('/register')

    await page.getByPlaceholder('name@example.com').fill('new@example.com')
    await page.getByPlaceholder('8～64 位密码').fill('secure-pass')
    await page.getByPlaceholder('再次输入密码').fill('secure-pass')
    await page.getByRole('button', { name: '创建账号' }).click()

    await expect(page).toHaveURL(/\/knowledge-bases/)
  })

  test('注册页不展示邮箱验证状态和密码找回入口', async ({ page }) => {
    await mockApis(page)
    await page.goto('/register')

    const bodyText = await page.locator('body').textContent()
    // 不展示邮箱验证状态
    expect(bodyText).not.toMatch(/邮箱已验证|邮箱未验证|email.?verified/i)
    // 不展示密码找回入口
    expect(bodyText).not.toMatch(/忘记密码|找回密码|重置密码|reset.?password|forgot.?password/i)
    // 不展示邮件发送文案
    expect(bodyText).not.toMatch(/验证邮件|发送邮件|check.?your.?email/i)
  })

  test('注册页可从登录页导航到达', async ({ page }) => {
    await mockApis(page)
    await page.goto('/login')

    // 点击注册链接
    await page.getByRole('link', { name: /注册/ }).click()
    await expect(page).toHaveURL(/\/register/)
  })
})
