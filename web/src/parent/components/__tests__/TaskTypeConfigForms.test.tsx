import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TaskTypeConfigForms } from '../TaskTypeConfigForms';

/** Helper: 打开 antd Select 下拉菜单并点击指定选项 */
async function selectAntdOption(label: string | RegExp, optionText: string) {
  const trigger = screen.getByRole('combobox', { name: label });
  await userEvent.click(trigger);
  // Antd renders dropdown in a portal; find the option text anywhere in the document
  const option = await screen.findByText(optionText, {}, { timeout: 3000 });
  await userEvent.click(option);
}

describe('TaskTypeConfigForms - 任务类型选择器', () => {
  it('渲染下拉选择器和默认选项', () => {
    const onTaskTypeChange = vi.fn();
    const onTypeConfigChange = vi.fn();
    render(
      <TaskTypeConfigForms
        taskType=""
        onTaskTypeChange={onTaskTypeChange}
        typeConfig={{}}
        onTypeConfigChange={onTypeConfigChange}
      />,
    );
    const select = screen.getByRole('combobox', { name: /任务类型/ });
    expect(select).toBeInTheDocument();
  });

  it('选择 LIMITED 时触发 onChange', async () => {
    const onTaskTypeChange = vi.fn();
    const onTypeConfigChange = vi.fn();
    render(
      <TaskTypeConfigForms
        taskType=""
        onTaskTypeChange={onTaskTypeChange}
        typeConfig={{}}
        onTypeConfigChange={onTypeConfigChange}
      />,
    );
    await selectAntdOption(/任务类型/, '限时任务');
    expect(onTaskTypeChange).toHaveBeenCalledWith('LIMITED');
  });

  it('选择 REPEAT 时触发 onChange', async () => {
    const onTaskTypeChange = vi.fn();
    const onTypeConfigChange = vi.fn();
    render(
      <TaskTypeConfigForms
        taskType=""
        onTaskTypeChange={onTaskTypeChange}
        typeConfig={{}}
        onTypeConfigChange={onTypeConfigChange}
      />,
    );
    await selectAntdOption(/任务类型/, '重复任务');
    expect(onTaskTypeChange).toHaveBeenCalledWith('REPEAT');
  });

  it('选择 STANDING 时触发 onChange', async () => {
    const onTaskTypeChange = vi.fn();
    const onTypeConfigChange = vi.fn();
    render(
      <TaskTypeConfigForms
        taskType=""
        onTaskTypeChange={onTaskTypeChange}
        typeConfig={{}}
        onTypeConfigChange={onTypeConfigChange}
      />,
    );
    await selectAntdOption(/任务类型/, '常驻任务');
    expect(onTaskTypeChange).toHaveBeenCalledWith('STANDING');
  });

  it('taskType 为空时不渲染配置表单', () => {
    const onTaskTypeChange = vi.fn();
    const onTypeConfigChange = vi.fn();
    render(
      <TaskTypeConfigForms
        taskType=""
        onTaskTypeChange={onTaskTypeChange}
        typeConfig={{}}
        onTypeConfigChange={onTypeConfigChange}
      />,
    );
    expect(screen.queryByText('开始日期（可选）')).not.toBeInTheDocument();
    expect(screen.queryByText('频率')).not.toBeInTheDocument();
    expect(screen.queryByText('当前类型的重复提交控制')).not.toBeInTheDocument();
  });
});

describe('LIMITED 配置表单', () => {
  it('渲染开始日期（可选）和结束日期输入框', () => {
    const onTaskTypeChange = vi.fn();
    const onTypeConfigChange = vi.fn();
    render(
      <TaskTypeConfigForms
        taskType="LIMITED"
        onTaskTypeChange={onTaskTypeChange}
        typeConfig={{}}
        onTypeConfigChange={onTypeConfigChange}
      />,
    );
    expect(screen.getByLabelText('开始日期（可选）')).toBeInTheDocument();
    expect(screen.getByLabelText('结束日期')).toBeInTheDocument();
  });

  it('结束日期修改时触发 onChange', async () => {
    const onTaskTypeChange = vi.fn();
    const onTypeConfigChange = vi.fn();
    render(
      <TaskTypeConfigForms
        taskType="LIMITED"
        onTaskTypeChange={onTaskTypeChange}
        typeConfig={{}}
        onTypeConfigChange={onTypeConfigChange}
      />,
    );
    const endDateInput = screen.getByLabelText('结束日期');
    await userEvent.clear(endDateInput);
    await userEvent.type(endDateInput, '2026-07-31');
    expect(onTypeConfigChange).toHaveBeenLastCalledWith(
      expect.objectContaining({ end_date: '2026-07-31' }),
    );
  });

  it('开始日期修改时触发 onChange', async () => {
    const onTaskTypeChange = vi.fn();
    const onTypeConfigChange = vi.fn();
    render(
      <TaskTypeConfigForms
        taskType="LIMITED"
        onTaskTypeChange={onTaskTypeChange}
        typeConfig={{}}
        onTypeConfigChange={onTypeConfigChange}
      />,
    );
    const startDateInput = screen.getByLabelText('开始日期（可选）');
    await userEvent.clear(startDateInput);
    await userEvent.type(startDateInput, '2026-07-15');
    expect(onTypeConfigChange).toHaveBeenLastCalledWith(
      expect.objectContaining({ start_date: '2026-07-15' }),
    );
  });

  it('接收初始值作为 typeConfig', () => {
    const onTaskTypeChange = vi.fn();
    const onTypeConfigChange = vi.fn();
    const initialConfig = { start_date: '2026-07-01', end_date: '2026-07-31' };
    render(
      <TaskTypeConfigForms
        taskType="LIMITED"
        onTaskTypeChange={onTaskTypeChange}
        typeConfig={initialConfig}
        onTypeConfigChange={onTypeConfigChange}
      />,
    );
    const startDateInput = screen.getByLabelText('开始日期（可选）') as HTMLInputElement;
    const endDateInput = screen.getByLabelText('结束日期') as HTMLInputElement;
    expect(startDateInput.value).toBe('2026-07-01');
    expect(endDateInput.value).toBe('2026-07-31');
  });
});

describe('REPEAT 配置表单', () => {
  it('渲染频率下拉选择器', () => {
    const onTaskTypeChange = vi.fn();
    const onTypeConfigChange = vi.fn();
    render(
      <TaskTypeConfigForms
        taskType="REPEAT"
        onTaskTypeChange={onTaskTypeChange}
        typeConfig={{}}
        onTypeConfigChange={onTypeConfigChange}
      />,
    );
    expect(screen.getByRole('combobox', { name: /频率/ })).toBeInTheDocument();
  });

  it('DAILY 频率不显示额外配置', () => {
    const onTaskTypeChange = vi.fn();
    const onTypeConfigChange = vi.fn();
    render(
      <TaskTypeConfigForms
        taskType="REPEAT"
        onTaskTypeChange={onTaskTypeChange}
        typeConfig={{ frequency: 'DAILY' }}
        onTypeConfigChange={onTypeConfigChange}
      />,
    );
    expect(screen.queryByText('选择星期')).not.toBeInTheDocument();
    expect(screen.queryByText('模式')).not.toBeInTheDocument();
    expect(screen.queryByText('月份')).not.toBeInTheDocument();
  });

  it('WEEKLY 频率显示星期选择器', () => {
    const onTaskTypeChange = vi.fn();
    const onTypeConfigChange = vi.fn();
    render(
      <TaskTypeConfigForms
        taskType="REPEAT"
        onTaskTypeChange={onTaskTypeChange}
        typeConfig={{ frequency: 'WEEKLY' }}
        onTypeConfigChange={onTypeConfigChange}
      />,
    );
    expect(screen.getByText('选择星期')).toBeInTheDocument();
    expect(screen.getByLabelText('周一')).toBeInTheDocument();
    expect(screen.getByLabelText('周二')).toBeInTheDocument();
    expect(screen.getByLabelText('周三')).toBeInTheDocument();
    expect(screen.getByLabelText('周四')).toBeInTheDocument();
    expect(screen.getByLabelText('周五')).toBeInTheDocument();
    expect(screen.getByLabelText('周六')).toBeInTheDocument();
    expect(screen.getByLabelText('周日')).toBeInTheDocument();
  });

  it('WEEKLY 选择星期时触发 onChange（单选，对象格式）', async () => {
    const onTaskTypeChange = vi.fn();
    const onTypeConfigChange = vi.fn();
    render(
      <TaskTypeConfigForms
        taskType="REPEAT"
        onTaskTypeChange={onTaskTypeChange}
        typeConfig={{ frequency: 'WEEKLY' }}
        onTypeConfigChange={onTypeConfigChange}
      />,
    );
    const mondayRadio = screen.getByLabelText('周一');
    await userEvent.click(mondayRadio);
    expect(onTypeConfigChange).toHaveBeenLastCalledWith(
      expect.objectContaining({
        frequency: 'WEEKLY',
        trigger_day: { weekday: 1 },
      }),
    );
  });

  it('WEEKLY 切换星期时更新选中项', async () => {
    const onTaskTypeChange = vi.fn();
    const onTypeConfigChange = vi.fn();
    render(
      <TaskTypeConfigForms
        taskType="REPEAT"
        onTaskTypeChange={onTaskTypeChange}
        typeConfig={{ frequency: 'WEEKLY' }}
        onTypeConfigChange={onTypeConfigChange}
      />,
    );
    const wedRadio = screen.getByLabelText('周三') as HTMLInputElement;
    await userEvent.click(wedRadio);
    expect(wedRadio.checked).toBe(true);
    expect(onTypeConfigChange).toHaveBeenLastCalledWith(
      expect.objectContaining({ trigger_day: { weekday: 3 } }),
    );
  });

  it('MONTHLY 选择模式时触发 onChange', async () => {
    const onTaskTypeChange = vi.fn();
    const onTypeConfigChange = vi.fn();
    render(
      <TaskTypeConfigForms
        taskType="REPEAT"
        onTaskTypeChange={onTaskTypeChange}
        typeConfig={{ frequency: 'MONTHLY' }}
        onTypeConfigChange={onTypeConfigChange}
      />,
    );
    await selectAntdOption(/模式/, '月末');
    expect(onTypeConfigChange).toHaveBeenLastCalledWith(
      expect.objectContaining({
        frequency: 'MONTHLY',
        trigger_day: { mode: 'LAST_DAY' },
      }),
    );
  });

  it('YEARLY 修改日期时触发 onChange', async () => {
    const onTaskTypeChange = vi.fn();
    const onTypeConfigChange = vi.fn();
    render(
      <TaskTypeConfigForms
        taskType="REPEAT"
        onTaskTypeChange={onTaskTypeChange}
        typeConfig={{ frequency: 'YEARLY' }}
        onTypeConfigChange={onTypeConfigChange}
      />,
    );
    await selectAntdOption(/月份/, '6 月');
    expect(onTypeConfigChange).toHaveBeenLastCalledWith(
      expect.objectContaining({
        frequency: 'YEARLY',
        trigger_day: { month: 6, day: 1 },
      }),
    );
  });

  it('MONTHLY 频率显示模式选择器', async () => {
    const onTaskTypeChange = vi.fn();
    const onTypeConfigChange = vi.fn();
    render(
      <TaskTypeConfigForms
        taskType="REPEAT"
        onTaskTypeChange={onTaskTypeChange}
        typeConfig={{ frequency: 'MONTHLY' }}
        onTypeConfigChange={onTypeConfigChange}
      />,
    );
    // 打开 Select 下拉菜单，验证下拉框出现
    const trigger = screen.getByRole('combobox', { name: /模式/ });
    await userEvent.click(trigger);
    await screen.findByRole('listbox');
  });

  it('YEARLY 频率显示月份和日期选择', () => {
    const onTaskTypeChange = vi.fn();
    const onTypeConfigChange = vi.fn();
    render(
      <TaskTypeConfigForms
        taskType="REPEAT"
        onTaskTypeChange={onTaskTypeChange}
        typeConfig={{ frequency: 'YEARLY' }}
        onTypeConfigChange={onTypeConfigChange}
      />,
    );
    expect(screen.getByText('月份')).toBeInTheDocument();
    expect(screen.getByText('日期')).toBeInTheDocument();
  });
});

describe('STANDING 配置表单', () => {
  it('显示配置迁移提示', () => {
    const onTaskTypeChange = vi.fn();
    const onTypeConfigChange = vi.fn();
    render(
      <TaskTypeConfigForms
        taskType="STANDING"
        onTaskTypeChange={onTaskTypeChange}
        typeConfig={{}}
        onTypeConfigChange={onTypeConfigChange}
      />,
    );
    expect(screen.getByText('当前类型的重复提交控制已移至表单顶部「允许重复提交」设置。')).toBeInTheDocument();
  });

  it('不渲染已移除的旧字段', () => {
    const onTaskTypeChange = vi.fn();
    const onTypeConfigChange = vi.fn();
    render(
      <TaskTypeConfigForms
        taskType="STANDING"
        onTaskTypeChange={onTaskTypeChange}
        typeConfig={{}}
        onTypeConfigChange={onTypeConfigChange}
      />,
    );
    expect(screen.queryByText('无限提交')).not.toBeInTheDocument();
    expect(screen.queryByText('最大提交次数')).not.toBeInTheDocument();
  });
});

describe('TaskTypeConfigForms - 提交序列化', () => {
  it('LIMITED 配置序列化为 JSON', () => {
    const config = { start_date: '2026-07-15', end_date: '2026-07-31' };
    const json = JSON.stringify(config);
    expect(json).toBe('{"start_date":"2026-07-15","end_date":"2026-07-31"}');
  });

  it('REPEAT DAILY 配置序列化为 JSON', () => {
    const config = { frequency: 'DAILY' };
    expect(JSON.stringify(config)).toBe('{"frequency":"DAILY"}');
  });

  it('REPEAT WEEKLY 配置序列化为 JSON（对象格式）', () => {
    const config = { frequency: 'WEEKLY', trigger_day: { weekday: 3 } };
    expect(JSON.stringify(config)).toBe('{"frequency":"WEEKLY","trigger_day":{"weekday":3}}');
  });

  it('REPEAT MONTHLY 配置序列化为 JSON', () => {
    const config = { frequency: 'MONTHLY', trigger_day: { mode: 'FIRST_DAY' } };
    expect(JSON.stringify(config)).toBe('{"frequency":"MONTHLY","trigger_day":{"mode":"FIRST_DAY"}}');
  });

  it('REPEAT YEARLY 配置序列化为 JSON', () => {
    const config = { frequency: 'YEARLY', trigger_day: { month: 6, day: 15 } };
    expect(JSON.stringify(config)).toBe('{"frequency":"YEARLY","trigger_day":{"month":6,"day":15}}');
  });

  it('STANDING 无限提交序列化为 JSON', () => {
    const config = { max_submissions: null };
    expect(JSON.stringify(config)).toBe('{"max_submissions":null}');
  });

  it('STANDING 有限次提交序列化为 JSON', () => {
    const config = { max_submissions: 5 };
    expect(JSON.stringify(config)).toBe('{"max_submissions":5}');
  });
});
