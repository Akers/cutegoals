import { test, expect, type Page } from '@playwright/test';

/**
 * Resubmission Controls E2E tests for CuteGoals 2.0.
 *
 * 家长端（本地 console dev + 桩后端即可运行）：
 * - 创建模板并配置 allow_resubmit + max_submissions=3（真实 UI 表单 + 断言请求/响应契约）
 * - 模板表单「允许重复提交」默认未勾选，勾选后出现「最大提交次数」「积分上限」字段
 *
 * 孩子端（需要完整栈，BASE_URL 指向网关，例如 docker compose 的 http://localhost:80）：
 * - 重复提交达到上限 → 列表 canSubmit=false / submissionBlockReason=MAX_REACHED，
 *   再次提交 → 422 TASK_SUBMISSION_MAX_REACHED
 * - 积分上限达到 → canSubmit=false / POINTS_CAP_REACHED，
 *   再次提交 → 422 TASK_SUBMISSION_POINTS_CAP_REACHED
 *
 * 家长登录沿用 parent-task-calendar.spec.ts 模式：
 *   E2E_PARENT_PHONE / E2E_PARENT_PASSWORD env + page.request POST /api/auth/login。
 * 孩子端凭据：E2E_CHILD_PHONE / E2E_CHILD_PASSWORD（缺失时孩子端用例 skip）。
 */
const BASE_URL = process.env.BASE_URL || 'http://localhost:8000';
// 完整栈判定：BASE_URL 显式设置（本地 console dev 默认 8000 时无孩子端应用）
const FULL_STACK = !!process.env.BASE_URL;

const PARENT_PHONE = process.env.E2E_PARENT_PHONE || '';
const PARENT_PASSWORD = process.env.E2E_PARENT_PASSWORD || '';
const CHILD_PHONE = process.env.E2E_CHILD_PHONE || '';
const CHILD_PASSWORD = process.env.E2E_CHILD_PASSWORD || '';

interface ApiEnvelope<T> {
  code: string;
  message: string;
  data: T;
  request_id?: string;
}

interface ChildAssignmentApi {
  id: number;
  status: string;
  cancelled?: boolean;
  canSubmit: boolean;
  submissionBlockReason: 'MAX_REACHED' | 'POINTS_CAP_REACHED' | null;
  snapshotTemplateAllowResubmit: boolean | null;
  snapshotTemplateMaxSubmissions: number | null;
  snapshotTemplatePointsCap: number | null;
}

/** 家长登录（cookie 写入 page context），失败抛错。凭据缺失时 skip。 */
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

/** 孩子登录，返回 { childId }。凭据缺失时 skip。 */
async function loginAsChild(page: Page): Promise<{ childId: number | null }> {
  test.skip(
    !CHILD_PHONE || !CHILD_PASSWORD,
    'E2E 孩子凭据缺失：设置 E2E_CHILD_PHONE 和 E2E_CHILD_PASSWORD',
  );
  const response = await page.request.post(`${BASE_URL}/api/auth/login`, {
    data: { phone: CHILD_PHONE, password: CHILD_PASSWORD },
    failOnStatusCode: false,
  });
  if (!response.ok()) {
    const body = await response.text();
    throw new Error(`child login failed (status=${response.status()}, body=${body || '<empty>'})`);
  }
  const body = (await response.json()) as ApiEnvelope<{ accountId: number; childId: number | null }>;
  return { childId: body.data?.childId ?? body.data?.accountId ?? null };
}

/** 拉取孩子任务列表（孩子会话 cookie）。 */
async function fetchChildAssignments(page: Page, childId: number) {
  const response = await page.request.get(
    `${BASE_URL}/api/task-assignments?childId=${childId}&pageSize=100`,
  );
  expect(response.status(), '孩子任务列表应返回 200').toBe(200);
  const body = (await response.json()) as ApiEnvelope<{ content: ChildAssignmentApi[] }>;
  return body.data?.content ?? [];
}

function genIdempotencyKey(): string {
  return typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

test.describe('Resubmission Controls — 重复提交控制', () => {
  // ─────────────────────────────────────────────
  // 家长端：本地 console dev + 桩后端即可运行
  // ─────────────────────────────────────────────

  test('家长创建模板并配置 allow_resubmit + max_submissions', async ({ page }) => {
    await loginAsParent(page);
    await page.goto(`${BASE_URL}/parent/templates`);

    // 打开新建模板弹窗
    await page.getByRole('button', { name: '新建模板' }).click();
    const modal = page.locator('.n-modal');
    await expect(modal).toBeVisible();

    // 填写必填字段
    const titleInput = modal.locator('.n-form-item').filter({ hasText: '标题' }).locator('input');
    await titleInput.fill('背单词重复打卡');

    // 勾选「允许重复提交」
    await modal.getByText('允许重复提交').click();

    // 配置最大提交次数 = 3（勾选后字段才渲染）
    const maxFormItem = modal.locator('.n-form-item').filter({ hasText: '最大提交次数' });
    await expect(maxFormItem).toBeVisible();
    await maxFormItem.locator('input').fill('3');

    // 保存，并同时捕获请求与响应验证契约（对真实后端/桩后端均成立）
    const [saveResponse] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/task-templates') && r.request().method() === 'POST',
      ),
      modal.getByRole('button', { name: '保存' }).click(),
    ]);

    expect(saveResponse.status(), '创建模板应返回 200').toBe(200);
    const payload = saveResponse.request().postDataJSON() as Record<string, unknown>;
    expect(payload.allow_resubmit).toBe(true);
    expect(payload.max_submissions).toBe(3);

    const responseBody = (await saveResponse.json()) as ApiEnvelope<Record<string, unknown>>;
    expect(responseBody.code).toBe('SUCCESS');
    expect(responseBody.data?.allowResubmit).toBe(true);
    expect(responseBody.data?.maxSubmissions).toBe(3);

    // 弹窗关闭 + 保存成功提示
    await expect(modal).toBeHidden();
    await expect(page.getByText('保存成功')).toBeVisible();
  });

  test('家长端模板表单显示重复提交控制字段', async ({ page }) => {
    await loginAsParent(page);
    await page.goto(`${BASE_URL}/parent/templates`);

    await page.getByRole('button', { name: '新建模板' }).click();
    const modal = page.locator('.n-modal');
    await expect(modal).toBeVisible();

    // 「允许重复提交」默认未勾选
    const resubmitCheckbox = modal.locator('.n-checkbox').filter({ hasText: '允许重复提交' });
    await expect(resubmitCheckbox).toBeVisible();
    await expect(resubmitCheckbox).not.toHaveClass(/n-checkbox--checked/);

    // 未勾选时不显示联动字段
    await expect(modal.locator('.n-form-item').filter({ hasText: '最大提交次数' })).toHaveCount(0);
    await expect(modal.locator('.n-form-item').filter({ hasText: '积分上限' })).toHaveCount(0);

    // 勾选后出现「最大提交次数」和「积分上限」输入框
    await modal.getByText('允许重复提交').click();
    const maxItem = modal.locator('.n-form-item').filter({ hasText: '最大提交次数' });
    const capItem = modal.locator('.n-form-item').filter({ hasText: '积分上限' });
    await expect(maxItem).toBeVisible();
    await expect(capItem).toBeVisible();
    await expect(maxItem.locator('input')).toBeEditable();
    await expect(capItem.locator('input')).toBeEditable();
  });

  // ─────────────────────────────────────────────
  // 孩子端：需要完整栈（BASE_URL 指向网关）
  // ─────────────────────────────────────────────

  test('孩子重复提交达到上限后被拒绝', async ({ page }) => {
    test.skip(!FULL_STACK, '需要完整栈：设置 BASE_URL 指向网关（例如 http://localhost:80）');
    const { childId } = await loginAsChild(page);
    expect(childId, '孩子账号应携带 childId/accountId').toBeTruthy();

    // 1. 找到一个允许重复提交且有提交次数上限的进行中任务
    let tasks = await fetchChildAssignments(page, childId!);
    const target = tasks.find(
      (t) =>
        !t.cancelled &&
        t.snapshotTemplateAllowResubmit === true &&
        (t.snapshotTemplateMaxSubmissions ?? 0) > 0 &&
        (t.status === 'PENDING' || t.status === 'REJECTED') &&
        t.canSubmit,
    );
    test.skip(!target, '环境中没有可用的允许重复提交任务（需预先由家长创建模板并分配）');

    const maxSubmissions = target!.snapshotTemplateMaxSubmissions!;
    const parentPage = await page.context().newPage();
    await loginAsParent(parentPage);

    // 2. 反复「提交 → 家长审核通过」，直到达到上限
    for (let attempt = 0; attempt < maxSubmissions; attempt++) {
      const submitRes = await page.request.post(`${BASE_URL}/api/task-review/submissions`, {
        data: {
          assignmentId: target!.id,
          notes: `e2e resubmit attempt ${attempt + 1}`,
          idempotencyKey: genIdempotencyKey(),
        },
        failOnStatusCode: false,
      });
      expect(submitRes.status(), `第 ${attempt + 1} 次提交应成功`).toBe(200);

      // 家长审核通过，任务回到可提交状态（最后一次提交后不再需要审核通过）
      const submitBody = (await submitRes.json()) as ApiEnvelope<{ attemptId?: number; id?: number }>;
      const attemptId = submitBody.data?.attemptId ?? submitBody.data?.id;
      if (attempt < maxSubmissions - 1) {
        test.skip(!attemptId, '后端提交响应未包含 attemptId，无法自动审核');
        const approveRes = await parentPage.request.post(
          `${BASE_URL}/api/task-review/${attemptId}/approve`,
          { data: {}, failOnStatusCode: false },
        );
        expect(approveRes.status(), `第 ${attempt + 1} 次审核应成功`).toBe(200);
      }
    }

    // 3. 任务列表显示 canSubmit=false，submissionBlockReason=MAX_REACHED
    tasks = await fetchChildAssignments(page, childId!);
    const blocked = tasks.find((t) => t.id === target!.id);
    expect(blocked, '任务应仍在列表中').toBeTruthy();
    expect(blocked!.canSubmit).toBe(false);
    expect(blocked!.submissionBlockReason).toBe('MAX_REACHED');

    // 4. 再次提交 → 422 TASK_SUBMISSION_MAX_REACHED
    const rejectRes = await page.request.post(`${BASE_URL}/api/task-review/submissions`, {
      data: {
        assignmentId: target!.id,
        notes: 'should be rejected',
        idempotencyKey: genIdempotencyKey(),
      },
      failOnStatusCode: false,
    });
    expect(rejectRes.status()).toBe(422);
    const rejectBody = (await rejectRes.json()) as ApiEnvelope<unknown>;
    expect(rejectBody.code).toBe('TASK_SUBMISSION_MAX_REACHED');
    await parentPage.close();
  });

  test('积分上限达到后被拒绝', async ({ page }) => {
    test.skip(!FULL_STACK, '需要完整栈：设置 BASE_URL 指向网关（例如 http://localhost:80）');
    const { childId } = await loginAsChild(page);
    expect(childId, '孩子账号应携带 childId/accountId').toBeTruthy();

    // 1. 找到一个配置了积分上限、且积分上限已耗尽/接近的任务
    const tasks = await fetchChildAssignments(page, childId!);
    const blockedByCap = tasks.find(
      (t) => !t.cancelled && !t.canSubmit && t.submissionBlockReason === 'POINTS_CAP_REACHED',
    );
    test.skip(
      !blockedByCap,
      '环境中没有已达积分上限的任务（需预先配置 points_cap 并使孩子积分达到上限）',
    );

    // 2. 再次提交 → 422 TASK_SUBMISSION_POINTS_CAP_REACHED
    const rejectRes = await page.request.post(`${BASE_URL}/api/task-review/submissions`, {
      data: {
        assignmentId: blockedByCap!.id,
        notes: 'should be rejected by points cap',
        idempotencyKey: genIdempotencyKey(),
      },
      failOnStatusCode: false,
    });
    expect(rejectRes.status()).toBe(422);
    const rejectBody = (await rejectRes.json()) as ApiEnvelope<unknown>;
    expect(rejectBody.code).toBe('TASK_SUBMISSION_POINTS_CAP_REACHED');
  });
});
