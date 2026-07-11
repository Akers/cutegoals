import { ButtonHTMLAttributes, forwardRef, ReactNode } from 'react';

export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost';
export type ButtonSize = 'sm' | 'md' | 'lg';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  isLoading?: boolean;
  leftIcon?: ReactNode;
  rightIcon?: ReactNode;
}

const variantClasses: Record<ButtonVariant, string> = {
  primary:
    'bg-cg-primary text-cg-primary-text hover:bg-cg-primary-hover active:bg-cg-primary-active border-transparent',
  secondary:
    'bg-cg-surface-raised text-cg-text hover:bg-cg-border border border-cg-border',
  danger:
    'bg-cg-danger text-cg-danger-text hover:bg-cg-danger-hover active:bg-cg-danger border-transparent',
  ghost:
    'bg-transparent text-cg-text hover:bg-cg-surface-raised border-transparent',
};

const sizeClasses: Record<ButtonSize, string> = {
  sm: 'px-3 py-1.5 text-sm min-h-touch min-w-touch',
  md: 'px-4 py-2 text-base min-h-touch min-w-touch',
  lg: 'px-6 py-3 text-lg min-h-touch min-w-touch',
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      variant = 'primary',
      size = 'md',
      isLoading = false,
      leftIcon,
      rightIcon,
      children,
      disabled,
      className = '',
      ...props
    },
    ref,
  ) => {
    const base =
      'inline-flex items-center justify-center gap-2 rounded-cg-md font-medium transition-colors duration-cg-fast focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cg-focus disabled:opacity-60 disabled:cursor-not-allowed';
    const classes = [base, variantClasses[variant], sizeClasses[size], className]
      .filter(Boolean)
      .join(' ');

    return (
      <button
        ref={ref}
        className={classes}
        disabled={disabled || isLoading}
        aria-busy={isLoading}
        {...props}
      >
        {isLoading && (
          <span
            className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent"
            aria-hidden="true"
          />
        )}
        {!isLoading && leftIcon}
        <span>{children}</span>
        {!isLoading && rightIcon}
      </button>
    );
  },
);

Button.displayName = 'Button';
