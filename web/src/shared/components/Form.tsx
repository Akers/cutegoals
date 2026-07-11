import { InputHTMLAttributes, forwardRef, LabelHTMLAttributes } from 'react';

export const Label = forwardRef<HTMLLabelElement, LabelHTMLAttributes<HTMLLabelElement>>(
  ({ className = '', children, ...props }, ref) => (
    <label
      ref={ref}
      className={`block text-sm font-medium text-cg-text ${className}`}
      {...props}
    >
      {children}
    </label>
  ),
);

Label.displayName = 'Label';

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  ({ className = '', ...props }, ref) => (
    <input
      ref={ref}
      className={`w-full rounded-cg-md border border-cg-border bg-cg-surface px-3 py-2 text-cg-text placeholder:text-cg-text-muted focus:border-cg-focus focus:outline-none focus:ring-2 focus:ring-cg-focus disabled:bg-cg-surface-raised disabled:opacity-60 min-h-touch ${className}`}
      {...props}
    />
  ),
);

Input.displayName = 'Input';

export const TextArea = forwardRef<HTMLTextAreaElement, React.TextareaHTMLAttributes<HTMLTextAreaElement>>(
  ({ className = '', ...props }, ref) => (
    <textarea
      ref={ref}
      className={`w-full rounded-cg-md border border-cg-border bg-cg-surface px-3 py-2 text-cg-text placeholder:text-cg-text-muted focus:border-cg-focus focus:outline-none focus:ring-2 focus:ring-cg-focus disabled:bg-cg-surface-raised disabled:opacity-60 ${className}`}
      {...props}
    />
  ),
);

TextArea.displayName = 'TextArea';

export const Select = forwardRef<HTMLSelectElement, React.SelectHTMLAttributes<HTMLSelectElement>>(
  ({ className = '', children, ...props }, ref) => (
    <select
      ref={ref}
      className={`w-full rounded-cg-md border border-cg-border bg-cg-surface px-3 py-2 text-cg-text focus:border-cg-focus focus:outline-none focus:ring-2 focus:ring-cg-focus disabled:bg-cg-surface-raised disabled:opacity-60 min-h-touch ${className}`}
      {...props}
    >
      {children}
    </select>
  ),
);

Select.displayName = 'Select';

const statusConfig: Record<
  string,
  { className: string; icon: string; label: string }
> = {
  pending: { className: 'bg-cg-info-bg text-cg-info', icon: '⏳', label: '待处理' },
  submitted: { className: 'bg-cg-info-bg text-cg-info', icon: '⏳', label: '已提交' },
  in_progress: { className: 'bg-cg-info-bg text-cg-info', icon: '▶', label: '进行中' },
  approved: { className: 'bg-cg-success-bg text-cg-success', icon: '✓', label: '已通过' },
  rejected: { className: 'bg-cg-warning-bg text-cg-warning', icon: '✕', label: '已驳回' },
  completed: { className: 'bg-cg-success-bg text-cg-success', icon: '✓', label: '已完成' },
  cancelled: { className: 'bg-cg-surface-raised text-cg-text-muted', icon: '⊘', label: '已取消' },
  fulfilled: { className: 'bg-cg-success-bg text-cg-success', icon: '✓', label: '已兑现' },
  pending_fulfillment: { className: 'bg-cg-info-bg text-cg-info', icon: '⏳', label: '待兑现' },
  refunded: { className: 'bg-cg-surface-raised text-cg-text-muted', icon: '↩', label: '已退款' },
  out_of_stock: { className: 'bg-cg-warning-bg text-cg-warning', icon: '!', label: '售罄' },
  default: { className: 'bg-cg-surface-raised text-cg-text-muted', icon: '•', label: '未知' },
};

export function StatusBadge({ status }: { status: string }) {
  const config = statusConfig[status.toLowerCase()] ?? statusConfig.default;
  return (
    <span
      className={`cg-badge ${config.className}`}
      role="status"
      aria-label={config.label}
    >
      <span aria-hidden="true">{config.icon}</span>
      {config.label}
    </span>
  );
}

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
      <Label htmlFor={htmlFor}>{label}</Label>
      {children}
      {error && (
        <p id={`${htmlFor}-error`} className="text-sm text-cg-danger" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
