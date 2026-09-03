import { Alova } from '@/utils/http/alova';
import type {
  Exchange,
  ExchangeCancelParams,
  ExchangeListParams,
  Id,
  PageResult,
} from '@/types/api';

/**
 * 兑换记录（家长侧：查询、发货、取消）
 * Source: server/exchange/.../controller/ExchangeController.java
 */

/** GET /api/exchanges — 兑换记录列表（分页，page 从 0 或 1 由页面层决定） */
export function listExchanges(params?: Partial<ExchangeListParams>) {
  return Alova.Get<PageResult<Exchange>>('/exchanges', {
    params,
  });
}

/** GET /api/exchanges/{id} — 兑换详情 */
export function getExchange(id: Id) {
  return Alova.Get<Exchange>(`/exchanges/${id}`);
}

/** POST /api/exchanges/{id}/fulfill — 标记已发货 */
export function fulfillExchange(id: Id) {
  return Alova.Post<void>(`/exchanges/${id}/fulfill`);
}

/** POST /api/exchanges/{id}/cancel — 取消兑换（退积分，必须填写原因） */
export function cancelExchange(id: Id, params: ExchangeCancelParams) {
  return Alova.Post<void>(`/exchanges/${id}/cancel`, params);
}
