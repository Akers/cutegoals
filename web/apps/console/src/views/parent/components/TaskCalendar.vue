<template>
  <div class="task-calendar">
    <!-- 导航栏 -->
    <div class="calendar-nav">
      <n-button size="small" @click="emit('navigate', -1)">&lt;</n-button>
      <span class="calendar-nav-title">{{ year }}年{{ month }}月</span>
      <n-button size="small" @click="emit('navigate', 1)">&gt;</n-button>
    </div>

    <!-- 加载/错误态 -->
    <div v-if="loading" class="calendar-loading">
      <n-spin size="medium" />
    </div>
    <n-alert v-else-if="loadError" type="error" title="加载失败">
      <n-button text type="primary" @click="load">重试</n-button>
    </n-alert>

    <!-- 月历主体：周日在前，6 行 × 7 列 -->
    <div v-else class="calendar-grid">
      <div class="calendar-weekday" v-for="w in WEEKDAYS" :key="w">{{ w }}</div>
      <div
        v-for="cell in cells"
        :key="cell.key"
        class="calendar-cell"
        :class="{
          'is-other-month': cell.otherMonth,
          'is-today': cell.date === todayStr,
          'is-selected': cell.date === selectedDate,
        }"
        @click="onCellClick(cell)"
      >
        <span class="cell-day">{{ cell.day }}</span>
        <span v-if="cell.total > 0" class="cell-badge" :data-testid="`task-badge-${cell.date}`">
          {{ cell.total }}
        </span>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
  import { computed, ref, watch } from 'vue';
  import { NAlert, NButton, NSpin } from 'naive-ui';
  import { getTaskCalendar } from '@/api/task';
  import type { TaskCalendarData } from '@/api/task';

  /**
   * 任务日历（从旧版 web/apps/console/src/parent/components/TaskCalendar.tsx 移植）。
   * 单月面板：GET /task-assignments/calendar?year=&month= 返回按日聚合的任务数，
   * 有任务的日期右上角渲染红色角标；点击日期触发 select。
   */
  const props = defineProps<{
    /** 'YYYY-MM' */
    baseMonth: string;
    /** 当前选中日期 'YYYY-MM-DD'（null 表示查看全部） */
    selectedDate?: string | null;
  }>();

  const emit = defineEmits<{
    (e: 'select', date: string): void;
    (e: 'navigate', direction: -1 | 1): void;
  }>();

  const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六'];

  const loading = ref(false);
  const loadError = ref(false);
  const calendarData = ref<TaskCalendarData | null>(null);

  const year = computed(() => Number(props.baseMonth.split('-')[0]));
  const month = computed(() => Number(props.baseMonth.split('-')[1]));
  const todayStr = computed(() => fmtDate(new Date()));

  function fmtDate(d: Date): string {
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${d.getFullYear()}-${m}-${day}`;
  }

  async function load() {
    loading.value = true;
    loadError.value = false;
    try {
      calendarData.value = await getTaskCalendar(year.value, month.value);
    } catch {
      // 错误已由 http 层全局提示
      loadError.value = true;
    } finally {
      loading.value = false;
    }
  }

  watch(() => props.baseMonth, load, { immediate: true });

  interface Cell {
    key: string;
    date: string;
    day: number;
    otherMonth: boolean;
    total: number;
  }

  const cells = computed<Cell[]>(() => {
    const y = year.value;
    const m = month.value;
    const first = new Date(y, m - 1, 1);
    const start = new Date(y, m - 1, 1 - first.getDay()); // 周日在前
    const result: Cell[] = [];
    const cursor = new Date(start);
    for (let i = 0; i < 42; i++) {
      const date = fmtDate(cursor);
      const otherMonth = cursor.getMonth() !== m - 1;
      result.push({
        key: date + '-' + i,
        date,
        day: cursor.getDate(),
        otherMonth,
        total: calendarData.value?.days?.[date]?.total ?? 0,
      });
      cursor.setDate(cursor.getDate() + 1);
    }
    return result;
  });

  function onCellClick(cell: Cell) {
    if (cell.otherMonth) return;
    emit('select', cell.date);
  }
</script>

<style lang="less" scoped>
  .task-calendar {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .calendar-nav {
    display: flex;
    justify-content: center;
    align-items: center;
    gap: 16px;

    .calendar-nav-title {
      font-size: 16px;
      font-weight: 600;
    }
  }

  .calendar-loading {
    text-align: center;
    padding: 40px 0;
  }

  .calendar-grid {
    display: grid;
    grid-template-columns: repeat(7, 1fr);
    border-top: 1px solid #efeff5;
    border-left: 1px solid #efeff5;
    user-select: none;
  }

  .calendar-weekday {
    text-align: center;
    padding: 6px 0;
    font-size: 13px;
    color: #606266;
    border-right: 1px solid #efeff5;
    border-bottom: 1px solid #efeff5;
    background: #fafafc;
  }

  .calendar-cell {
    position: relative;
    min-height: 56px;
    padding: 6px;
    text-align: left;
    border-right: 1px solid #efeff5;
    border-bottom: 1px solid #efeff5;
    cursor: pointer;
    transition: background-color 0.2s;

    &:hover:not(.is-other-month) {
      background-color: #f3f3f5;
    }

    &.is-other-month {
      color: #c2c2c7;
      cursor: default;

      .cell-day {
        color: #c2c2c7;
      }
    }

    &.is-today:not(.is-selected) {
      .cell-day {
        color: #0d9488;
        border: 1px solid #1677ff;
        border-radius: 4px;
      }
    }

    &.is-selected {
      background-color: #0d9488;

      .cell-day {
        color: #ffffff;
      }
    }

    .cell-day {
      display: inline-block;
      min-width: 24px;
      padding: 1px 4px;
      text-align: center;
      font-size: 16px;
    }

    .cell-badge {
      position: absolute;
      top: 4px;
      right: 4px;
      background-color: #ff4d4f;
      color: #fff;
      border-radius: 5px;
      min-width: 14px;
      height: 14px;
      padding: 0 3px;
      font-size: 10px;
      line-height: 14px;
      text-align: center;
      font-weight: 700;
      pointer-events: none;
    }
  }
</style>
