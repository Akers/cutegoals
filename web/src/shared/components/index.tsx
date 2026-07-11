export { Button } from './Button';
export type { ButtonVariant, ButtonSize } from './Button';
export { Modal, ConfirmModal } from './Modal';
export { ToastProvider, useToast } from './Toast';
export { Pagination } from './Pagination';
export { EmptyState, ErrorState, LoadingState, OfflineState } from './States';
export { ErrorBoundary, GlobalErrorFallback } from './ErrorBoundary';
export { PageHeader, Layout } from './Layout';
export { Label, Input, TextArea, Select, StatusBadge, FormField } from './Form';
export function CardSection({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="cg-card p-4">
      <h2 className="mb-3 text-lg font-semibold text-cg-text">{title}</h2>
      {children}
    </section>
  );
}
