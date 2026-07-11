import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright E2E test configuration for CuteGoals 2.0.
 *
 * Target environments:
 *   - CI: chromium headless
 *   - Local: chromium, firefox, webkit (desktop + mobile)
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
    baseURL: process.env.BASE_URL || 'http://localhost:80',
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
  // Local dev server (only used when running locally)
  webServer: process.env.CI ? undefined : {
    command: 'cd ../web && npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 120000,
  },
});
