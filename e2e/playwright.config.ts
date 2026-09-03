import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { defineConfig, devices } from '@playwright/test';

const configDir = resolve(fileURLToPath(import.meta.url), '..');

/**
 * Playwright E2E test configuration for CuteGoals 2.0.
 *
 * Target environments:
 *   - CI: chromium headless (BASE_URL 指向完整栈网关，例如 http://localhost:80)
 *   - Local: chromium, firefox, webkit (desktop + mobile)
 *
 * 本地默认目标是 web/apps/console（Vue3 + Naive UI）的 dev server (http://localhost:8000)，
 * /api 由 vite 代理到 http://localhost:8080 后端。
 * 涉及 /child/*（孩子端独立应用，8001）或网关行为的用例需要显式设置 BASE_URL
 * 指向完整栈（docker compose 网关），否则相关用例会自动跳过。
 *
 * Tests cover:
 *   - 三角色权限隔离 (admin/parent/child)
 *   - 越权访问防护 (403/404)
 *   - 资源不泄露验证
 *   - 账号停用后立即失效
 *   - 路由守卫与重定向
 *
 * Task 9.5: 三角色权限与 E2E 测试
 */
export default defineConfig({
  testDir: './tests',
  timeout: 30000,
  expect: {
    timeout: 10000,
  },
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [
    ['html', { outputFolder: 'playwright-report' }],
    ['json', { outputFile: 'test-results.json' }],
    ['list'],
  ],
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:8000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium-desktop',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1280, height: 720 },
      },
    },
    {
      name: 'firefox-desktop',
      use: {
        ...devices['Desktop Firefox'],
        viewport: { width: 1280, height: 720 },
      },
    },
    {
      name: 'chromium-mobile',
      use: {
        ...devices['Pixel 5'],
      },
    },
    // CI-only: headless chromium for fast feedback
    {
      name: 'chromium-ci',
      use: {
        ...devices['Desktop Chrome'],
        headless: true,
      },
      testMatch: /.*\.ci\.spec\.ts/,
    },
  ],
  // Local dev server (only used when running locally):
  // start the Vue console app via the workspace script (replaces the legacy
  // `npm run dev` which did not exist at the web/ root).
  webServer: process.env.CI ? undefined : {
    command: 'pnpm run dev:console',
    url: 'http://localhost:8000',
    cwd: resolve(configDir, '../web'),
    reuseExistingServer: !process.env.CI,
    timeout: 120000,
  },
});
