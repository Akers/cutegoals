/**
 * 管理端页面共享工具（与旧版 React 管理端语义对齐）。
 */

/** API 状态/结果值 → 中文文案（对齐旧版 StatusBadge 约定，未匹配时原样返回） */
export function statusLabel(s: string): string {
  const map: Record<string, string> = {
    active: '启用',
    disabled: '停用',
    success: '成功',
    failed: '失败',
    approved: '已通过',
    rejected: '已驳回',
    pending: '待处理',
    submitted: '已提交',
    completed: '已完成',
    cancelled: '已取消',
  };
  return map[s.toLowerCase()] ?? s;
}

/** 手机号脱敏展示（对齐旧版 web/packages/shared maskPhone：保留前 3 后 4） */
export function maskPhone(phone?: string): string {
  if (!phone || phone.length < 7) return phone ?? '';
  return phone.slice(0, 3) + '****' + phone.slice(7);
}
