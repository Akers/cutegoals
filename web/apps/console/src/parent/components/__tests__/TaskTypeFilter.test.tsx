import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TaskTypeFilter } from '../TaskTypeFilter';

describe('TaskTypeFilter - 任务类型筛选器', () => {
  it('渲染三个类型的复选框', () => {
    const onChange = vi.fn();
    render(<TaskTypeFilter selected={[]} onChange={onChange} />);
    expect(screen.getByLabelText('限时任务')).toBeInTheDocument();
    expect(screen.getByLabelText('重复任务')).toBeInTheDocument();
    expect(screen.getByLabelText('常驻任务')).toBeInTheDocument();
  });

  it('选中 LIMITED 时触发 onChange', async () => {
    const onChange = vi.fn();
    render(<TaskTypeFilter selected={[]} onChange={onChange} />);
    await userEvent.click(screen.getByLabelText('限时任务'));
    expect(onChange).toHaveBeenCalledWith(['LIMITED']);
  });

  it('选中 REPEAT 时触发 onChange', async () => {
    const onChange = vi.fn();
    render(<TaskTypeFilter selected={[]} onChange={onChange} />);
    await userEvent.click(screen.getByLabelText('重复任务'));
    expect(onChange).toHaveBeenCalledWith(['REPEAT']);
  });

  it('选中 STANDING 时触发 onChange', async () => {
    const onChange = vi.fn();
    render(<TaskTypeFilter selected={[]} onChange={onChange} />);
    await userEvent.click(screen.getByLabelText('常驻任务'));
    expect(onChange).toHaveBeenCalledWith(['STANDING']);
  });

  it('多选时传递所有选中值', async () => {
    const onChange = vi.fn();
    const { rerender } = render(<TaskTypeFilter selected={[]} onChange={onChange} />);
    await userEvent.click(screen.getByLabelText('限时任务'));
    expect(onChange).toHaveBeenCalledWith(['LIMITED']);

    // Re-render with updated selection and select another
    rerender(<TaskTypeFilter selected={['LIMITED']} onChange={onChange} />);
    await userEvent.click(screen.getByLabelText('重复任务'));
    expect(onChange).toHaveBeenCalledWith(['LIMITED', 'REPEAT']);
  });

  it('取消选中时移除该类型', async () => {
    const onChange = vi.fn();
    render(<TaskTypeFilter selected={['LIMITED', 'STANDING']} onChange={onChange} />);
    await userEvent.click(screen.getByLabelText('限时任务'));
    expect(onChange).toHaveBeenCalledWith(['STANDING']);
  });

  it('复选框状态反映选中状态', () => {
    const { rerender } = render(<TaskTypeFilter selected={['LIMITED']} onChange={vi.fn()} />);
    const limitedCheckbox = screen.getByLabelText('限时任务') as HTMLInputElement;
    const repeatCheckbox = screen.getByLabelText('重复任务') as HTMLInputElement;
    const standingCheckbox = screen.getByLabelText('常驻任务') as HTMLInputElement;
    expect(limitedCheckbox.checked).toBe(true);
    expect(repeatCheckbox.checked).toBe(false);
    expect(standingCheckbox.checked).toBe(false);

    rerender(<TaskTypeFilter selected={['REPEAT', 'STANDING']} onChange={vi.fn()} />);
    expect((screen.getByLabelText('限时任务') as HTMLInputElement).checked).toBe(false);
    expect((screen.getByLabelText('重复任务') as HTMLInputElement).checked).toBe(true);
    expect((screen.getByLabelText('常驻任务') as HTMLInputElement).checked).toBe(true);
  });

  it('无选中时全部不勾选', () => {
    render(<TaskTypeFilter selected={[]} onChange={vi.fn()} />);
    expect((screen.getByLabelText('限时任务') as HTMLInputElement).checked).toBe(false);
    expect((screen.getByLabelText('重复任务') as HTMLInputElement).checked).toBe(false);
    expect((screen.getByLabelText('常驻任务') as HTMLInputElement).checked).toBe(false);
  });
});
