import { defineConfig } from '@playwright/test'

const managedWebServer = process.env.PLAYWRIGHT_SKIP_WEBSERVER
  ? undefined
  : {
      command: 'npm run dev -- --port 4173',
      url: 'http://127.0.0.1:4173',
      reuseExistingServer: true,
      timeout: 20_000,
      env: {
        VITE_RAG2OKF_WORKSPACE_KEY: 'ws-e2e',
        VITE_RAG2OKF_WORKSPACE_NAME: '质量验证空间',
        VITE_RAG2OKF_WORKSPACE_ROLE: 'ADMIN',
        VITE_RAG2OKF_API_BASE_URL: '/knowledge/api/v1',
      },
    }

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  reporter: 'line',
  expect: { timeout: 8_000 },
  use: {
    baseURL: 'http://127.0.0.1:4173',
    colorScheme: 'light',
    locale: 'zh-CN',
    reducedMotion: 'reduce',
    screenshot: 'only-on-failure',
  },
  webServer: managedWebServer,
})
