import React from 'react';
import { useOnline } from '@shared/theme';

/* =============================================================================
 * 孩子端自绘童趣组件层（D3）—— 移动优先、大触控目标、明亮糖果色。
 * 仅保留极少量 antd 底层（message 等），页面主体全部由本文件组件构成。
 * ========================================================================== */

/* ── 页面容器 ─────────────────────────────────────────────────────── */
export function KidPage({ children }: { children: React.ReactNode }) {
  return <div className="kid-page">{children}</div>;
}

/* ── 卡片 ─────────────────────────────────────────────────────────── */
export function KidCard({
  children,
  title,
  emoji,
  accent,
  selected,
  onClick,
  className,
}: {
  children: React.ReactNode;
  title?: string;
  emoji?: string;
  accent?: 'warning' | 'muted';
  selected?: boolean;
  onClick?: () => void;
  className?: string;
}) {
  const cls = [
    'kid-card',
    onClick ? 'kid-card--clickable' : '',
    selected ? 'kid-card--selected' : '',
    accent === 'warning' ? 'kid-card--accent-warning' : '',
    accent === 'muted' ? 'kid-card--accent-muted' : '',
    className ?? '',
  ]
    .filter(Boolean)
    .join(' ');
  if (onClick) {
    return (
      <div className={cls} role="button" tabIndex={0} onClick={onClick}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            onClick();
          }
        }}>
        {title && (
          <h3 className="kid-card-title">
            {emoji && <span aria-hidden="true">{emoji}</span>}
            {title}
          </h3>
        )}
        {children}
      </div>
    );
  }
  return (
    <div className={cls}>
      {title && (
        <h3 className="kid-card-title">
          {emoji && <span aria-hidden="true">{emoji}</span>}
          {title}
        </h3>
      )}
      {children}
    </div>
  );
}

/* ── 按钮 ─────────────────────────────────────────────────────────── */
export function KidButton({
  children,
  variant = 'primary',
  size,
  block,
  disabled,
  loading,
  onClick,
  ariaLabel,
  type = 'button',
}: {
  children: React.ReactNode;
  variant?: 'primary' | 'peach' | 'mint' | 'ghost';
  size?: 'sm';
  block?: boolean;
  disabled?: boolean;
  loading?: boolean;
  onClick?: () => void;
  ariaLabel?: string;
  type?: 'button' | 'submit';
}) {
  const cls = [
    'kid-btn',
    `kid-btn--${variant}`,
    size === 'sm' ? 'kid-btn--sm' : '',
    block ? 'kid-btn--block' : '',
  ]
    .filter(Boolean)
    .join(' ');
  return (
    <button
      type={type}
      className={cls}
      disabled={disabled || loading}
      onClick={onClick}
      aria-label={ariaLabel}
      aria-busy={loading || undefined}
    >
      {children}
    </button>
  );
}

/* ── Chip 筛选器（选中 = 主题色背景 + 白字，保持 child-page-migration 契约）── */
export function ChipGroup<T extends string>({
  options,
  value,
  onChange,
  ariaLabel,
}: {
  options: readonly T[];
  value: T;
  onChange: (v: T) => void;
  ariaLabel?: string;
}) {
  return (
    <div className="kid-chip-row" role="group" aria-label={ariaLabel}>
      {options.map((opt) => (
        <button
          key={opt}
          type="button"
          className={`kid-chip${opt === value ? ' kid-chip--active' : ''}`}
          aria-pressed={opt === value}
          onClick={() => onChange(opt)}
        >
          {opt}
        </button>
      ))}
    </div>
  );
}

/* ── 标签 ─────────────────────────────────────────────────────────── */
export function KidTag({
  children,
  color = 'sky',
}: {
  children: React.ReactNode;
  color?: 'sky' | 'mint' | 'lemon' | 'peach' | 'muted' | 'danger';
}) {
  return <span className={`kid-tag kid-tag--${color}`}>{children}</span>;
}

/* ── 积分胶囊 / 大积分卡 ──────────────────────────────────────────── */
export function PointsPill({ points }: { points: number }) {
  return (
    <span className="kid-points-pill">
      <span aria-hidden="true">🌟</span>
      {points} 积分
    </span>
  );
}

export function PointsHero({ points, loading }: { points: number; loading?: boolean }) {
  return (
    <div className="kid-points-hero">
      <div>
        <div style={{ fontSize: 14, fontWeight: 700, opacity: 0.9 }}>我的积分</div>
        <div className="kid-points-hero-num">{loading ? '…' : `${points} 积分`}</div>
      </div>
      <span style={{ fontSize: 44 }} aria-hidden="true">🌟</span>
    </div>
  );
}

/* ── 空态 ─────────────────────────────────────────────────────────── */
export function EmptyState({ emoji = '🎈', text }: { emoji?: string; text: string }) {
  return (
    <div className="kid-empty">
      <span className="kid-empty-emoji" aria-hidden="true">{emoji}</span>
      <span>{text}</span>
    </div>
  );
}

/* ── 加载 ─────────────────────────────────────────────────────────── */
export function KidSpinner() {
  return (
    <div className="kid-spinner" role="status" aria-label="加载中">
      <span className="kid-spinner-dot" />
      <span className="kid-spinner-dot" />
      <span className="kid-spinner-dot" />
    </div>
  );
}

/* ── 加载/离线/错误状态处理 ───────────────────────────────────────── */
export function StateView({
  loading,
  error,
  onRetry,
  children,
}: {
  loading: boolean;
  error?: { message?: string };
  onRetry?: () => void;
  children: React.ReactNode;
}) {
  const online = useOnline();
  if (!online) {
    return (
      <div className="kid-empty">
        <span className="kid-empty-emoji" aria-hidden="true">📡</span>
        <span>当前处于离线状态，请检查网络连接</span>
        {onRetry && (
          <KidButton variant="ghost" size="sm" onClick={onRetry}>重试</KidButton>
        )}
      </div>
    );
  }
  if (loading) return <KidSpinner />;
  if (error) {
    return (
      <div className="kid-empty">
        <span className="kid-empty-emoji" aria-hidden="true">😵</span>
        <span>加载失败：{error.message}</span>
        {onRetry && (
          <KidButton variant="ghost" size="sm" onClick={onRetry}>重试</KidButton>
        )}
      </div>
    );
  }
  return <>{children}</>;
}

/* ── 底部弹层 Modal（移动优先底部弹出，≥640px 居中）────────────────── */
export function KidModal({
  open,
  title,
  emoji,
  onClose,
  footer,
  children,
}: {
  open: boolean;
  title?: string;
  emoji?: string;
  onClose?: () => void;
  footer?: React.ReactNode;
  children: React.ReactNode;
}) {
  if (!open) return null;
  return (
    <div className="kid-modal-overlay" onClick={onClose}>
      <div
        className="kid-modal"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={(e) => e.stopPropagation()}
      >
        {title && (
          <h3 className="kid-modal-title">
            {emoji && <span aria-hidden="true">{emoji}</span>}
            {title}
          </h3>
        )}
        <div>{children}</div>
        {footer && <div className="kid-modal-footer">{footer}</div>}
      </div>
    </div>
  );
}
