import { Component, ErrorInfo, ReactNode } from 'react';
import { ErrorState } from './States';
import { Button } from './Button';

interface ErrorBoundaryProps {
  children: ReactNode;
  fallback?: ReactNode;
  onError?: (error: Error, info: ErrorInfo) => void;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    this.props.onError?.(error, info);
  }

  handleReset = (): void => {
    this.setState({ hasError: false, error: null });
  };

  render(): ReactNode {
    if (this.state.hasError) {
      if (this.props.fallback) return this.props.fallback;
      return (
        <ErrorState
          title="页面出现错误"
          message={this.state.error?.message ?? '请刷新页面重试'}
          onRetry={() => window.location.reload()}
        />
      );
    }
    return this.props.children;
  }
}

export function GlobalErrorFallback({ onReset }: { onReset?: () => void }) {
  return (
    <div className="cg-page flex min-h-screen flex-col items-center justify-center gap-4">
      <h1 className="text-2xl font-bold text-cg-text">出错了</h1>
      <p className="text-cg-text-muted">应用遇到了无法恢复的问题，请刷新页面。</p>
      <Button onClick={() => (onReset ? onReset() : window.location.reload())}>刷新</Button>
    </div>
  );
}
