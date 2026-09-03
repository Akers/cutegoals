import { Alova } from '@/utils/http/alova';
import type {
  ChildProfile,
  CreateChildParams,
  PagedParams,
  PageResult,
  UpdateChildParams,
} from '@/types/api';

/**
 * GET /api/family/children — 孩子档案列表（分页）
 * Source: server/family/.../controller/ChildProfileController.java
 */
export function listChildren(params?: Partial<PagedParams>) {
  return Alova.Get<PageResult<ChildProfile>>('/family/children', {
    params,
  });
}

/** POST /api/family/children — 创建孩子档案 { nickname, pin?, birthday? } */
export function createChild(params: CreateChildParams) {
  return Alova.Post<ChildProfile>('/family/children', params);
}

/** PUT /api/family/children/{id} — 更新孩子档案 */
export function updateChild(id: string | number, params: UpdateChildParams) {
  return Alova.Put<void>(`/family/children/${id}`, params);
}

/** DELETE /api/family/children/{id} — 删除孩子档案 */
export function deleteChild(id: string | number) {
  return Alova.Delete<void>(`/family/children/${id}`);
}
