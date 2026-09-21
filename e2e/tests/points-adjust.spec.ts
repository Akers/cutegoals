import { test, expect, type Page } from '@playwright/test';

/**
 * 积分调整「写后读」一致性回归（change: parent-points-adjust-not-reflected-fix）
 *
 * 背景：console 的 alova 实例曾未关闭 GET 默认 5 分钟内存缓存（cacheFor），
 * 家长在 /parent/points 确认调整并看到「积分已调整」成功提示后，页面重新拉取
 * 命中陈旧缓存，余额与流水不更新，需整页刷新才能看到新状态。
 *
 * 运行条件（完整栈网关 + 家长凭据）：
 *   BASE_URL=https://localhost E2E_PARENT_PHONE=... E2E_PARENT_PASSWORD=... \
 *     npx playwright test tests/points-adjust.spec.ts --project=chromium-desktop
 * BASE_URL 或凭据缺失时按套件惯例 skip。
 */
const BASE_URL = process.env.BASE_URL || '';
const FULL_STACK = !!process.env.BASE_URL;
const PARENT_PHONE = process.env.E2E_PARENT_PHONE || '';
const PARENT_PASSWORD = process.env.E2E_PARENT_PASSWORD || '';

interface ApiEnvelope<T> {
  code: string;
  message: string;
  data: T;
  request_id?: string;
}

interface LedgerData {
  currentBalance: number;
  totalElements: number;
  content: Array<{ id: number; reason: string | null; type: string; amount: number }>;
}

/** 家长登录（cookie 写入 page context）。凭据缺失时 skip。 */
async function loginAsParent(page: Page) {
  test.skip(
    !PARENT_PHONE || !PARENT_PASSWORD,
    'E2E 家长凭据缺失：设置 E2E_PARENT_PHONE 和 E2E_PARENT_PASSWORD',
  );
  const response = await page.request.post(`${BASE_URL}/api/auth/login`, {
    data: { phone: PARENT_PHONE, password: PARENT_PASSWORD },
    failOnStatusCode: false,
  });
  if (!response.ok()) {
    const body = await response.text();
    throw new Error(`parent login failed (status=${response.status()}, body=${body || '<empty>'})`);
  }
}

/** 从首页经侧边栏进入积分管理页（冷启动直达深链接会被路由守卫重定向回工作台）。 */
async function openPointsPage(page: Page) {
  await page.goto(`${BASE_URL}/`);
  await page.waitForSelector('.n-menu', { timeout: 20000 });
  const group = page.locator('.n-submenu:has-text("家长端")');
  if (await group.count()) {
    await group.first().click();
    await page.waitForTimeout(600);
  }
  await page.locator('.n-menu-item:has-text("积分管理")').first().click();
  await page.waitForSelector('h3:has-text("积分")', { timeout: 15000 });
}

/** 在积分页选择第一个孩子，等待余额与流水渲染完成。 */
async function selectFirstChild(page: Page) {
  const trigger = (await page.locator('.n-base-selection').count())
    ? page.locator('.n-base-selection').first()
    : page.locator('.n-select').first();
  await trigger.click();
  await page.waitForSelector('.n-base-select-option, .n-select-option', { timeout: 5000 });
  await page.locator('.n-base-select-option, .n-select-option').first().click();
  await page.waitForSelector('.balance', { timeout: 5000 });
  await page.waitForTimeout(800);
}

async function fetchLedger(page: Page, url: string): Promise<LedgerData> {
  const resp = await page.request.get(url);
  expect(resp.status(), 'ledger API 应返回 200').toBe(200);
  const body = (await resp.json()) as ApiEnvelope<LedgerData>;
  expect(body.code).toBe('SUCCESS');
  return body.data;
}

test.describe('积分调整写后读一致性（完整栈）', () => {
  test.skip(!FULL_STACK, '需要 BASE_URL 指向完整栈网关（例如 https://localhost）');
  // 缓存行为在 JS 层与浏览器内核无关；仅桌面 chromium 执行一次，避免多 project 对真实环境重复写入积分
  test.skip(
    () => test.info().project.name !== 'chromium-desktop',
    '仅 chromium-desktop 执行，避免多 project 重复写入真实环境',
  );

  // 支持自签证书的 https 网关（如本机部署的 https://localhost）
  test.use({ ignoreHTTPSErrors: true });

  test('调整积分成功提示后，不刷新页面余额与流水立即更新', async ({ page }) => {
    await loginAsParent(page);
    await openPointsPage(page);

    // 记录最近一次 ledger 请求 URL（用于 API 交叉核对），并统计调整后的真实网络响应数
    let ledgerUrl: string | null = null;
    let ledgerResponsesAfterAdjust = 0;
    let adjustClicked = false;
    page.on('response', (r) => {
      if (!r.url().includes('/api/points/ledger/')) return;
      ledgerUrl = r.url();
      if (adjustClicked) ledgerResponsesAfterAdjust++;
    });

    await selectFirstChild(page);
    expect(ledgerUrl, '选择孩子后应发出 ledger 请求').not.toBeNull();
    const before = await fetchLedger(page, ledgerUrl!);
    const balanceBefore = Number(
      (await page.locator('.balance').innerText()).replace(/[^0-9-]/g, ''),
    );
    const rowsBefore = await page.locator('.tx-row').count();
    expect(balanceBefore).toBe(before.currentBalance);

    // 提交一次 +1 调整（正数调整不受余额下限约束）
    await page.locator('input[placeholder="请输入调整数量"]').fill('1');
    await page.locator('input[placeholder="请输入原因"]').fill(`e2e-cache-regression-${Date.now()}`);
    adjustClicked = true;
    await page.locator('button:has-text("确认调整")').click();
    await page.waitForSelector('text=积分已调整', { timeout: 8000 });

    // 断言 1：不刷新页面，余额立即 +1（回归点：陈旧缓存下该断言失败）
    await expect(page.locator('.balance')).toHaveText(
      new RegExp(`^\\s*${balanceBefore + 1}\\s*积分`),
      { timeout: 5000 },
    );
    // 流水立即出现新记录（页大小 20，满页时行数不增长，由 totalElements 断言覆盖）
    if (rowsBefore < 20) {
      await expect(page.locator('.tx-row')).toHaveCount(rowsBefore + 1, { timeout: 5000 });
    }

    // 断言 2：调整后的重新拉取真实到达网络（未命中 alova 内存缓存）
    expect(ledgerResponsesAfterAdjust).toBeGreaterThanOrEqual(1);

    // 断言 3：界面数值与后端 API 一致（totalElements 与页大小无关）
    const after = await fetchLedger(page, ledgerUrl!);
    expect(after.currentBalance).toBe(balanceBefore + 1);
    expect(after.totalElements).toBe(before.totalElements + 1);
    expect(after.content[0]?.reason).toMatch(/^e2e-cache-regression-/);
  });
});
