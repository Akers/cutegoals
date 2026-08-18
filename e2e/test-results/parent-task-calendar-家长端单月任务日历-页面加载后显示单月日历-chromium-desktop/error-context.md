# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: parent-task-calendar.spec.ts >> 家长端单月任务日历 >> 页面加载后显示单月日历
- Location: tests/parent-task-calendar.spec.ts:28:3

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: locator('.task-calendar-grid')
Expected: visible
Timeout: 10000ms
Error: element(s) not found

Call log:
  - Expect "toBeVisible" with timeout 10000ms
  - waiting for locator('.task-calendar-grid')

```

```yaml
- heading "家长登录" [level=1]
- paragraph: 使用手机号与密码登录
- text: 手机号
- img "mobile"
- textbox "手机号":
  - /placeholder: 11 位手机号
- text: 密码
- img "lock"
- textbox "密码":
  - /placeholder: 请输入密码
- button "登 录"
- paragraph: 首次部署请使用初始化向导
```

# Test source

```ts
  1   | import { test, expect } from '@playwright/test';
  2   | 
  3   | /**
  4   |  * 家长端单月任务日历 E2E 测试
  5   |  *
  6   |  * 覆盖：
  7   |  * - 单月日历渲染
  8   |  * - 日期点击 → 任务列表联动
  9   |  * - 任务类型筛选
  10  |  * - 查看全部模式
  11  |  * - 跨月周号
  12  |  *
  13  |  * 需要运行中的 CuteGoals 实例。
  14  |  * 启动: docker compose -f deploy/docker-compose.yml up -d
  15  |  * 运行: npx playwright test tests/parent-task-calendar.spec.ts
  16  |  */
  17  | const BASE_URL = process.env.BASE_URL || 'http://localhost:80';
  18  | 
  19  | test.describe('家长端单月任务日历', () => {
  20  | 
  21  |   test.beforeEach(async ({ page }) => {
  22  |     // 登录为家长角色
  23  |     await page.goto(`${BASE_URL}/parent/login`);
  24  |     // 实际测试需要先初始化或使用已登录 cookie
  25  |     // 此处为测试骨架，需要配合已有认证流程
  26  |   });
  27  | 
  28  |   test('页面加载后显示单月日历', async ({ page }) => {
  29  |     await page.goto(`${BASE_URL}/parent/tasks`);
  30  | 
  31  |     // 验证单月日历渲染
  32  |     const calendarGrid = page.locator('.task-calendar-grid');
> 33  |     await expect(calendarGrid).toBeVisible();
      |                                ^ Error: expect(locator).toBeVisible() failed
  34  | 
  35  |     // 验证一个日历面板
  36  |     const panel = calendarGrid.locator('.calendar-panel');
  37  |     await expect(panel).toHaveCount(1);
  38  |   });
  39  | 
  40  |   test('点击有色日期 → 下方任务列表刷新为当天任务', async ({ page }) => {
  41  |     await page.goto(`${BASE_URL}/parent/tasks`);
  42  | 
  43  |     // 等待日历加载
  44  |     await page.waitForSelector('.cell-task', { timeout: 10000 });
  45  | 
  46  |     // 点击一个有任务的日期
  47  |     const taskCell = page.locator('.cell-task').first();
  48  |     if (await taskCell.count() > 0) {
  49  |       await taskCell.click();
  50  | 
  51  |       // 验证任务列表刷新
  52  |       const taskListCard = page.getByText('任务列表');
  53  |       await expect(taskListCard).toBeVisible();
  54  |     }
  55  |   });
  56  | 
  57  |   test('任务类型筛选正确过滤任务列表', async ({ page }) => {
  58  |     await page.goto(`${BASE_URL}/parent/tasks`);
  59  | 
  60  |     // 找到筛选器
  61  |     const limitedCheckbox = page.getByLabel('限时任务');
  62  |     const repeatCheckbox = page.getByLabel('重复任务');
  63  | 
  64  |     // 仅选限时任务
  65  |     await limitedCheckbox.click();
  66  |     await repeatCheckbox.click();
  67  | 
  68  |     // 等待 debounce 300ms 后列表刷新
  69  |     await page.waitForTimeout(500);
  70  | 
  71  |     // 验证列表只包含限时任务
  72  |     const taskCards = page.locator('.cell-task');
  73  |     // 实际验证需根据页面结构调整
  74  |   });
  75  | 
  76  |   test('查看全部模式清除日期约束', async ({ page }) => {
  77  |     await page.goto(`${BASE_URL}/parent/tasks`);
  78  | 
  79  |     // 先点击某个日期选中
  80  |     const taskCell = page.locator('.cell-task').first();
  81  |     if (await taskCell.count() > 0) {
  82  |       await taskCell.click();
  83  |     }
  84  | 
  85  |     // 点击查看全部
  86  |     const viewAllButton = page.getByText('查看全部');
  87  |     await viewAllButton.click();
  88  | 
  89  |     // 验证按钮状态变更
  90  |     await expect(page.getByText('查看全部（已激活）')).toBeVisible();
  91  | 
  92  |     // 验证日历选中高亮已清除
  93  |     const selectedCell = page.locator('.cell-selected');
  94  |     await expect(selectedCell).toHaveCount(0);
  95  |   });
  96  | 
  97  |   test('移动端单月布局', async ({ page }) => {
  98  |     // 设置为移动端视口
  99  |     await page.setViewportSize({ width: 375, height: 812 });
  100 |     await page.goto(`${BASE_URL}/parent/tasks`);
  101 | 
  102 |     // 验证日历在移动端仍然单月可见
  103 |     const calendarGrid = page.locator('.task-calendar-grid');
  104 |     await expect(calendarGrid).toBeVisible();
  105 |     const panel = calendarGrid.locator('.calendar-panel');
  106 |     await expect(panel).toHaveCount(1);
  107 |   });
  108 | });
  109 | 
```