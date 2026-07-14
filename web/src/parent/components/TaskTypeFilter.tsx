import { useCallback } from 'react';
import { CardSection } from '@shared/components';

/** 任务类型 */
export type TaskTypeValue = 'LIMITED' | 'REPEAT' | 'STANDING';

/** TaskTypeFilter 属性 */
export interface TaskTypeFilterProps {
  /** 当前选中的类型列表 */
  selected: TaskTypeValue[];
  /** 选中变化回调 */
  onChange: (selected: TaskTypeValue[]) => void;
}

/** 类型选项配置 */
const TYPE_OPTIONS: { value: TaskTypeValue; label: string }[] = [
  { value: 'LIMITED', label: '限时任务' },
  { value: 'REPEAT', label: '重复任务' },
  { value: 'STANDING', label: '常驻任务' },
];

/**
 * TaskTypeFilter —— 任务模板列表页的任务类型多选筛选器。
 *
 * 选中状态变化时拼接 `taskType=LIMITED,STANDING` 参数，
 * 无选中时（全部）不传 `taskType` 参数。
 *
 * 用法：
 * ```tsx
 * const [selectedTypes, setSelectedTypes] = useState<TaskTypeValue[]>([]);
 * <TaskTypeFilter selected={selectedTypes} onChange={setSelectedTypes} />
 * ```
 */
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
    <CardSection title="任务类型筛选">
      <div className="flex flex-wrap gap-4">
        {TYPE_OPTIONS.map((opt) => (
          <label key={opt.value} className="flex items-center gap-2 text-sm text-cg-text">
            <input
              type="checkbox"
              value={opt.value}
              checked={selected.includes(opt.value)}
              onChange={() => handleToggle(opt.value)}
              className="h-4 w-4 rounded border-cg-border text-cg-focus focus:ring-cg-focus"
            />
            {opt.label}
          </label>
        ))}
      </div>
    </CardSection>
  );
}
