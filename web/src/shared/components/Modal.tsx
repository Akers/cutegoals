import { ReactNode, useEffect, useRef } from 'react';
import { Button } from './Button';

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  footer?: ReactNode;
  size?: 'sm' | 'md' | 'lg';
}

export function Modal({ isOpen, onClose, title, children, footer, size = 'md' }: ModalProps) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const titleId = `${title}-title`;

  useEffect(() => {
    if (!isOpen) return;
    const previouslyFocused = document.activeElement as HTMLElement | null;
    dialogRef.current?.focus();

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };

    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      previouslyFocused?.focus();
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const widthClass = {
    sm: 'max-w-sm',
    md: 'max-w-md',
    lg: 'max-w-lg',
  }[size];

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50"
      role="presentation"
      onClick={onClose}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className={`w-full ${widthClass} bg-cg-surface rounded-cg-lg shadow-cg-lg border border-cg-border`}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-4 py-3 border-b border-cg-border">
          <h2 id={titleId} className="text-lg font-semibold text-cg-text">
            {title}
          </h2>
          <Button variant="ghost" size="sm" onClick={onClose} aria-label="关闭">
            ✕
          </Button>
        </div>
        <div className="px-4 py-4 max-h-[70vh] overflow-y-auto">{children}</div>
        {footer && (
          <div className="flex items-center justify-end gap-2 px-4 py-3 border-t border-cg-border">
            {footer}
          </div>
        )}
      </div>
    </div>
  );
}

interface ConfirmModalProps extends Omit<ModalProps, 'children' | 'footer'> {
  message: string;
  confirmText?: string;
  cancelText?: string;
  onConfirm: () => void;
  confirmVariant?: 'primary' | 'danger';
  isConfirming?: boolean;
}

export function ConfirmModal({
  message,
  confirmText = '确认',
  cancelText = '取消',
  onConfirm,
  confirmVariant = 'primary',
  isConfirming = false,
  ...modalProps
}: ConfirmModalProps) {
  return (
    <Modal
      {...modalProps}
      footer={
        <>
          <Button variant="secondary" onClick={modalProps.onClose} disabled={isConfirming}>
            {cancelText}
          </Button>
          <Button variant={confirmVariant} onClick={onConfirm} isLoading={isConfirming}>
            {confirmText}
          </Button>
        </>
      }
    >
      <p className="text-cg-text">{message}</p>
    </Modal>
  );
}
