// antd direct re-exports
export {
  Button,
  Modal,
  Pagination,
  Empty,
  Result,
  Spin,
  Tag,
  Card,
  Input,
  Badge,
} from 'antd';
export { Select } from 'antd';

// Legacy custom component aliases (for backward compat with old page files)
// These are minimal implementations that wrap antd components.
import React from 'react';
import { Modal as AntModal } from 'antd';

export function FormField({
  label,
  children,
  error,
  htmlFor,
}: {
  label: string;
  children: React.ReactNode;
  error?: string;
  htmlFor?: string;
}) {
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={htmlFor} style={{ fontSize: 14, fontWeight: 500 }}>
        {label}
      </label>
      {children}
      {error && (
        <p style={{ color: '#ff4d4f', fontSize: 13 }} role="alert">
          {error}
        </p>
      )}
    </div>
  );
}

export function Label(
  props: React.LabelHTMLAttributes<HTMLLabelElement>,
) {
  return <label {...props} />;
}

/** Minimal StatusBadge – uses Tag internally */
export function StatusBadge({ status }: { status: string }) {
  const colorMap: Record<string, string> = {
    pending: 'gold',
    submitted: 'blue',
    in_progress: 'blue',
    approved: 'green',
    rejected: 'red',
    completed: 'green',
    cancelled: 'default',
    fulfilled: 'green',
    pending_fulfillment: 'blue',
    refunded: 'default',
    out_of_stock: 'red',
  };
  const labelMap: Record<string, string> = {
    pending: '待处理',
    submitted: '已提交',
    in_progress: '进行中',
    approved: '已通过',
    rejected: '已驳回',
    completed: '已完成',
    cancelled: '已取消',
    fulfilled: '已兑现',
    pending_fulfillment: '待兑现',
    refunded: '已退款',
    out_of_stock: '售罄',
  };
  const color = colorMap[status?.toLowerCase()] ?? 'default';
  const label = labelMap[status?.toLowerCase()] ?? status;
  return <span style={{
    display: 'inline-flex', alignItems: 'center', gap: 4,
    padding: '2px 8px', borderRadius: 4, fontSize: 12, fontWeight: 600,
    backgroundColor: color === 'green' ? '#f6ffed' : color === 'red' ? '#fff2f0' : color === 'gold' ? '#fffbe6' : color === 'blue' ? '#e6f7ff' : '#fafafa',
    color: color === 'green' ? '#52c41a' : color === 'red' ? '#ff4d4f' : color === 'gold' ? '#faad14' : color === 'blue' ? '#1890ff' : '#000000d9',
    border: '1px solid ' + (color === 'green' ? '#b7eb8f' : color === 'red' ? '#ffccc7' : color === 'gold' ? '#ffe58f' : color === 'blue' ? '#91d5ff' : '#d9d9d9'),
    whiteSpace: 'nowrap',
  }} role="status" aria-label={label}>{label}</span>;
}

export function PageHeader({
  title,
  subtitle,
  actions,
}: {
  title: string;
  subtitle?: string;
  actions?: React.ReactNode;
  backTo?: string;
}) {
  return (
    <div style={{ marginBottom: 24 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div>
          <h1 style={{ fontSize: 24, fontWeight: 700, margin: 0 }}>{title}</h1>
          {subtitle && <p style={{ fontSize: 14, color: '#666', margin: '4px 0 0' }}>{subtitle}</p>}
        </div>
        {actions && <div style={{ display: 'flex', gap: 8 }}>{actions}</div>}
      </div>
    </div>
  );
}

export function Layout({ children }: { children: React.ReactNode }) {
  // Try to read role from DOM if available, for footer display
  const el = typeof document !== 'undefined' ? document.getElementById('root') : null;
  const roleFromAttr = el?.getAttribute('data-role');
  const layoutRole = roleFromAttr || 'child';
  const roleLabel = layoutRole === 'admin' ? '管理后台' : layoutRole === 'parent' ? '家长端' : '儿童端';
  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <main style={{ flex: 1 }}>{children}</main>
      <footer style={{ textAlign: 'center', padding: 8, fontSize: 12, color: '#999' }}>
        CuteGoals 2.0 · {roleLabel}
      </footer>
    </div>
  );
}

export function ConfirmModal({
  isOpen,
  onClose,
  title,
  message,
  confirmText = '确认',
  cancelText = '取消',
  onConfirm,
  confirmVariant = 'primary',
  isConfirming = false,
}: {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  onConfirm: () => void;
  confirmVariant?: 'primary' | 'danger';
  isConfirming?: boolean;
}) {
  return (
    <AntModal
      open={isOpen}
      onCancel={onClose}
      title={title}
      footer={
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <button onClick={onClose} disabled={isConfirming}>{cancelText}</button>
          <button
            onClick={onConfirm}
            disabled={isConfirming}
            style={{
              backgroundColor: confirmVariant === 'danger' ? '#ff4d4f' : '#1677ff',
              color: '#fff', border: 'none', padding: '4px 16px', borderRadius: 4,
            }}
          >
            {isConfirming ? '处理中...' : confirmText}
          </button>
        </div>
      }
    >
      <p>{message}</p>
    </AntModal>
  );
}
