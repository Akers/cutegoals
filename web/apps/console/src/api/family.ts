import { Alova } from '@/utils/http/alova';
import type {
  CreateInvitationParams,
  Family,
  FamilyUpdateParams,
  Invitation,
  PagedParams,
  PageResult,
} from '@/types/api';

/**
 * GET /api/family — 当前家庭（含 members/children/devices）
 * Source: server/family/.../controller/FamilyController.java
 */
export function getFamily() {
  return Alova.Get<Family>('/family');
}

/** PUT /api/family — 更新家庭名称 */
export function updateFamily(params: FamilyUpdateParams) {
  return Alova.Put<void>('/family', params);
}

/**
 * GET /api/family/invitations — 邀请记录（分页）
 * Source: server/family/.../controller/InvitationController.java
 */
export function listInvitations(params?: Partial<PagedParams>) {
  return Alova.Get<PageResult<Invitation>>('/family/invitations', {
    params,
  });
}

/** POST /api/family/invitations — 发起家长邀请 */
export function createInvitation(params: CreateInvitationParams) {
  return Alova.Post<void>('/family/invitations', params);
}

/** DELETE /api/family/members/{id} — 移除家庭成员 */
export function deleteMember(id: string | number) {
  return Alova.Delete<void>(`/family/members/${id}`);
}

/** POST /api/family/members/me/leave — 当前账号退出家庭 */
export function leaveFamily() {
  return Alova.Post<void>('/family/members/me/leave');
}
