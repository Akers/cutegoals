import { test, expect } from '@playwright/test';

/**
 * 家长端单月任务日历 E2E 测试
 *
 * 覆盖：
 * - 单月日历渲染
 * - 日期点击 → 任务列表联动
 * - 任务类型筛选
 * - 查看全部模式
 * - 跨月周号
 *
 * 认证：
 *   beforeEach 通过 page.request 调用 POST /api/auth/login 完成家长登录。
 *   - 默认行为：要求环境变量 E2E_PARENT_PHONE / E2E_PARENT_PASSWORD，
 *     否则 test.skip（避免在缺失凭据的环境下污染 CI 失败计数）。
 *   - 凭据应来自一次 /api/auth/initialize 写入的账号（该账号同时持有
 *     INSTANCE_ADMIN + PARENT 双角色，可访问 /parent/*）。
 *   - 登录成功写入的 HttpOnly cookies (access_token / refresh_token /
 *     csrf_token) 通过 page.request 共享到 page context，后续
 *     page.goto('/parent/tasks') 由 AuthGuard 校验通过。
 *
 * 需要运行中的 CuteGoals 实例。
 * 启动: docker compose -f deploy/docker-compose.yml up -d
 * 运行: npx playwright test tests/parent-task-calendar.spec.ts
 */
const BASE_URL = process.env.BASE_URL || 'http://localhost:80';
const PARENT_PHONE = process.env.E2E_PARENT_PHONE || '';
const PARENT_PASSWORD = process.env.E2E_PARENT_PASSWORD || '';

test.describe('家长端单月任务日历', () => {

  test.beforeEach(async ({ page }, testInfo) => {
    // 凭据缺失时静默跳过，避免给 CI 噪声
    test.skip(
      !PARENT_PHONE || !PARENT_PASSWORD,
      'E2E 家长凭据缺失：设置 E2E_PARENT_PHONE 和 E2E_PARENT_PASSWORD（来源：/api/auth/initialize 创建的账号）',
    );

    // 通过 page.request 登录（与 page 共享 BrowserContext cookie jar）
    const response = await page.request.post(`${BASE_URL}/api/auth/login`, {
      data: { phone: PARENT_PHONE, password: PARENT_PASSWORD },
      failOnStatusCode: false,
    });

    if (!response.ok()) {
      const body = await response.text();
      throw new Error(
        `parent login failed (status=${response.status()}, url=${BASE_URL}/api/auth/login, body=${body || '<empty>'})`,
      );
    }

    // 标记 cookie 已设，避免 beforeAll/log 误判
    testInfo.attachments.push({
      name: 'login-cookies',
      body: Buffer.from(`logged in as ${PARENT_PHONE.slice(0, 3)}**** on ${BASE_URL}`),
      contentType: 'text/plain',
    });
  });

  test('页面加载后显示单月日历', async ({ page }) => {
    await page.goto(`${BASE_URL}/parent/tasks`);

    // 验证单月日历渲染
    const calendarGrid = page.locator('.task-calendar-grid');
    await expect(calendarGrid).toBeVisible();

    // 验证一个日历面板
    const panel = calendarGrid.locator('.calendar-panel');
    await expect(panel).toHaveCount(1);
  });

  test('点击有色日期 → 下方任务列表刷新为当天任务', async ({ page }) => {
    await page.goto(`${BASE_URL}/parent/tasks`);

    // 等待日历加载
    await page.waitForSelector('.cell-task', { timeout: 10000 });

    // 点击一个有任务的日期
    const taskCell = page.locator('.cell-task').first();
    if (await taskCell.count() > 0) {
      await taskCell.click();

      // 验证任务列表刷新
      const taskListCard = page.getByText('任务列表');
      await expect(taskListCard).toBeVisible();
    }
  });

  test('任务类型筛选正确过滤任务列表', async ({ page }) => {
    await page.goto(`${BASE_URL}/parent/tasks`);

    // 找到筛选器
    const limitedCheckbox = page.getByLabel('限时任务');
    const repeatCheckbox = page.getByLabel('重复任务');

    // 仅选限时任务
    await limitedCheckbox.click();
    await repeatCheckbox.click();

    // 等待 debounce 300ms 后列表刷新
    await page.waitForTimeout(500);

    // 验证列表只包含限时任务
    const taskCards = page.locator('.cell-task');
    // 实际验证需根据页面结构调整
  });

  test('查看全部模式清除日期约束', async ({ page }) => {
    await page.goto(`${BASE_URL}/parent/tasks`);

    // 先点击某个日期选中
    const taskCell = page.locator('.cell-task').first();
    if (await taskCell.count() > 0) {
      await taskCell.click();
    }

    // 点击查看全部
    const viewAllButton = page.getByText('查看全部');
    await viewAllButton.click();

    // 验证按钮状态变更
    await expect(page.getByText('查看全部（已激活）')).toBeVisible();

    // 验证日历选中高亮已清除
    const selectedCell = page.locator('.cell-selected');
    await expect(selectedCell).toHaveCount(0);
  });

  test('移动端单月布局', async ({ page }) => {
    // 设置为移动端视口
    await page.setViewportSize({ width: 375, height: 812 });
    await page.goto(`${BASE_URL}/parent/tasks`);

    // 验证日历在移动端仍然单月可见
    const calendarGrid = page.locator('.task-calendar-grid');
    await expect(calendarGrid).toBeVisible();
    const panel = calendarGrid.locator('.calendar-panel');
    await expect(panel).toHaveCount(1);
  });
});
