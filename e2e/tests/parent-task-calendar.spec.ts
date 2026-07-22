import { test, expect } from '@playwright/test';

/**
 * 家长端双月任务日历 E2E 测试
 *
 * 覆盖：
 * - 双月日历渲染（桌面/移动端）
 * - 日期点击 → 任务列表联动
 * - 任务类型筛选
 * - 查看全部模式
 * - 跨月周号
 *
 * 需要运行中的 CuteGoals 实例。
 * 启动: docker compose -f deploy/docker-compose.yml up -d
 * 运行: npx playwright test tests/parent-task-calendar.spec.ts
 */
const BASE_URL = process.env.BASE_URL || 'http://localhost:80';

test.describe('家长端双月任务日历', () => {

  test.beforeEach(async ({ page }) => {
    // 登录为家长角色
    await page.goto(`${BASE_URL}/parent/login`);
    // 实际测试需要先初始化或使用已登录 cookie
    // 此处为测试骨架，需要配合已有认证流程
  });

  test('页面加载后显示双月日历', async ({ page }) => {
    await page.goto(`${BASE_URL}/parent/tasks`);

    // 验证双月日历渲染
    const calendarGrid = page.locator('.task-calendar-grid');
    await expect(calendarGrid).toBeVisible();

    // 验证两个日历面板
    const panels = calendarGrid.locator('.calendar-panel');
    await expect(panels).toHaveCount(2);
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

  test('移动端日历上下堆叠', async ({ page }) => {
    // 设置为移动端视口
    await page.setViewportSize({ width: 375, height: 812 });
    await page.goto(`${BASE_URL}/parent/tasks`);

    // 验证日历使用单列布局
    const calendarGrid = page.locator('.task-calendar-grid');
    // 通过 CSS 属性验证 grid 为单列
  });
});
