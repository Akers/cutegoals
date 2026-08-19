import { useCallback } from 'react';
import { Card, Checkbox, Space } from 'antd';
import type { TaskTypeValue } from '@shared/api/types';

export interface TaskTypeFilterProps {
  selected: TaskTypeValue[];
  onChange: (selected: TaskTypeValue[]) => void;
}

const TYPE_OPTIONS: { value: TaskTypeValue; label: string }[] = [
  { value: 'LIMITED', label: '限时任务' },
  { value: 'REPEAT', label: '重复任务' },
  { value: 'STANDING', label: '常驻任务' },
];

export function TaskTypeFilter({ selected, onChange }: TaskTypeFilterProps) {
  const handleToggle = useCallback(
    (value: TaskTypeValue) => {
      const next = selected.includes(value)
        ? selected.filter((t) => t !== value)
        : [...selected, value];
      onChange(next);
    },
    [selected, onChange],
  );

  return (
    <Card title="任务类型筛选">
      <Space wrap>
        {TYPE_OPTIONS.map((opt) => (
          <Checkbox
            key={opt.value}
            checked={selected.includes(opt.value)}
            onChange={() => handleToggle(opt.value)}
          >
            {opt.label}
          </Checkbox>
        ))}
      </Space>
    </Card>
  );
}
