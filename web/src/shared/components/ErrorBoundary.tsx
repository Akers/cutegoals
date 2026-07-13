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
      // 注意：ErrorBoundary 是 class component，不能用 useAuth/useRole hooks，
      // 也无法直接复用 <Layout>（Layout 内部强依赖这两个 hook）。
      // 这里用 inline 简化版页面骨架，保留 header + main + footer 视觉结构，
      // 让用户在 React 渲染崩溃时仍能看到主导航入口和错误信息，不至于全屏白屏。
      return (
        <div className="cg-page flex min-h-screen flex-col">
          <header className="border-b border-cg-border bg-cg-surface px-4 py-3">
            <div className="mx-auto flex max-w-6xl items-center justify-between">
              <a href="/" className="font-bold text-cg-text">CuteGoals</a>
              <a href="/" className="text-sm text-cg-text-muted hover:text-cg-text">返回首页</a>
            </div>
          </header>
          <main className="cg-main mx-auto w-full max-w-6xl flex-1 p-4">
            <ErrorState
              title="页面出现错误"
              message={this.state.error?.message ?? '请刷新页面重试'}
              onRetry={() => window.location.reload()}
            />
          </main>
          <footer className="border-t border-cg-border bg-cg-surface px-4 py-3 text-center text-xs text-cg-text-muted">
            CuteGoals
          </footer>
        </div>
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
