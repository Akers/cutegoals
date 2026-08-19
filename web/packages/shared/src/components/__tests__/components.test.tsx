import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Button, Pagination } from 'antd';
import { ConfirmModal } from '../index';

describe('Button (antd)', () => {
  it('renders and responds to click', async () => {
    const handleClick = vi.fn();
    render(<Button onClick={handleClick}>确认</Button>);
    const button = screen.getByRole('button', { name: /确/ });
    expect(button).toBeInTheDocument();
    await userEvent.click(button);
    expect(handleClick).toHaveBeenCalledTimes(1);
  });

  it('renders danger variant', () => {
    render(<Button danger>删除</Button>);
    expect(screen.getByRole('button', { name: /删/ })).toBeInTheDocument();
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

describe('Pagination (antd)', () => {
  it('navigates between pages', async () => {
    const onChange = vi.fn();
    render(
      <Pagination current={1} total={30} pageSize={10} onChange={onChange} />,
    );
    await userEvent.click(screen.getByTitle('Next Page'));
    expect(onChange).toHaveBeenCalledWith(2, 10);
  });

  it('disables previous on the first page', () => {
    render(<Pagination current={1} total={30} pageSize={10} onChange={() => {}} />);
    expect(screen.getByTitle('Previous Page')).toHaveClass('ant-pagination-disabled');
  });
});
