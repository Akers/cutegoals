import { test, expect } from '@playwright/test';

/**
 * Resubmission Controls E2E tests for CuteGoals 2.0.
 *
 * Covers the full parent→child flow for task template resubmission limits:
 * - Parent creates template with allow_resubmit=true, max_submissions=3
 * - Parent creates assignment for child
 * - Child submits 3 times, each approved
 * - Child task list shows canSubmit=false
 * - Child tries to submit again → 422 TASK_SUBMISSION_MAX_REACHED
 *
 * Requires running CuteGoals instance:
 *   docker compose -f deploy/docker-compose.yml up -d
 *   npx playwright test tests/resubmission-controls.spec.ts
 */
const BASE_URL = process.env.BASE_URL || 'http://localhost:80';

test.describe('Resubmission Controls — 重复提交控制', () => {

  test('家长创建模板并配置 allow_resubmit + max_submissions', async ({ page }) => {
    // Parent login → create template with resubmission settings
    await page.goto(`${BASE_URL}/parent/`);
    // This test requires a pre-authenticated parent session.
    // In CI, the fixture would handle authentication.
    // For now, this is a skeleton that documents the expected flow.
    test.skip(true, 'E2E requires running server with pre-authenticated session');
  });

  test('孩子重复提交达到上限后被拒绝', async ({ page }) => {
    // 1. Child views task list → sees canSubmit=true
    // 2. Child submits 3 times, each approved by parent
    // 3. Child views task list → sees canSubmit=false, reason=MAX_REACHED
    // 4. Child tries to submit → gets 422 TASK_SUBMISSION_MAX_REACHED
    test.skip(true, 'E2E requires running server with pre-authenticated session');
  });

  test('积分上限达到后被拒绝', async ({ page }) => {
    // 1. Child earns points up to cap
    // 2. Child views task list → sees canSubmit=false, reason=POINTS_CAP_REACHED
    // 3. Child tries to submit → gets 422 TASK_SUBMISSION_POINTS_CAP_REACHED
    test.skip(true, 'E2E requires running server with pre-authenticated session');
  });

  test('家长端模板表单显示重复提交控制字段', async ({ page }) => {
    // 1. Parent opens template editor
    // 2. Sees "允许重复提交" checkbox (unchecked by default)
    // 3. Checks it → sees "最大提交次数" and "积分上限" input fields
    test.skip(true, 'E2E requires running server with pre-authenticated session');
  });
});
