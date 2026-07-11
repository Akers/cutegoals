import { ReactNode } from 'react';
import { Button } from './Button';

interface EmptyStateProps {
  title?: string;
  description?: string;
  action?: {
    label: string;
    onClick: () => void;
  };
  icon?: ReactNode;
}

export function EmptyState({
  title = '暂无数据',
  description = '当前列表为空',
  action,
  icon,
}: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-12 text-center">
      {icon && <div className="text-4xl text-cg-text-muted" aria-hidden="true">{icon}</div>}
      <div className="text-lg font-medium text-cg-text" role="status">{title}</div>
      <p className="max-w-xs text-sm text-cg-text-muted">{description}</p>
      {action && <Button onClick={action.onClick}>{action.label}</Button>}
    </div>
  );
}

interface ErrorStateProps {
  title?: string;
  message?: string;
  onRetry?: () => void;
}

export function ErrorState({
  title = '加载失败',
  message = '请检查网络连接后重试',
  onRetry,
}: ErrorStateProps) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-12 text-center" role="alert">
      <div className="text-4xl text-cg-danger" aria-hidden="true">✕</div>
      <div className="text-lg font-medium text-cg-text">{title}</div>
      <p className="max-w-xs text-sm text-cg-text-muted">{message}</p>
      {onRetry && <Button onClick={onRetry}>重试</Button>}
    </div>
  );
}

export function LoadingState({ message = '加载中…' }: { message?: string }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-12" role="status" aria-live="polite">
      <span className="inline-block h-8 w-8 animate-spin rounded-full border-4 border-cg-border border-t-cg-primary" aria-hidden="true" />
      <span className="text-sm text-cg-text-muted">{message}</span>
    </div>
  );
}

export function OfflineState({ onRetry }: { onRetry?: () => void }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-12 text-center" role="alert">
      <div className="text-4xl text-cg-warning" aria-hidden="true">!</div>
      <div className="text-lg font-medium text-cg-text">当前处于离线状态</div>
      <p className="max-w-xs text-sm text-cg-text-muted">
        请检查网络连接，恢复后重试
      </p>
      {onRetry && <Button onClick={onRetry}>重试</Button>}
    </div>
  );
}
