import { expect, type Page, test } from '@playwright/test'

function envelope(data: unknown): object {
  return { success: true, code: 'SUCCESS', message: 'success', data }
}

const document = {
  documentKey: 'doc-e2e',
  knowledgeBaseKey: 'kb-e2e',
  displayName: '贷款产品准入政策.pdf',
  currentFile: { filename: '贷款产品准入政策.pdf', contentType: 'application/pdf', size: 240000 },
  currentFileToken: 'opaque-file-token',
  parseStatus: 'SUCCEEDED',
  publishStatus: 'PUBLISHED',
  hasActivePublication: true,
  latestTask: { taskKey: 'task-e2e', taskType: 'PARSE', status: 'SUCCEEDED', progress: 100, attempt: 1, maxAttempts: 3, updated: '2026-08-05T12:00:00Z' },
  updated: '2026-08-05T12:00:00Z',
}

async function mockLifecycleApis(page: Page, rechunkRequests: string[]): Promise<void> {
  await page.route('**/knowledge/api/v1/**', async (route) => {
    const request = route.request()
    const { pathname } = new URL(request.url())
    let data: unknown = null
    if (pathname.endsWith('/auth/login')) data = { token: 'e2e-runtime-token' }
    else if (pathname.endsWith('/users/me')) data = { userKey: 'user-e2e', email: 'admin@example.com', displayName: '文档管理员', avatarUrl: '', preferenceJson: '{}' }
    else if (pathname.endsWith('/workspaces/ws-e2e/knowledge-bases')) data = { records: [{ knowledgeBaseKey: 'kb-e2e', name: '产品知识库', description: '产品规则与流程', autoParse: true, autoPublish: false, updated: document.updated }], total: 1, page: 0, size: 20 }
    else if (pathname.endsWith('/knowledge-bases/kb-e2e/documents')) data = { records: [document], total: 1, page: 0, size: 20 }
    else if (pathname.endsWith('/documents/doc-e2e/chunks')) data = { hasChunk: true, currentChunkRevisionKey: 'opaque-chunk-token', chunkProfile: { strategy: 'PARENT_CHILD' }, parentCount: 3, childCount: 9, total: 12 }
    else if (pathname.endsWith('/documents/doc-e2e/parse-preview')) data = { hasParse: true, parserProfile: 'DEFAULT', blockCount: 18 }
    else if (pathname.endsWith('/documents/doc-e2e/rechunk')) {
      rechunkRequests.push(request.postData() ?? '')
      data = { taskKey: 'rechunk-task-e2e' }
    } else if (pathname.endsWith('/documents/doc-e2e')) data = document
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope(data)) })
  })
}

async function signIn(page: Page): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('邮箱').fill('admin@example.com')
  await page.getByLabel('密码').fill('not-a-real-password')
  await page.getByRole('button', { name: '进入知识空间' }).click()
  await expect(page).toHaveURL(/\/knowledge-bases$/)
}

async function navigateWithinApp(page: Page, path: string): Promise<void> {
  await page.evaluate((nextPath) => {
    window.history.pushState({}, '', nextPath)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }, path)
  await expect(page).toHaveURL(new RegExp(`${path}$`))
}

test('administrator traverses current-file lifecycle with keyboard-visible controls', async ({ page }) => {
  const rechunkRequests: string[] = []
  await page.setViewportSize({ width: 1366, height: 900 })
  await mockLifecycleApis(page, rechunkRequests)
  await signIn(page)

  await navigateWithinApp(page, '/knowledge-bases/kb-e2e/documents')
  await expect(page.getByRole('heading', { name: '文档工作台' })).toBeVisible()
  await page.getByRole('button', { name: /贷款产品准入政策/ }).click()
  await expect(page.getByRole('heading', { name: '贷款产品准入政策.pdf' })).toBeVisible()
  await expect(page.getByText('12 个分块')).toBeVisible()
  await expect(page.getByText('已进入检索')).toBeVisible()

  const forbiddenActions = page.getByRole('button', { name: /版本|回退|撤回/ })
  await expect(forbiddenActions).toHaveCount(0)

  await page.getByRole('button', { name: '重新分块' }).focus()
  await expect(page.getByRole('button', { name: '重新分块' })).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(page.getByRole('heading', { name: '确认重新分块？' })).toBeVisible()
  await expect(page.getByText('将删除当前解析结果所对应的既有分块')).toBeVisible()

  await page.locator('[data-test="cancel-rechunk"]').click()
  expect(rechunkRequests).toHaveLength(0)
  await expect(page.getByRole('heading', { name: '确认重新分块？' })).toHaveCount(0)

  await page.getByRole('button', { name: '重新分块' }).click()
  await page.locator('[data-test="confirm-rechunk"]').click()
  await expect.poll(() => rechunkRequests.length).toBe(1)
  expect(JSON.parse(rechunkRequests[0])).toEqual({ confirmed: true, expectedChunkRevisionKey: 'opaque-chunk-token', chunkProfile: { strategy: 'PARENT_CHILD' } })
})

test('mobile document page has no horizontal overflow and preserves state text', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await mockLifecycleApis(page, [])
  await signIn(page)
  await navigateWithinApp(page, '/knowledge-bases/kb-e2e/documents/doc-e2e')
  await expect(page.getByRole('heading', { name: '贷款产品准入政策.pdf' })).toBeVisible()
  expect(await page.locator('body').evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true)
  await expect(page.getByText('解析：已完成')).toBeVisible()
  await expect(page.getByText('发布：已发布')).toBeVisible()
})

for (const { mode, system } of [{ mode: 'light', system: 'light' as const }, { mode: 'dark', system: 'dark' as const }, { mode: 'system', system: 'dark' as const }]) {
  test(`document lifecycle page is consistent in ${mode} theme`, async ({ page }) => {
    await page.setViewportSize({ width: 1366, height: 900 })
    await page.emulateMedia({ colorScheme: system, reducedMotion: 'reduce' })
    await mockLifecycleApis(page, [])
    await signIn(page)
    await navigateWithinApp(page, '/knowledge-bases/kb-e2e/documents/doc-e2e')
    await expect(page.getByRole('heading', { name: '贷款产品准入政策.pdf' })).toBeVisible()
    // 三主题下状态文本一致
    await expect(page.getByText('解析：已完成')).toBeVisible()
    await expect(page.getByText('发布：已发布')).toBeVisible()
    // 无水平溢出
    expect(await page.locator('body').evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true)
    // 无版本/回退/撤回按钮
    const forbidden = page.getByRole('button', { name: /版本|回退|撤回|rollback|withdraw|revert/i })
    await expect(forbidden).toHaveCount(0)
  })
}
