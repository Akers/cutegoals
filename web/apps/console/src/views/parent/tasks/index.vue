<template>
  <div>
    <n-spin v-if="loading && assignments.length === 0" class="page-spin" />
    <n-result
      v-else-if="loadError"
      status="error"
      title="加载失败"
      description="请求失败，请稍后重试"
    >
      <template #footer>
        <n-button @click="refetch">重试</n-button>
      </template>
    </n-result>
    <n-space v-else vertical :size="24">
      <div class="page-header">
        <h3 class="page-title">任务分配</h3>
        <n-space>
          <n-button
            @click="
              () => {
                resetSingleAssignForm();
                showSingleAssign = true;
              }
            "
          >
            分配任务
          </n-button>
          <n-button
            @click="
              () => {
                resetAssignForm();
                showAssign = true;
              }
            "
          >
            批量分配
          </n-button>
        </n-space>
      </div>

      <!-- 日历 -->
      <n-card title="日历">
        <TaskCalendar
          :base-month="baseMonth"
          :selected-date="selectedDate"
          @select="(date) => (selectedDate = date)"
          @navigate="navigateMonth"
        />
      </n-card>

      <!-- 类型筛选 -->
      <n-card>
        <n-space vertical size="small">
          <TaskTypeFilter :selected="taskTypeFilters" @update:selected="taskTypeFilters = $event" />
          <n-button :type="selectedDate === null ? 'primary' : 'default'" @click="toggleViewAll">
            {{ selectedDate === null ? '查看全部（已激活）' : '查看全部' }}
          </n-button>
        </n-space>
      </n-card>

      <!-- 任务列表 -->
      <n-card title="任务列表">
        <n-space vertical size="small">
          <n-card v-for="a in assignments" :key="a.id" size="small" :class="{ overdue: a.overdue }">
            <div class="task-item">
              <n-space vertical size="tiny">
                <span class="task-name">{{ a.snapshotTemplateName }}</span>
                <span class="task-sub">
                  {{ childLine(a) }}
                </span>
                <span v-if="a.snapshotTemplateTaskType === 'REPEAT'" class="task-sub">
                  {{ formatRepeatProgress(a) }}
                </span>
                <span v-if="a.overdue" class="task-overdue">已逾期</span>
              </n-space>
              <n-space vertical size="tiny" align="end">
                <n-tag size="small">{{ statusLabel(a.status.toLowerCase()) }}</n-tag>
                <span class="task-sub">{{ a.snapshotDifficultyReward }} 积分</span>
              </n-space>
            </div>
          </n-card>
          <span v-if="assignments.length === 0" class="task-empty">当天暂无任务</span>
        </n-space>
      </n-card>
    </n-space>

    <!-- 批量分配任务 -->
    <n-modal v-model:show="showAssign" preset="card" title="批量分配任务" style="width: 520px">
      <n-space vertical :size="16">
        <n-form label-placement="top" :show-feedback="false">
          <n-form-item label="模板">
            <n-select
              v-model:value="assignTemplateId"
              :options="templateOptions"
              placeholder="请选择模板"
              clearable
              @update:value="onAssignTemplateChange"
            />
          </n-form-item>
          <n-form-item label="难度">
            <n-select
              v-model:value="assignDifficultyId"
              :options="assignDifficultyOptions"
              :disabled="!selectedTemplate"
              placeholder="请选择难度"
              clearable
            />
          </n-form-item>
          <n-form-item label="孩子">
            <n-select
              v-model:value="assignChildIds"
              :options="childOptions"
              multiple
              placeholder="请选择孩子"
            />
            <div class="field-hint">可选择多个孩子</div>
          </n-form-item>
          <n-form-item label="开始日期">
            <n-date-picker
              v-model:formatted-value="assignStartDate"
              type="date"
              value-format="yyyy-MM-dd"
              style="width: 100%"
            />
          </n-form-item>
          <n-form-item v-if="selectedTemplate?.taskType !== 'REPEAT'" label="结束日期">
            <n-date-picker
              v-model:formatted-value="assignEndDate"
              type="date"
              value-format="yyyy-MM-dd"
              style="width: 100%"
            />
          </n-form-item>
        </n-form>
      </n-space>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showAssign = false">取消</n-button>
          <n-button type="primary" :loading="assigning" @click="handleAssign">分配</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 单任务分配 -->
    <n-modal
      v-model:show="showSingleAssign"
      preset="card"
      title="分配任务"
      style="width: 520px"
      @after-leave="resetSingleAssignForm"
    >
      <n-space vertical :size="16">
        <n-form label-placement="top" :show-feedback="false">
          <n-form-item label="模板">
            <n-select
              v-model:value="singleTemplateId"
              :options="templateOptions"
              placeholder="请选择模板"
              clearable
              @update:value="onSingleTemplateChange"
            />
          </n-form-item>
          <n-form-item label="难度">
            <n-select
              v-model:value="singleDifficultyId"
              :options="singleDifficultyOptions"
              :disabled="!selectedSingleTemplate"
              placeholder="请选择难度"
              clearable
            />
          </n-form-item>
          <n-form-item label="孩子">
            <n-select
              v-model:value="singleChildId"
              :options="childOptions"
              placeholder="请选择孩子"
              clearable
            />
          </n-form-item>
          <n-form-item v-if="selectedSingleTemplate?.taskType !== 'REPEAT'" label="截止日期">
            <n-date-picker
              v-model:formatted-value="singleDeadline"
              type="date"
              value-format="yyyy-MM-dd"
              style="width: 100%"
            />
          </n-form-item>
        </n-form>
      </n-space>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showSingleAssign = false">取消</n-button>
          <n-button type="primary" :loading="singleAssigning" @click="handleSingleAssign">
            分配
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script lang="ts" setup>
  import { computed, onMounted, ref, watch } from 'vue';
  import dayjs from 'dayjs';
  import {
    NButton,
    NCard,
    NDatePicker,
    NForm,
    NFormItem,
    NModal,
    NResult,
    NSelect,
    NSpace,
    NSpin,
    NTag,
    createDiscreteApi,
  } from 'naive-ui';
  import type {
    AssignmentListParams,
    ChildProfile,
    TaskAssignment,
    TaskTemplate,
  } from '@/types/api';
  import {
    createAssignment,
    createAssignmentsBatch,
    listAssignments,
    listTemplates,
  } from '@/api/task';
  import { listChildren } from '@/api/children';
  import TaskCalendar from '../components/TaskCalendar.vue';
  import TaskTypeFilter from '../components/TaskTypeFilter.vue';
  import {
    formatRepeatProgress,
    repeatTaskLabel,
    statusLabel,
    type ParentTaskType,
  } from '../utils';

  defineOptions({ name: 'ParentTasks' });

  // 表单校验错误弹窗（旧版 Modal.error 语义）
  const { dialog } = createDiscreteApi(['dialog']);

  // ── 日历状态 ──
  const todayStr = dayjs().format('YYYY-MM-DD');
  const baseMonth = ref(dayjs().format('YYYY-MM'));
  const selectedDate = ref<string | null>(todayStr);
  const taskTypeFilters = ref<ParentTaskType[]>(['LIMITED', 'REPEAT', 'STANDING']);

  function navigateMonth(direction: -1 | 1) {
    baseMonth.value = dayjs(baseMonth.value + '-01')
      .add(direction, 'month')
      .format('YYYY-MM');
  }

  function toggleViewAll() {
    selectedDate.value = selectedDate.value === null ? todayStr : null;
  }

  // ── 数据加载：A（选中日）+ 全量 REPEAT 合并去重，按类型后置过滤 ──
  const loadingA = ref(false);
  const loadingRepeat = ref(false);
  const loadError = ref(false);
  const listA = ref<TaskAssignment[]>([]);
  const listRepeat = ref<TaskAssignment[]>([]);
  const templates = ref<TaskTemplate[]>([]);
  const children = ref<ChildProfile[]>([]);

  const loading = computed(() => loadingA.value || loadingRepeat.value);

  const childNameMap = computed(() => {
    const map = new Map<string, string>();
    for (const c of children.value) {
      map.set(String(c.id), c.nickname);
    }
    return map;
  });

  function parseFrequency(typeConfig?: string): string | null {
    if (!typeConfig) return null;
    try {
      const parsed = JSON.parse(typeConfig) as { frequency?: string };
      return parsed.frequency ?? null;
    } catch {
      return null;
    }
  }

  function isWeeklyOrDailyRepeat(task: TaskAssignment): boolean {
    if (task.snapshotTemplateTaskType !== 'REPEAT') return false;
    const freq = parseFrequency(task.snapshotTemplateTypeConfig);
    return freq === 'WEEKLY' || freq === 'DAILY';
  }

  const assignments = computed(() => {
    // Map 去重，A 优先；REPEAT（每周/每天）不依赖日期过滤
    const merged = new Map<string, TaskAssignment>();
    for (const t of listA.value) merged.set(String(t.id), t);
    for (const t of listRepeat.value) {
      if (isWeeklyOrDailyRepeat(t) && !merged.has(String(t.id))) merged.set(String(t.id), t);
    }
    // 按任务类型后置过滤（未知类型不隐藏）
    const filters = taskTypeFilters.value;
    return Array.from(merged.values()).filter((t) => {
      const tp = t.snapshotTemplateTaskType;
      if (!tp) return true;
      return filters.includes(tp as ParentTaskType);
    });
  });

  async function fetchList(dateFilter: boolean): Promise<TaskAssignment[]> {
    const params: AssignmentListParams = { page: 1, pageSize: 100 };
    if (dateFilter && selectedDate.value) {
      params.startDate = selectedDate.value;
      params.endDate = selectedDate.value;
    }
    const res = await listAssignments(params);
    return res.content ?? [];
  }

  async function refetch() {
    loadingA.value = true;
    loadingRepeat.value = true;
    loadError.value = false;
    try {
      const [a, repeat] = await Promise.all([
        fetchList(true).finally(() => (loadingA.value = false)),
        listAssignments({ page: 1, pageSize: 100, taskType: 'REPEAT' })
          .then((r) => r.content ?? [])
          .finally(() => (loadingRepeat.value = false)),
      ]);
      listA.value = a;
      listRepeat.value = repeat;
    } catch {
      loadError.value = true;
    }
  }

  // 查询条件变化 300ms 防抖（对齐旧版）
  let debounceTimer: ReturnType<typeof setTimeout> | null = null;
  watch([selectedDate, taskTypeFilters], () => {
    if (debounceTimer) clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => {
      void refetch();
    }, 300);
  });

  async function loadMeta() {
    try {
      const [tplRes, childRes] = await Promise.all([
        listTemplates({ enabled: true } as never),
        listChildren(),
      ]);
      templates.value = tplRes.content ?? [];
      children.value = childRes.content ?? [];
    } catch {
      // 元数据加载失败时下拉为空，不阻塞页面
    }
  }

  function childLine(a: TaskAssignment): string {
    const childName = childNameMap.value.get(String(a.childId)) ?? `ID ${a.childId}`;
    const label = repeatTaskLabel(a.snapshotTemplateTaskType, a.snapshotTemplateTypeConfig);
    return label ? `孩子：${childName} · ${label}` : `孩子：${childName} · 截止 ${a.deadline}`;
  }

  // ── 批量分配 ──
  const showAssign = ref(false);
  const assigning = ref(false);
  const assignTemplateId = ref<string | null>(null);
  const assignDifficultyId = ref<string | null>(null);
  const assignChildIds = ref<string[]>([]);
  const assignStartDate = ref<string | null>(null);
  const assignEndDate = ref<string | null>(null);
  const idempotencyKey = ref('');

  const templateOptions = computed(() =>
    templates.value.map((t) => ({ label: t.name, value: String(t.id) }))
  );

  const selectedTemplate = computed(() =>
    templates.value.find((t) => String(t.id) === assignTemplateId.value)
  );

  const assignDifficultyOptions = computed(() =>
    (selectedTemplate.value?.difficulties ?? [])
      .filter((d) => d.enabled)
      .map((d) => ({ label: `${d.name}（${d.rewardPoints} 积分）`, value: String(d.id) }))
  );

  const childOptions = computed(() =>
    children.value.map((c) => ({ label: c.nickname, value: String(c.id) }))
  );

  function onAssignTemplateChange(value: string | null) {
    assignDifficultyId.value = null;
    // 根据所选模板的任务类型自动填入日期
    const tpl = templates.value.find((t) => String(t.id) === value);
    const today = dayjs().format('YYYY-MM-DD');
    if (tpl?.taskType === 'LIMITED') {
      try {
        const config = JSON.parse(tpl.typeConfig ?? '{}') as {
          start_date?: string;
          end_date?: string;
        };
        assignStartDate.value = config.start_date || today;
        assignEndDate.value = config.end_date || today;
      } catch {
        assignStartDate.value = today;
        assignEndDate.value = today;
      }
    } else {
      // REPEAT / STANDING：默认只填开始日期，结束日期与开始日期保持一致
      assignStartDate.value = today;
      assignEndDate.value = today;
    }
  }

  function resetAssignForm() {
    assignTemplateId.value = null;
    assignDifficultyId.value = null;
    assignStartDate.value = null;
    assignEndDate.value = null;
    assignChildIds.value = [];
    idempotencyKey.value = crypto.randomUUID();
  }

  async function handleAssign() {
    const tId = Number(assignTemplateId.value);
    const dId = Number(assignDifficultyId.value);
    if (!tId || !dId) {
      dialog.error({ title: '错误', content: '请选择模板和难度' });
      return;
    }
    if (assignChildIds.value.length === 0) {
      dialog.error({ title: '错误', content: '请至少选择一个孩子' });
      return;
    }
    const isRepeat = selectedTemplate.value?.taskType === 'REPEAT';
    const payloadStartDate = assignStartDate.value;
    const payloadEndDate = isRepeat ? payloadStartDate : assignEndDate.value;
    if (!payloadStartDate || !payloadEndDate) {
      dialog.error({ title: '错误', content: '请填写开始日期和结束日期' });
      return;
    }
    if (payloadStartDate > payloadEndDate) {
      dialog.error({ title: '错误', content: '开始日期不得晚于结束日期' });
      return;
    }
    assigning.value = true;
    try {
      await createAssignmentsBatch({
        templateId: tId,
        difficultyId: dId,
        startDate: payloadStartDate,
        endDate: payloadEndDate,
        childIds: assignChildIds.value.map(Number),
        idempotencyKey: idempotencyKey.value,
      });
      showAssign.value = false;
      resetAssignForm();
      await refetch();
    } catch {
      // 失败提示由 http 层全局处理
    } finally {
      assigning.value = false;
    }
  }

  // ── 单任务分配 ──
  const showSingleAssign = ref(false);
  const singleAssigning = ref(false);
  const singleTemplateId = ref<string | null>(null);
  const singleDifficultyId = ref<string | null>(null);
  const singleChildId = ref<string | null>(null);
  const singleDeadline = ref<string | null>(null);

  const selectedSingleTemplate = computed(() =>
    templates.value.find((t) => String(t.id) === singleTemplateId.value)
  );

  const singleDifficultyOptions = computed(() =>
    (selectedSingleTemplate.value?.difficulties ?? [])
      .filter((d) => d.enabled)
      .map((d) => ({ label: `${d.name}（${d.rewardPoints} 积分）`, value: String(d.id) }))
  );

  function onSingleTemplateChange() {
    singleDifficultyId.value = null;
  }

  function resetSingleAssignForm() {
    singleTemplateId.value = null;
    singleDifficultyId.value = null;
    singleChildId.value = null;
    singleDeadline.value = null;
  }

  async function handleSingleAssign() {
    const tId = Number(singleTemplateId.value);
    const dId = Number(singleDifficultyId.value);
    const cId = Number(singleChildId.value);
    if (!tId || !dId || !cId) {
      window.$message?.error('请选择模板、难度和孩子');
      return;
    }

    const isRepeat = selectedSingleTemplate.value?.taskType === 'REPEAT';

    singleAssigning.value = true;
    try {
      if (isRepeat) {
        // 重复任务：不需要任何日期，直接创建分配
        await createAssignment({ templateId: tId, childId: cId, difficultyId: dId });
        showSingleAssign.value = false;
        resetSingleAssignForm();
        window.$message?.success('重复任务已分配');
        await refetch();
      } else {
        // 非重复任务：需要截止日期
        if (!singleDeadline.value) {
          window.$message?.error('请选择截止日期');
          return;
        }
        await createAssignment({
          templateId: tId,
          childId: cId,
          difficultyId: dId,
          deadline: `${singleDeadline.value}T23:59:59`,
        });
        showSingleAssign.value = false;
        resetSingleAssignForm();
        window.$message?.success('任务已分配');
        await refetch();
      }
    } catch {
      // 失败提示由 http 层全局处理
    } finally {
      singleAssigning.value = false;
    }
  }

  onMounted(() => {
    idempotencyKey.value = crypto.randomUUID();
    void refetch();
    void loadMeta();
  });
</script>

<style lang="less" scoped>
  .page-spin {
    display: block;
    margin: 80px auto;
  }

  .page-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .page-title {
    margin: 0;
    font-size: 22px;
  }

  .task-item {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
  }

  .task-name {
    font-weight: 600;
  }

  .task-sub {
    font-size: 12px;
    color: rgba(0, 0, 0, 0.45);
  }

  .task-overdue {
    font-size: 12px;
    font-weight: 600;
    color: #faad14;
  }

  .task-empty {
    color: rgba(0, 0, 0, 0.45);
  }

  .overdue {
    border-left: 4px solid #faad14;
  }

  .field-hint {
    font-size: 12px;
    color: rgba(0, 0, 0, 0.45);
  }
</style>
