import { config } from '@vue/test-utils'
import { vi, beforeEach } from 'vitest'
import Antd, { message } from 'ant-design-vue'

// 全局注册 ant-design-vue，使测试中使用的 antd 组件可正确渲染
config.global.plugins = [Antd]

// Mock message API，避免测试中在 document.body 产生残留 DOM
vi.spyOn(message, 'success').mockImplementation((): any => vi.fn())
vi.spyOn(message, 'error').mockImplementation((): any => vi.fn())
vi.spyOn(message, 'warning').mockImplementation((): any => vi.fn())
vi.spyOn(message, 'info').mockImplementation((): any => vi.fn())

beforeEach(() => {
  vi.mocked(message.success).mockClear()
  vi.mocked(message.error).mockClear()
  vi.mocked(message.warning).mockClear()
  vi.mocked(message.info).mockClear()
  document.body.innerHTML = ''
})
