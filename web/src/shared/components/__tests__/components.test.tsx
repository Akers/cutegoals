import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Button } from '../Button';
import { Modal, ConfirmModal } from '../Modal';
import { ToastProvider, useToast } from '../Toast';
import { Pagination } from '../Pagination';
import { EmptyState } from '../States';
import { ErrorBoundary } from '../ErrorBoundary';

describe('Button', () => {
  it('renders with default variant and responds to click', async () => {
    const handleClick = vi.fn();
    render(<Button onClick={handleClick}>确认</Button>);
    const button = screen.getByRole('button', { name: /确认/ });
    expect(button).toBeInTheDocument();
    await userEvent.click(button);
    expect(handleClick).toHaveBeenCalledTimes(1);
  });

  it('shows loading state and disables clicks', async () => {
    const handleClick = vi.fn();
    render(<Button onClick={handleClick} isLoading>保存中</Button>);
    const button = screen.getByRole('button', { name: /保存中/ });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');
    await userEvent.click(button);
    expect(handleClick).not.toHaveBeenCalled();
  });

  it('renders danger variant', () => {
    render(<Button variant="danger">删除</Button>);
    expect(screen.getByRole('button', { name: /删除/ })).toBeInTheDocument();
  });
});

describe('Modal', () => {
  it('renders when open and closes on backdrop click', async () => {
    const onClose = vi.fn();
    render(
      <Modal isOpen title="测试弹窗" onClose={onClose}>
        <p>弹窗内容</p>
      </Modal>,
    );
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText('弹窗内容')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('presentation'));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('closes on Escape key', async () => {
    const onClose = vi.fn();
    render(
      <Modal isOpen title="测试弹窗" onClose={onClose}>
        <p>弹窗内容</p>
      </Modal>,
    );
    await userEvent.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalledOnce();
  });
});

describe('ConfirmModal', () => {
  it('calls onConfirm when confirmed', async () => {
    const onConfirm = vi.fn();
    render(
      <ConfirmModal
        isOpen
        title="确认？"
        message="真的要继续吗？"
        onClose={() => {}}
        onConfirm={onConfirm}
      />,
    );
    await userEvent.click(screen.getByRole('button', { name: /确认/ }));
    expect(onConfirm).toHaveBeenCalledOnce();
  });
});

function ToastTester() {
  const { showToast } = useToast();
  return (
    <button onClick={() => showToast('操作成功', 'success')}>
      显示通知
    </button>
  );
}

describe('Toast', () => {
  it('shows and dismisses a toast', async () => {
    render(
      <ToastProvider>
        <ToastTester />
      </ToastProvider>,
    );
    await userEvent.click(screen.getByRole('button', { name: /显示通知/ }));
    expect(screen.getByRole('status')).toHaveTextContent('操作成功');
    await userEvent.click(screen.getByRole('button', { name: /关闭通知/ }));
    await waitFor(() => {
      expect(screen.queryByRole('status')).not.toBeInTheDocument();
    });
  });
});

describe('Pagination', () => {
  it('navigates between pages', async () => {
    const onPageChange = vi.fn();
    render(
      <Pagination currentPage={1} totalPages={3} onPageChange={onPageChange} />,
    );
    await userEvent.click(screen.getByRole('button', { name: /下一页/ }));
    expect(onPageChange).toHaveBeenCalledWith(2);
  });

  it('disables previous on the first page', () => {
    render(<Pagination currentPage={1} totalPages={3} onPageChange={() => {}} />);
    expect(screen.getByRole('button', { name: /上一页/ })).toBeDisabled();
  });

  it('disables next on the last page', () => {
    render(<Pagination currentPage={3} totalPages={3} onPageChange={() => {}} />);
    expect(screen.getByRole('button', { name: /下一页/ })).toBeDisabled();
  });
});

describe('EmptyState', () => {
  it('renders title and description', () => {
    render(<EmptyState title="没有数据" description="请稍后重试" />);
    expect(screen.getByRole('status')).toHaveTextContent('没有数据');
    expect(screen.getByText('请稍后重试')).toBeInTheDocument();
  });

  it('calls action on click', async () => {
    const action = vi.fn();
    render(<EmptyState title="没有数据" action={{ label: '刷新', onClick: action }} />,
    );
    await userEvent.click(screen.getByRole('button', { name: /刷新/ }));
    expect(action).toHaveBeenCalledOnce();
  });
});

function ThrowError(): never {
  throw new Error('测试错误');
}

describe('ErrorBoundary', () => {
  it('renders fallback when child throws', () => {
    render(
      <ErrorBoundary>
        <ThrowError />
      </ErrorBoundary>,
    );
    expect(screen.getByRole('alert')).toHaveTextContent(/页面出现错误/);
  });

  it('renders custom fallback', () => {
    render(
      <ErrorBoundary fallback={<div>自定义错误</div>}>
        <ThrowError />
      </ErrorBoundary>,
    );
    expect(screen.getByText('自定义错误')).toBeInTheDocument();
  });
});
