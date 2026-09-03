<template>
  <n-space vertical :size="12">
    <n-form-item label="任务类型" path="taskType">
      <n-select
        :value="taskType"
        :options="typeOptions"
        placeholder="选择任务类型"
        @update:value="handleTypeChange"
      />
    </n-form-item>

    <!-- 限时任务配置 -->
    <template v-if="taskType === 'LIMITED'">
      <n-form-item label="开始日期（可选）" path="limitedStart">
        <n-date-picker
          :value="limitedStartTs"
          type="date"
          style="width: 100%"
          clearable
          @update:formatted-value="(v: string | null) => emitLimited(v, limitedEnd)"
        />
      </n-form-item>
      <n-form-item label="结束日期" path="limitedEnd">
        <n-date-picker
          :value="limitedEndTs"
          type="date"
          style="width: 100%"
          clearable
          @update:formatted-value="(v: string | null) => emitLimited(limitedStart, v)"
        />
      </n-form-item>
    </template>

    <!-- 重复任务配置 -->
    <template v-else-if="taskType === 'REPEAT'">
      <n-form-item label="频率" path="frequency">
        <n-select
          :value="frequency"
          :options="frequencyOptions"
          @update:value="handleFrequencyChange"
        />
      </n-form-item>
      <n-form-item v-if="frequency === 'WEEKLY'" label="选择星期" path="weekday">
        <n-radio-group :value="weekday" @update:value="handleWeekdayChange">
          <n-space>
            <n-radio v-for="wd in weekdayLabels" :key="wd.value" :value="wd.value">
              {{ wd.label }}
            </n-radio>
          </n-space>
        </n-radio-group>
      </n-form-item>
      <n-form-item v-if="frequency === 'MONTHLY'" label="模式" path="monthlyMode">
        <n-select
          :value="monthlyMode"
          :options="monthlyModeOptions"
          @update:value="handleMonthlyModeChange"
        />
      </n-form-item>
      <n-form-item v-if="frequency === 'YEARLY'" label="触发日期" path="yearly">
        <n-space>
          <n-select
            :value="yearMonth"
            :options="monthOptions"
            style="width: 120px"
            @update:value="(v: number) => emitYearly(v, yearDay)"
          />
          <n-select
            :value="yearDay"
            :options="dayOptions"
            style="width: 120px"
            @update:value="(v: number) => emitYearly(yearMonth, v)"
          />
        </n-space>
      </n-form-item>
    </template>

    <!-- 常驻任务：无附加配置 -->
    <template v-else-if="taskType === 'STANDING'">
      <n-text depth="3" style="font-size: 12px">
        当前类型的重复提交控制已移至表单顶部「允许重复提交」设置。
      </n-text>
    </template>
  </n-space>
</template>

<script lang="ts" setup>
  import { computed, ref, watch } from 'vue';
  import { NDatePicker, NFormItem, NRadio, NRadioGroup, NSelect, NSpace, NText } from 'naive-ui';
  import type { ParentTaskType } from '../utils';

  /**
   * TaskTypeConfigForms —— 任务类型选择器 + 按类型动态渲染配置子表单。
   * 从旧版 React 组件 web/apps/console/src/parent/components/TaskTypeConfigForms.tsx 移植。
   * typeConfig 在线上序列化为 JSON 字符串，由页面层负责 stringify。
   */
  const props = defineProps<{
    taskType: ParentTaskType | '';
    typeConfig: Record<string, unknown>;
  }>();

  const emit = defineEmits<{
    (e: 'update:taskType', value: ParentTaskType | ''): void;
    (e: 'update:typeConfig', value: Record<string, unknown>): void;
  }>();

  const typeOptions = [
    { label: '选择任务类型', value: '' },
    { label: '限时任务', value: 'LIMITED' },
    { label: '重复任务', value: 'REPEAT' },
    { label: '常驻任务', value: 'STANDING' },
  ];

  const frequencyOptions = [
    { label: '每天', value: 'DAILY' },
    { label: '每周', value: 'WEEKLY' },
    { label: '每月', value: 'MONTHLY' },
    { label: '每年', value: 'YEARLY' },
  ];

  const monthlyModeOptions = [
    { label: '月初', value: 'FIRST_DAY' },
    { label: '月末', value: 'LAST_DAY' },
    { label: '月中', value: 'MID_MONTH' },
  ];

  const weekdayLabels = [
    { value: 1, label: '周一' },
    { value: 2, label: '周二' },
    { value: 3, label: '周三' },
    { value: 4, label: '周四' },
    { value: 5, label: '周五' },
    { value: 6, label: '周六' },
    { value: 7, label: '周日' },
  ];

  const monthOptions = Array.from({ length: 12 }, (_, i) => ({
    label: `${i + 1} 月`,
    value: i + 1,
  }));
  const dayOptions = Array.from({ length: 31 }, (_, i) => ({
    label: `${i + 1} 日`,
    value: i + 1,
  }));

  // ---- 类型切换：重置为默认配置（旧版 handleTaskTypeChange） ----
  function handleTypeChange(value: ParentTaskType | '') {
    emit('update:taskType', value);
    if (value === 'LIMITED') {
      emit('update:typeConfig', { end_date: '' });
    } else if (value === 'REPEAT') {
      emit('update:typeConfig', { frequency: 'DAILY' });
    } else if (value === 'STANDING') {
      emit('update:typeConfig', { max_submissions: null });
    } else {
      emit('update:typeConfig', {});
    }
  }

  // ---- LIMITED 子表单 ----
  const limitedStart = ref('');
  const limitedEnd = ref('');

  const limitedStartTs = computed(() => (limitedStart.value ? dayStart(limitedStart.value) : null));
  const limitedEndTs = computed(() => (limitedEnd.value ? dayStart(limitedEnd.value) : null));

  function dayStart(date: string): number {
    const ts = new Date(date + 'T00:00:00').getTime();
    return Number.isNaN(ts) ? 0 : ts;
  }

  function emitLimited(start: string | null, end: string | null) {
    limitedStart.value = start ?? '';
    limitedEnd.value = end ?? '';
    emit('update:typeConfig', {
      start_date: limitedStart.value || undefined,
      end_date: limitedEnd.value || undefined,
    });
  }

  // ---- REPEAT 子表单 ----
  type Frequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';
  const frequency = ref<Frequency>('DAILY');
  const weekday = ref<number | null>(null);
  const monthlyMode = ref('FIRST_DAY');
  const yearMonth = ref(1);
  const yearDay = ref(1);

  function buildRepeatConfig(
    freq: Frequency,
    wd: number | null,
    mm: string,
    ym: number,
    yd: number
  ): Record<string, unknown> {
    const cfg: Record<string, unknown> = { frequency: freq };
    if (freq === 'WEEKLY') {
      if (wd != null) cfg.trigger_day = { weekday: wd };
    } else if (freq === 'MONTHLY') {
      cfg.trigger_day = { mode: mm };
    } else if (freq === 'YEARLY') {
      cfg.trigger_day = { month: ym, day: yd };
    }
    return cfg;
  }

  function handleFrequencyChange(v: Frequency) {
    frequency.value = v;
    weekday.value = null;
    monthlyMode.value = 'FIRST_DAY';
    yearMonth.value = 1;
    yearDay.value = 1;
    emit('update:typeConfig', buildRepeatConfig(v, null, 'FIRST_DAY', 1, 1));
  }

  function handleWeekdayChange(day: number) {
    weekday.value = day;
    emit(
      'update:typeConfig',
      buildRepeatConfig(frequency.value, day, monthlyMode.value, yearMonth.value, yearDay.value)
    );
  }

  function handleMonthlyModeChange(v: string) {
    monthlyMode.value = v;
    emit(
      'update:typeConfig',
      buildRepeatConfig(frequency.value, weekday.value, v, yearMonth.value, yearDay.value)
    );
  }

  function emitYearly(ym: number, yd: number) {
    yearMonth.value = ym;
    yearDay.value = yd;
    emit(
      'update:typeConfig',
      buildRepeatConfig(frequency.value, weekday.value, monthlyMode.value, ym, yd)
    );
  }

  // ---- 外部 config 变化时同步本地状态（编辑回填） ----
  watch(
    () => [props.taskType, props.typeConfig] as const,
    () => {
      const cfg = props.typeConfig ?? {};
      // LIMITED
      limitedStart.value = (cfg.start_date as string) ?? '';
      limitedEnd.value = (cfg.end_date as string) ?? '';
      // REPEAT
      if (cfg.frequency) frequency.value = cfg.frequency as Frequency;
      const td = cfg.trigger_day;
      if (td && typeof td === 'object') {
        const obj = td as Record<string, unknown>;
        weekday.value = 'weekday' in obj ? Number(obj.weekday) || null : null;
        monthlyMode.value = 'mode' in obj ? (obj.mode as string) ?? 'FIRST_DAY' : 'FIRST_DAY';
        yearMonth.value = 'month' in obj ? Number(obj.month) || 1 : 1;
        yearDay.value = 'day' in obj ? Number(obj.day) || 1 : 1;
      } else {
        weekday.value = null;
        monthlyMode.value = 'FIRST_DAY';
        yearMonth.value = 1;
        yearDay.value = 1;
      }
      if (!cfg.frequency) frequency.value = 'DAILY';
    },
    { immediate: true, deep: true }
  );
</script>
