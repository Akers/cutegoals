import { Alova } from '@/utils/http/alova';
import type {
  BlindBox,
  BlindBoxCandidate,
  BlindBoxItemParams,
  BlindBoxPayload,
  Id,
  PageResult,
  Prize,
  PrizePayload,
  PrizeUploadResult,
  PagedParams,
} from '@/types/api';

// ===== 奖品 =====
// Source: server/prize/.../controller/PrizeController.java + UploadController.java

/** GET /api/prizes — 奖品列表（分页） */
export function listPrizes(
  params?: Partial<PagedParams> & { enabled?: boolean; keyword?: string }
) {
  return Alova.Get<PageResult<Prize>>('/prizes', {
    params,
  });
}

/** POST /api/prizes — 创建奖品 */
export function createPrize(payload: PrizePayload) {
  return Alova.Post<Prize>('/prizes', payload);
}

/** PUT /api/prizes/{id} — 更新奖品（含库存调整） */
export function updatePrize(id: Id, payload: Partial<PrizePayload>) {
  return Alova.Put<Prize>(`/prizes/${id}`, payload);
}

/** DELETE /api/prizes/{id} — 删除奖品 */
export function deletePrize(id: Id) {
  return Alova.Delete<void>(`/prizes/${id}`);
}

/** POST /api/prizes/upload — 上传奖品图片（multipart，返回 { url }） */
export function uploadPrizeImage(file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return Alova.Post<PrizeUploadResult>('/prizes/upload', formData);
}

// ===== 盲盒奖池 =====
// Source: server/prize/.../controller/BlindBoxController.java

/** GET /api/blind-boxes — 盲盒奖池列表（分页） */
export function listBlindBoxes(params?: Partial<PagedParams> & { enabled?: boolean }) {
  return Alova.Get<PageResult<BlindBox>>('/blind-boxes', {
    params,
  });
}

/** GET /api/blind-boxes/{id} — 奖池详情（含 items） */
export function getBlindBox(id: Id) {
  return Alova.Get<BlindBox & { items?: unknown[] }>(`/blind-boxes/${id}`);
}

/** POST /api/blind-boxes — 创建盲盒奖池 { name, cost, enabled? } */
export function createBlindBox(payload: BlindBoxPayload) {
  return Alova.Post<BlindBox>('/blind-boxes', payload);
}

/** PUT /api/blind-boxes/{id} — 更新盲盒奖池 */
export function updateBlindBox(id: Id, payload: Partial<BlindBoxPayload>) {
  return Alova.Put<BlindBox>(`/blind-boxes/${id}`, payload);
}

/** DELETE /api/blind-boxes/{id} — 删除盲盒奖池 */
export function deleteBlindBox(id: Id) {
  return Alova.Delete<void>(`/blind-boxes/${id}`);
}

/** GET /api/blind-boxes/{id}/candidates — 抽中概率候选（奖品+概率） */
export function getBlindBoxCandidates(id: Id) {
  return Alova.Get<{ candidates: BlindBoxCandidate[] }>(`/blind-boxes/${id}/candidates`);
}

/** POST /api/blind-boxes/{id}/items — 添加奖品项 { prizeId, weight? } */
export function addBlindBoxItem(id: Id, params: BlindBoxItemParams) {
  return Alova.Post<unknown>(`/blind-boxes/${id}/items`, params);
}

/** DELETE /api/blind-boxes/{poolId}/items/{itemId} — 移除奖品项 */
export function removeBlindBoxItem(poolId: Id, itemId: Id) {
  return Alova.Delete<void>(`/blind-boxes/${poolId}/items/${itemId}`);
}
