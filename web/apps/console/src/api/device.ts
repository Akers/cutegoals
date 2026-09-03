import { Alova } from '@/utils/http/alova';
import type { DeviceBinding } from '@/types/api';

/**
 * 设备绑定（家长将孩子设备绑定到家庭）
 * Source: server/family/.../controller/DeviceController.java
 */

/** POST /api/family/devices/bind — 绑定设备 { deviceId }，返回绑定信息（含设备凭据 credential） */
export function bindDevice(params: { deviceId: string }) {
  return Alova.Post<DeviceBinding>('/family/devices/bind', params);
}

/** DELETE /api/family/devices/{id} — 解绑设备 */
export function unbindDevice(id: string | number) {
  return Alova.Delete<void>(`/family/devices/${id}`);
}
