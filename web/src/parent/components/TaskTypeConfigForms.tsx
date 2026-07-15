import { useCallback, useEffect, useState } from 'react';
import { Checkbox, Input, InputNumber, Radio, Select, Space, Typography } from 'antd';
import type { TaskTypeValue } from '@shared/api/types';
import { FormField } from '@shared/components';

/** 类型配置（Record 形式，序列化为 JSON） */
export type TypeConfigValue = Record<string, unknown>;

/** TaskTypeConfigForms 属性 */
export interface TaskTypeConfigFormsProps {
  taskType: TaskTypeValue | '';
  onTaskTypeChange: (value: TaskTypeValue | '') => void;
  typeConfig: TypeConfigValue;
  onTypeConfigChange: (config: TypeConfigValue) => void;
}

// ---- 星期标签 ----
const WEEKDAY_LABELS: { value: number; label: string }[] = [
  { value: 1, label: '周一' },
  { value: 2, label: '周二' },
  { value: 3, label: '周三' },
  { value: 4, label: '周四' },
  { value: 5, label: '周五' },
  { value: 6, label: '周六' },
  { value: 7, label: '周日' },
];

// ---- LIMITED 子表单 ----
function LimitedConfigForm({
  config,
  onChange,
}: {
  config: TypeConfigValue;
  onChange: (cfg: TypeConfigValue) => void;
}) {
  const [startDate, setStartDate] = useState((config.start_date as string) ?? '');
  const [endDate, setEndDate] = useState((config.end_date as string) ?? '');

  useEffect(() => {
    setStartDate((config.start_date as string) ?? '');
    setEndDate((config.end_date as string) ?? '');
  }, [config.start_date, config.end_date]);

  const handleStartChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const v = e.target.value;
      setStartDate(v);
      onChange({ start_date: v || undefined, end_date: endDate });
    },
    [endDate, onChange],
  );

  const handleEndChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const v = e.target.value;
      setEndDate(v);
      onChange({ start_date: startDate || undefined, end_date: v });
    },
    [startDate, onChange],
  );

  return (
    <Space direction="vertical" size="small" style={{ width: '100%' }}>
      <FormField label="开始日期（可选）" htmlFor="limited-start">
        <Input id="limited-start" type="date" value={startDate} onChange={handleStartChange} />
      </FormField>
      <FormField label="结束日期" htmlFor="limited-end">
        <Input id="limited-end" type="date" value={endDate} onChange={handleEndChange} />
      </FormField>
    </Space>
  );
}

// ---- REPEAT 子表单 ----
type Frequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';

/** 从 config 中提取 trigger_day 的指定字段 */
function getTriggerField<T>(config: TypeConfigValue, key: string, fallback: T): T {
  const td = config.trigger_day;
  if (td && typeof td === 'object' && key in (td as Record<string, unknown>)) {
    return (td as Record<string, unknown>)[key] as T;
  }
  return fallback;
}

function RepeatConfigForm({
  config,
  onChange,
}: {
  config: TypeConfigValue;
  onChange: (cfg: TypeConfigValue) => void;
}) {
  const [frequency, setFrequency] = useState<Frequency>(
    (config.frequency as Frequency) ?? 'DAILY',
  );
  const [weekday, setWeekday] = useState<number | null>(
    getTriggerField<number | null>(config, 'weekday', null),
  );
  const [monthlyMode, setMonthlyMode] = useState(
    getTriggerField<string>(config, 'mode', 'FIRST_DAY'),
  );
  const [yearMonth, setYearMonth] = useState(
    String(getTriggerField<number>(config, 'month', 1)),
  );
  const [yearDay, setYearDay] = useState(
    String(getTriggerField<number>(config, 'day', 1)),
  );

  // 外部 config 变化时同步到本地状态
  useEffect(() => {
    setFrequency((config.frequency as Frequency) ?? 'DAILY');
    const td = config.trigger_day;
    if (td && typeof td === 'object') {
      const obj = td as Record<string, unknown>;
      setWeekday('weekday' in obj ? (Number(obj.weekday) || null) : null);
      setMonthlyMode('mode' in obj ? ((obj.mode as string) ?? 'FIRST_DAY') : 'FIRST_DAY');
      setYearMonth('month' in obj ? String(obj.month ?? 1) : '1');
      setYearDay('day' in obj ? String(obj.day ?? 1) : '1');
    } else {
      setWeekday(null);
      setMonthlyMode('FIRST_DAY');
      setYearMonth('1');
      setYearDay('1');
    }
  }, [config.frequency, config.trigger_day]);

  /** 根据频率和当前配置生成 typeConfig 对象 */
  function buildConfig(
    freq: Frequency,
    wd: number | null,
    mm: string,
    ym: string,
    yd: string,
  ): TypeConfigValue {
    const cfg: TypeConfigValue = { frequency: freq };
    if (freq === 'WEEKLY') {
      if (wd != null) cfg.trigger_day = { weekday: wd };
    } else if (freq === 'MONTHLY') {
      cfg.trigger_day = { mode: mm };
    } else if (freq === 'YEARLY') {
      cfg.trigger_day = { month: Number(ym), day: Number(yd) };
    }
    return cfg;
  }

  const handleFrequencyChange = useCallback(
    (v: string) => {
      const freq = v as Frequency;
      setFrequency(freq);
      onChange(buildConfig(freq, null, 'FIRST_DAY', '1', '1'));
    },
    [onChange],
  );

  const handleWeekdayChange = useCallback(
    (day: number) => {
      setWeekday(day);
      onChange(buildConfig(frequency, day, monthlyMode, yearMonth, yearDay));
    },
    [frequency, monthlyMode, yearMonth, yearDay, onChange],
  );

  const handleMonthlyModeChange = useCallback(
    (v: string) => {
      setMonthlyMode(v);
      onChange(buildConfig(frequency, weekday, v, yearMonth, yearDay));
    },
    [frequency, weekday, yearMonth, yearDay, onChange],
  );

  // 年月日变更时重新通知
  useEffect(() => {
    if (frequency === 'YEARLY') {
      onChange(buildConfig(frequency, weekday, monthlyMode, yearMonth, yearDay));
    }
  }, [frequency, yearMonth, yearDay, weekday, monthlyMode, onChange]);

  return (
    <Space direction="vertical" size="small" style={{ width: '100%' }}>
      <FormField label="频率" htmlFor="repeat-frequency">
        <Select id="repeat-frequency" value={frequency} onChange={handleFrequencyChange} style={{ width: '100%' }}>
          <Select.Option value="DAILY">每天</Select.Option>
          <Select.Option value="WEEKLY">每周</Select.Option>
          <Select.Option value="MONTHLY">每月</Select.Option>
          <Select.Option value="YEARLY">每年</Select.Option>
        </Select>
      </FormField>

      {frequency === 'WEEKLY' && (
        <fieldset>
          <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>选择星期</Typography.Text>
          <Radio.Group value={weekday} onChange={(e) => handleWeekdayChange(e.target.value)}>
            <Space wrap>
              {WEEKDAY_LABELS.map((wd) => (
                <Radio key={wd.value} value={wd.value}>
                  {wd.label}
                </Radio>
              ))}
            </Space>
          </Radio.Group>
        </fieldset>
      )}

      {frequency === 'MONTHLY' && (
        <FormField label="模式" htmlFor="repeat-monthly-mode">
          <Select id="repeat-monthly-mode" value={monthlyMode} onChange={handleMonthlyModeChange} style={{ width: '100%' }}>
            <Select.Option value="FIRST_DAY">月初</Select.Option>
            <Select.Option value="LAST_DAY">月末</Select.Option>
            <Select.Option value="MID_MONTH">月中</Select.Option>
          </Select>
        </FormField>
      )}

      {frequency === 'YEARLY' && (
        <Space>
          <FormField label="月份" htmlFor="repeat-year-month">
            <Select id="repeat-year-month" value={yearMonth} onChange={(v) => setYearMonth(v)} style={{ width: 120 }}>
              {Array.from({ length: 12 }, (_, i) => (
                <Select.Option key={i + 1} value={String(i + 1)}>
                  {i + 1} 月
                </Select.Option>
              ))}
            </Select>
          </FormField>
          <FormField label="日期" htmlFor="repeat-year-day">
            <Select id="repeat-year-day" value={yearDay} onChange={(v) => setYearDay(v)} style={{ width: 120 }}>
              {Array.from({ length: 31 }, (_, i) => (
                <Select.Option key={i + 1} value={String(i + 1)}>
                  {i + 1} 日
                </Select.Option>
              ))}
            </Select>
          </FormField>
        </Space>
      )}
    </Space>
  );
}

// ---- STANDING 子表单 ----
function StandingConfigForm({
  config,
  onChange,
}: {
  config: TypeConfigValue;
  onChange: (cfg: TypeConfigValue) => void;
}) {
  const [unlimited, setUnlimited] = useState(config.max_submissions === null);
  const [maxSubmissions, setMaxSubmissions] = useState(
    config.max_submissions != null ? String(config.max_submissions) : '1',
  );

  useEffect(() => {
    setUnlimited(config.max_submissions === null);
    if (config.max_submissions != null) {
      setMaxSubmissions(String(config.max_submissions));
    }
  }, [config.max_submissions]);

  const handleUnlimitedToggle = useCallback(
    (e: { target: { checked: boolean } }) => {
      const v = e.target.checked;
      setUnlimited(v);
      if (v) {
        onChange({ max_submissions: null });
      } else {
        onChange({ max_submissions: Number(maxSubmissions) || 1 });
      }
    },
    [maxSubmissions, onChange],
  );

  const handleMaxChange = useCallback(
    (v: number | null) => {
      setMaxSubmissions(v != null ? String(v) : '');
      if (v != null && v >= 1) {
        onChange({ max_submissions: v });
      }
    },
    [onChange],
  );

  return (
    <Space direction="vertical" size="small" style={{ width: '100%' }}>
      <Checkbox checked={unlimited} onChange={handleUnlimitedToggle}>
        无限提交
      </Checkbox>

      {!unlimited && (
        <FormField label="最大提交次数" htmlFor="standing-max">
          <InputNumber
            id="standing-max"
            min={1}
            max={10000}
            value={maxSubmissions ? Number(maxSubmissions) : undefined}
            onChange={handleMaxChange}
            style={{ width: '100%' }}
          />
        </FormField>
      )}
    </Space>
  );
}

/**
 * TaskTypeConfigForms —— 任务类型选择器 + 按类型动态渲染配置子表单。
 *
 * 用法：
 * ```tsx
 * const [taskType, setTaskType] = useState<TaskTypeValue | ''>('');
 * const [typeConfig, setTypeConfig] = useState<TypeConfigValue>({});
 *
 * <TaskTypeConfigForms
 *   taskType={taskType}
 *   onTaskTypeChange={setTaskType}
 *   typeConfig={typeConfig}
 *   onTypeConfigChange={setTypeConfig}
 * />
 * ```
 */
export function TaskTypeConfigForms({
  taskType,
  onTaskTypeChange,
  typeConfig,
  onTypeConfigChange,
}: TaskTypeConfigFormsProps) {
  const handleTaskTypeChange = useCallback(
    (v: string) => {
      const value = v as TaskTypeValue | '';
      onTaskTypeChange(value);
      // 切换类型时重置配置为默认值（空字符串属性在序列化为 JSON 时会保留，故省略）
      if (value === 'LIMITED') {
        onTypeConfigChange({ end_date: '' });
      } else if (value === 'REPEAT') {
        onTypeConfigChange({ frequency: 'DAILY' });
      } else if (value === 'STANDING') {
        onTypeConfigChange({ max_submissions: null });
      } else {
        onTypeConfigChange({});
      }
    },
    [onTaskTypeChange, onTypeConfigChange],
  );

  return (
    <Space direction="vertical" size="small" style={{ width: '100%' }}>
      <FormField label="任务类型" htmlFor="task-type">
        <Select id="task-type" value={taskType} onChange={handleTaskTypeChange} style={{ width: '100%' }}>
          <Select.Option value="">选择任务类型</Select.Option>
          <Select.Option value="LIMITED">限时任务</Select.Option>
          <Select.Option value="REPEAT">重复任务</Select.Option>
          <Select.Option value="STANDING">常驻任务</Select.Option>
        </Select>
      </FormField>

      {taskType === 'LIMITED' && (
        <LimitedConfigForm config={typeConfig} onChange={onTypeConfigChange} />
      )}

      {taskType === 'REPEAT' && (
        <RepeatConfigForm config={typeConfig} onChange={onTypeConfigChange} />
      )}

      {taskType === 'STANDING' && (
        <StandingConfigForm config={typeConfig} onChange={onTypeConfigChange} />
      )}
    </Space>
  );
}
