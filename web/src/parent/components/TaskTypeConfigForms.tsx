import { useEffect, useState, useCallback } from 'react';
import { FormField, Input, Select } from '@shared/components';
import type { TaskTypeValue } from '@shared/api/types';

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
const WEEKDAY_LABELS: { value: string; label: string }[] = [
  { value: '1', label: '周一' },
  { value: '2', label: '周二' },
  { value: '3', label: '周三' },
  { value: '4', label: '周四' },
  { value: '5', label: '周五' },
  { value: '6', label: '周六' },
  { value: '7', label: '周日' },
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
    <div className="space-y-3">
      <FormField label="开始日期（可选）" htmlFor="limited-start">
        <Input id="limited-start" type="date" value={startDate} onChange={handleStartChange} />
      </FormField>
      <FormField label="结束日期" htmlFor="limited-end">
        <Input id="limited-end" type="date" value={endDate} onChange={handleEndChange} />
      </FormField>
    </div>
  );
}

// ---- REPEAT 子表单 ----
type Frequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';

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
  const [triggerDays, setTriggerDays] = useState<string[]>(
    config.trigger_day ? String(config.trigger_day).split(',').filter(Boolean) : [],
  );
  const [monthlyMode, setMonthlyMode] = useState(
    (config.trigger_day as string) ?? 'FIRST_DAY',
  );
  const [yearMonth, setYearMonth] = useState('1');
  const [yearDay, setYearDay] = useState('1');

  useEffect(() => {
    setFrequency((config.frequency as Frequency) ?? 'DAILY');
    if (config.frequency === 'WEEKLY' && config.trigger_day) {
      setTriggerDays(String(config.trigger_day).split(',').filter(Boolean));
    }
  }, [config.frequency, config.trigger_day]);

  const buildConfig = useCallback(
    (freq: Frequency, tDays: string[], mMode?: string): TypeConfigValue => {
      const cfg: TypeConfigValue = { frequency: freq };
      if (freq === 'WEEKLY') {
        const sorted = tDays.sort((a, b) => Number(a) - Number(b)).join(',');
        if (sorted) cfg.trigger_day = sorted;
      } else if (freq === 'MONTHLY') {
        cfg.trigger_day = mMode ?? 'FIRST_DAY';
      } else if (freq === 'YEARLY') {
        cfg.trigger_day = `${yearMonth}-${yearDay}`;
      }
      return cfg;
    },
    [yearMonth, yearDay],
  );

  const handleFrequencyChange = useCallback(
    (e: React.ChangeEvent<HTMLSelectElement>) => {
      const v = e.target.value as Frequency;
      setFrequency(v);
      if (v === 'WEEKLY') {
        setTriggerDays([]);
        onChange(buildConfig(v, []));
      } else if (v === 'MONTHLY') {
        setMonthlyMode('FIRST_DAY');
        onChange(buildConfig(v, [], 'FIRST_DAY'));
      } else if (v === 'YEARLY') {
        onChange(buildConfig(v, []));
      } else {
        onChange(buildConfig(v, []));
      }
    },
    [onChange, buildConfig],
  );

  const handleWeekdayToggle = useCallback(
    (day: string) => {
      const next = triggerDays.includes(day)
        ? triggerDays.filter((d) => d !== day)
        : [...triggerDays, day];
      setTriggerDays(next);
      onChange(buildConfig(frequency, next));
    },
    [frequency, triggerDays, onChange, buildConfig],
  );

  const handleMonthlyModeChange = useCallback(
    (e: React.ChangeEvent<HTMLSelectElement>) => {
      const v = e.target.value;
      setMonthlyMode(v);
      onChange(buildConfig(frequency, triggerDays, v));
    },
    [frequency, triggerDays, onChange, buildConfig],
  );

  // 年月日变更时重新通知。此处依赖 frequency/onChange 变化时无需执行，
  // 因为 onChange 引用稳定（来自父组件 render），且 YEARLY 分支仅需在 yearMonth/yearDay 变化时重新触发。
  useEffect(() => {
    if (frequency === 'YEARLY') {
      onChange({ frequency: 'YEARLY', trigger_day: `${yearMonth}-${yearDay}` });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [yearMonth, yearDay]);

  return (
    <div className="space-y-3">
      <FormField label="频率" htmlFor="repeat-frequency">
        <Select id="repeat-frequency" value={frequency} onChange={handleFrequencyChange}>
          <option value="DAILY">每天</option>
          <option value="WEEKLY">每周</option>
          <option value="MONTHLY">每月</option>
          <option value="YEARLY">每年</option>
        </Select>
      </FormField>

      {frequency === 'WEEKLY' && (
        <fieldset>
          <legend className="mb-2 text-sm font-medium text-cg-text">选择星期</legend>
          <div className="flex flex-wrap gap-3">
            {WEEKDAY_LABELS.map((wd) => (
              <label key={wd.value} className="flex items-center gap-1 text-sm text-cg-text">
                <input
                  type="checkbox"
                  value={wd.value}
                  checked={triggerDays.includes(wd.value)}
                  onChange={() => handleWeekdayToggle(wd.value)}
                  className="h-4 w-4 rounded border-cg-border text-cg-focus focus:ring-cg-focus"
                  aria-label={wd.label}
                />
                {wd.label}
              </label>
            ))}
          </div>
        </fieldset>
      )}

      {frequency === 'MONTHLY' && (
        <FormField label="模式" htmlFor="repeat-monthly-mode">
          <Select id="repeat-monthly-mode" value={monthlyMode} onChange={handleMonthlyModeChange}>
            <option value="FIRST_DAY">月初</option>
            <option value="LAST_DAY">月末</option>
            <option value="MID_MONTH">月中</option>
          </Select>
        </FormField>
      )}

      {frequency === 'YEARLY' && (
        <div className="flex gap-4">
          <FormField label="月份" htmlFor="repeat-year-month">
            <Select id="repeat-year-month" value={yearMonth} onChange={(e) => setYearMonth(e.target.value)}>
              {Array.from({ length: 12 }, (_, i) => (
                <option key={i + 1} value={String(i + 1)}>
                  {i + 1} 月
                </option>
              ))}
            </Select>
          </FormField>
          <FormField label="日期" htmlFor="repeat-year-day">
            <Select id="repeat-year-day" value={yearDay} onChange={(e) => setYearDay(e.target.value)}>
              {Array.from({ length: 31 }, (_, i) => (
                <option key={i + 1} value={String(i + 1)}>
                  {i + 1} 日
                </option>
              ))}
            </Select>
          </FormField>
        </div>
      )}
    </div>
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
    (e: React.ChangeEvent<HTMLInputElement>) => {
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
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const v = e.target.value;
      setMaxSubmissions(v);
      const parsed = parseInt(v, 10);
      if (!isNaN(parsed) && parsed >= 1) {
        onChange({ max_submissions: parsed });
      }
    },
    [onChange],
  );

  return (
    <div className="space-y-3">
      <label className="flex items-center gap-2 text-sm text-cg-text">
        <input
          type="checkbox"
          checked={unlimited}
          onChange={handleUnlimitedToggle}
          className="h-4 w-4 rounded border-cg-border text-cg-focus focus:ring-cg-focus"
        />
        无限提交
      </label>

      {!unlimited && (
        <FormField label="最大提交次数" htmlFor="standing-max">
          <Input
            id="standing-max"
            type="number"
            min="1"
            max="10000"
            value={maxSubmissions}
            onChange={handleMaxChange}
          />
        </FormField>
      )}
    </div>
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
    (e: React.ChangeEvent<HTMLSelectElement>) => {
      const v = e.target.value as TaskTypeValue | '';
      onTaskTypeChange(v);
      // 切换类型时重置配置为默认值（空字符串属性在序列化为 JSON 时会保留，故省略）
      if (v === 'LIMITED') {
        onTypeConfigChange({ end_date: '' });
      } else if (v === 'REPEAT') {
        onTypeConfigChange({ frequency: 'DAILY' });
      } else if (v === 'STANDING') {
        onTypeConfigChange({ max_submissions: null });
      } else {
        onTypeConfigChange({});
      }
    },
    [onTaskTypeChange, onTypeConfigChange],
  );

  return (
    <div className="space-y-4">
      <FormField label="任务类型" htmlFor="task-type">
        <Select id="task-type" value={taskType} onChange={handleTaskTypeChange}>
          <option value="">选择任务类型</option>
          <option value="LIMITED">限时任务</option>
          <option value="REPEAT">重复任务</option>
          <option value="STANDING">常驻任务</option>
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
    </div>
  );
}
