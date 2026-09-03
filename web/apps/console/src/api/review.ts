import { Alova } from '@/utils/http/alova';
import type { Id, ReviewDecisionParams, ReviewItem } from '@/types/api';

/**
 * 任务审核（家长处理孩子提交）
 * Source: server/task-review/.../controller/TaskReviewController.java
 */

/** GET /api/task-review/pending — 待审核列表 */
export function listPendingReviews() {
  return Alova.Get<ReviewItem[]>('/task-review/pending');
}

/** GET /api/task-review/history — 审核历史 */
export function listReviewHistory() {
  return Alova.Get<ReviewItem[]>('/task-review/history');
}

/** POST /api/task-review/{attemptId}/approve — 通过审核 */
export function approveAttempt(attemptId: Id, params?: ReviewDecisionParams) {
  return Alova.Post<void>(`/task-review/${attemptId}/approve`, params ?? {});
}

/** POST /api/task-review/{attemptId}/reject — 驳回审核（必须填写原因） */
export function rejectAttempt(attemptId: Id, params: ReviewDecisionParams) {
  return Alova.Post<void>(`/task-review/${attemptId}/reject`, params);
}
