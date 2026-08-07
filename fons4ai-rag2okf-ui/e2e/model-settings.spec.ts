import { expect, type Page, test } from '@playwright/test'

function envelope(data: unknown): object {
  return { success: true, code: 'SUCCESS', message: 'success', data }
}

const defaultProfile = {
  userKey: 'user-e2e',
  email: 'admin@example.com',
  displayName: '文档管理员',
  avatarUrl: '',
  preferenceJson: '{}',
}

const templates = [
  { code: 'ALIYUN_DASHSCOPE', providerName: '阿里云百炼', defaultBaseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1' },
  { code: 'VOLCENGINE_ARK', providerName: '火山方舟', defaultBaseUrl: 'https://ark.cn-beijing.volces.com/api/v3' },
  { code: 'TENCENT_HUNYUAN', providerName: '腾讯混元', defaultBaseUrl: 'https://api.hunyuan.cloud.tencent.com/v1' },
  { code: 'ZHIPU_BIGMODEL', providerName: '智谱 BigModel', defaultBaseUrl: 'https://open.bigmodel.cn/api/paas/v4' },
  { code: 'CUSTOM', providerName: '自定义', defaultBaseUrl: null },
]

const connections = [
  { connectionKey: 'conn-1', providerCode: 'ALIYUN_DASHSCOPE', providerName: '阿里云百炼', displayName: '阿里云百炼', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', apiKeyMask: '····7K2M', status: 'ACTIVE', lastTestStatus: 'SUCCESS', lastTestAt: '2026-08-06T10:00:00Z' },
]

const profiles = [
  { profileKey: 'prof-1', connectionKey: 'conn-1', modelName: 'qwen-plus', modelType: 'CHAT', dimensions: null, timeoutSeconds: 60, temperature: null, status: 'ACTIVE', lastTestStatus: 'SUCCESS', lastTestAt: '2026-08-06T10:00:00Z' },
  { profileKey: 'prof-2', connectionKey: 'conn-1', modelName: 'text-embedding-v3', modelType: 'EMBEDDING', dimensions: 1024, timeoutSeconds: 60, temperature: null, status: 'ACTIVE', lastTestStatus: 'PENDING', lastTestAt: null },
]

async function mockApis(page: Page): Promise<void> {
  await page.route('**/knowledge/api/v1/**', async (route) => {
    const request = route.request()
    const { pathname } = new URL(request.url())

    if (pathname.endsWith('/auth/login')) {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope({ token: 'e2e-runtime-token' })) })
      return
    }

    if (pathname.endsWith('/users/me')) {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope(defaultProfile)) })
      return
    }

    if (pathname.endsWith('/auth/logout')) {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope(null)) })
      return
    }

    if (pathname.endsWith('/model-provider-templates') && request.method() === 'GET') {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope(templates)) })
      return
    }

    if (pathname.endsWith('/model-connections') && request.method() === 'GET') {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope(connections)) })
      return
    }

    if (pathname.endsWith('/model-connections') && request.method() === 'POST') {
      const body = JSON.parse(request.postData() ?? '{}')
      const newConn = { ...connections[0], connectionKey: 'conn-new', displayName: body.displayName ?? '新连接', providerCode: body.templateCode ?? 'CUSTOM', apiKeyMask: '····NEW1' }
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope(newConn)) })
      return
    }

    if (pathname.match(/\/model-connections\/[^/]+$/) && request.method() === 'PATCH') {
      const body = JSON.parse(request.postData() ?? '{}')
      const updated = { ...connections[0], apiKeyMask: body.apiKey ? '····9X3Z' : connections[0].apiKeyMask, baseUrl: body.baseUrl ?? connections[0].baseUrl, displayName: body.displayName ?? connections[0].displayName }
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope(updated)) })
      return
    }

    if (pathname.endsWith('/model-profiles') && request.method() === 'GET') {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope(profiles)) })
      return
    }

    if (pathname.endsWith('/model-profiles') && request.method() === 'POST') {
      const body = JSON.parse(request.postData() ?? '{}')
      const newProfile = { ...profiles[0], profileKey: 'prof-new', modelName: body.modelName ?? '新模型', modelType: body.modelType ?? 'CHAT' }
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope(newProfile)) })
      return
    }

    if (pathname.match(/\/model-profiles\/[^/]+\/test$/) && request.method() === 'POST') {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope({ status: 'SUCCESS', errorCode: null, dimensions: null })) })
      return
    }

    if (pathname.includes('/knowledge-bases')) {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope({ records: [], total: 0, page: 0, size: 20 })) })
      return
    }

    await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope(null)) })
  })
}

async function signIn(page: Page): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('邮箱').fill('admin@example.com')
  await page.getByLabel('密码').fill('not-a-real-password')
  await page.getByRole('button', { name: '进入知识空间' }).click()
  await expect(page).toHaveURL(/\/knowledge-bases$/)
}

test.describe('模型设置 E2E（T035）', () => {
  test('展示连接列表和模型档案', async ({ page }) => {
    await mockApis(page)
    await signIn(page)
    await page.goto('/settings/models')

    await expect(page.locator('.model-row').filter({ hasText: '阿里云百炼' })).toBeVisible()
    await expect(page.locator('.profile-row').filter({ hasText: 'qwen-plus' })).toBeVisible()
    await expect(page.locator('.profile-row').filter({ hasText: 'text-embedding-v3' })).toBeVisible()
  })

  test('展示 API Key 掩码且无显示原值按钮', async ({ page }) => {
    await mockApis(page)
    await signIn(page)
    await page.goto('/settings/models')

    await expect(page.getByText('····7K2M').first()).toBeVisible()
    const bodyText = await page.locator('body').textContent()
    expect(bodyText).not.toMatch(/显示.*原.*值|查看.*原.*Key|复制.*Key/i)
  })

  test('展示测试费用提示', async ({ page }) => {
    await mockApis(page)
    await signIn(page)
    await page.goto('/settings/models')

    await expect(page.getByText('会向模型厂商发送固定测试文本，可能产生少量费用')).toBeVisible()
  })

  test('添加连接时展示五类模板并预填', async ({ page }) => {
    await mockApis(page)
    await signIn(page)
    await page.goto('/settings/models')

    await page.locator('header.page-heading').getByRole('button', { name: '添加连接' }).click()
    const drawer = page.locator('.settings-drawer')
    await expect(drawer).toBeVisible()

    const options = drawer.locator('option')
    await expect(options).toHaveCount(5)

    // 选择火山方舟，预填厂商名和 Base URL
    await drawer.locator('select').selectOption('VOLCENGINE_ARK')
    const inputs = drawer.locator('input')
    await expect(inputs.nth(0)).toHaveValue('火山方舟')
    await expect(inputs.nth(2)).toHaveValue('https://ark.cn-beijing.volces.com/api/v3')
  })

  test('自定义模板 Base URL 为空', async ({ page }) => {
    await mockApis(page)
    await signIn(page)
    await page.goto('/settings/models')

    await page.locator('header.page-heading').getByRole('button', { name: '添加连接' }).click()
    await page.locator('.settings-drawer select').selectOption('CUSTOM')
    const urlInput = page.locator('.settings-drawer input[type="url"]')
    await expect(urlInput).toHaveValue('')
  })

  test('替换 Key 后展示新掩码', async ({ page }) => {
    await mockApis(page)
    await signIn(page)
    await page.goto('/settings/models')

    await page.getByRole('button', { name: '替换 Key' }).click()
    await page.locator('.inline-form input[type="password"]').fill('new-secret-key')
    await page.locator('.inline-form button').click()

    await expect(page.getByText('····9X3Z').first()).toBeVisible()
  })

  test('测试模型后展示安全化结果', async ({ page }) => {
    await mockApis(page)
    await signIn(page)
    await page.goto('/settings/models')

    await page.locator('.profile-row').first().getByRole('button', { name: '测试模型' }).click()
    await expect(page.locator('.test-result')).toContainText('成功')
  })

  test('页面不展示技术名词和内部异常', async ({ page }) => {
    await mockApis(page)
    await signIn(page)
    await page.goto('/settings/models')

    const bodyText = await page.locator('body').textContent() ?? ''
    expect(bodyText).not.toMatch(/sa-token|redis|mybatis|datasource|exception|stacktrace/i)
  })

  test('暗色模式下页面可操作', async ({ page }) => {
    await mockApis(page)
    await page.emulateMedia({ colorScheme: 'dark' })
    await signIn(page)
    await page.goto('/settings/models')

    await expect(page.locator('.model-row').first()).toBeVisible()
    await page.locator('.profile-row').first().getByRole('button', { name: '测试模型' }).click()
    await expect(page.locator('.test-result')).toContainText('成功')
  })
})
