import { Alova } from '@/utils/http/alova';
import type {
  Id,
  PointsAdjustmentParams,
  PointsAdjustmentResult,
  PointsBalance,
  PointsLedger,
} from '@/types/api';

/**
 * 积分（家长侧：查询流水/余额、手工调整）
 * Source: server/points/.../controller/PointsController.java
 */

/** GET /api/points/ledger/{childId} — 积分流水 */
export function getLedger(childId: Id) {
  return Alova.Get<PointsLedger>(`/points/ledger/${childId}`);
}

/** GET /api/points/balance/{childId} — 积分余额概览 */
export function getBalance(childId: Id) {
  return Alova.Get<PointsBalance>(`/points/balance/${childId}`);
}

/** POST /api/points/adjustments — 家长手工调整积分 { childId, amount, reason, businessRef } */
export function createAdjustment(params: PointsAdjustmentParams) {
  return Alova.Post<PointsAdjustmentResult>('/points/adjustments', params);
}
