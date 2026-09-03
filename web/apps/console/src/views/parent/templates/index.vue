<template>
  <div>
    <n-spin v-if="loading && items.length === 0" class="page-spin" />
    <n-result v-else-if="error" status="error" title="加载失败" description="请求失败，请稍后重试">
      <template #footer>
        <n-button @click="load">重试</n-button>
      </template>
    </n-result>
    <n-space v-else vertical :size="24">
      <div class="page-header">
        <h3 class="page-title">任务模板</h3>
        <n-button @click="openNew">新建模板</n-button>
      </div>

      <!-- 任务类型筛选器 -->
      <TaskTypeFilter
        :selected="selectedTypes"
        @update:selected="(types) => ((selectedTypes = types), (page = 1), load())"
      />

      <n-data-table
        remote
        :columns="columns"
        :data="items"
        :loading="loading"
        :pagination="pagination"
        :row-key="(row: TaskTemplate) => row.id"
      />
    </n-space>

    <!-- 新建/编辑模板 -->
    <n-modal
      v-model:show="showModal"
      preset="card"
      :title="editing ? '编辑模板' : '新建模板'"
      style="width: 560px"
    >
      <n-space vertical :size="16">
        <n-form ref="formRef" :model="form" :rules="rules" label-placement="top">
          <n-form-item label="标题" path="name">
            <n-input v-model:value="form.name" />
          </n-form-item>
          <n-form-item label="描述" path="description">
            <n-input v-model:value="form.description" type="textarea" :rows="3" />
          </n-form-item>
          <n-form-item label="分类" path="category">
            <n-input v-model:value="form.category" />
          </n-form-item>
          <n-form-item label="基础积分" path="basePoints">
            <n-input-number
              v-model:value="form.basePoints"
              :min="1"
              :precision="0"
              style="width: 100%"
            />
          </n-form-item>
        </n-form>

        <!-- 任务类型选择器和配置表单 -->
        <TaskTypeConfigForms
          :task-type="taskType"
          :type-config="typeConfig"
          @update:task-type="(v) => (taskType = v)"
          @update:type-config="(v) => (typeConfig = v)"
        />

        <n-checkbox v-model:checked="allowResubmit">允许重复提交</n-checkbox>

        <n-form v-if="allowResubmit" :show-feedback="false" label-placement="top">
          <n-form-item label="最大提交次数">
            <n-input-number
              v-model:value="maxSubmissions"
              :min="0"
              :max="10000"
              :precision="0"
              placeholder="0 = 不限制"
              style="width: 100%"
            />
          </n-form-item>
          <n-form-item label="积分上限">
            <n-input-number
              v-model:value="pointsCap"
              :min="0"
              :max="100000000"
              :precision="0"
              placeholder="0 = 不限制"
              style="width: 100%"
            />
          </n-form-item>
        </n-form>
      </n-space>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleSave">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script lang="ts" setup>
  import { computed, h, onMounted, ref } from 'vue';
  import type { DataTableColumns, FormInst, FormRules } from 'naive-ui';
  import {
    NButton,
    NCheckbox,
    NForm,
    NFormItem,
    NInput,
    NInputNumber,
    NModal,
    NPopconfirm,
    NResult,
    NSpace,
    NSpin,
    NTag,
    createDiscreteApi,
    useMessage,
  } from 'naive-ui';
  import type { TaskTemplate } from '@/types/api';
  import {
    createTemplate,
    deleteTemplate,
    listTemplates,
    setTemplateEnabled,
    updateTemplate,
  } from '@/api/task';
  import TaskTypeFilter from '../components/TaskTypeFilter.vue';
  import TaskTypeConfigForms from '../components/TaskTypeConfigForms.vue';
  import { readTypeConfig, type ParentTaskType } from '../utils';

  defineOptions({ name: 'ParentTemplates' });

  const message = useMessage();
  // 表格行内的删除确认（旧版 Modal.confirm 语义）
  const { dialog } = createDiscreteApi(['dialog']);

  const loading = ref(false);
  const error = ref(false);
  const items = ref<TaskTemplate[]>([]);
  const page = ref(1);
  const pageSize = ref(10);
  const total = ref(0);
  const selectedTypes = ref<ParentTaskType[]>([]);

  const pagination = computed(() => ({
    page: page.value,
    pageSize: pageSize.value,
    itemCount: total.value,
    onChange: (p: number) => {
      page.value = p;
      load();
    },
  }));

  async function load() {
    loading.value = true;
    error.value = false;
    try {
      const params: Record<string, unknown> = {
        page: page.value,
        pageSize: pageSize.value,
      };
      if (selectedTypes.value.length > 0) {
        params.taskType = selectedTypes.value.join(',');
      }
      const res = await listTemplates(params);
      items.value = res.content ?? [];
      total.value = res.totalElements ?? 0;
    } catch {
      error.value = true;
    } finally {
      loading.value = false;
    }
  }

  const columns: DataTableColumns<TaskTemplate> = [
    { title: '模板名称', key: 'name' },
    { title: '分类', key: 'category' },
    {
      title: '状态',
      key: 'enabled',
      render(row) {
        return h(
          NTag,
          { type: row.enabled ? 'success' : 'default', size: 'small' },
          { default: () => (row.enabled ? '已启用' : '已停用') }
        );
      },
    },
    {
      title: '操作',
      key: 'actions',
      render(row) {
        return h(NSpace, null, {
          default: () => [
            h(
              NButton,
              { text: true, size: 'small', onClick: () => openEdit(row) },
              { default: () => '编辑' }
            ),
            h(
              NButton,
              { size: 'small', onClick: () => toggleEnabled(row) },
              { default: () => (row.enabled ? '停用' : '启用') }
            ),
            h(
              NButton,
              { size: 'small', type: 'error', ghost: true, onClick: () => confirmDelete(row) },
              { default: () => '删除' }
            ),
          ],
        });
      },
    },
  ];

  async function toggleEnabled(t: TaskTemplate) {
    try {
      await setTemplateEnabled(t.id, !t.enabled);
      await load();
    } catch {
      // 失败提示由 http 层全局处理
    }
  }

  function confirmDelete(t: TaskTemplate) {
    dialog.warning({
      title: '确认删除',
      content: '删除模板将影响相关任务分配，确认删除？',
      positiveText: '确认删除',
      negativeText: '取消',
      onPositiveClick: async () => {
        await deleteTemplate(t.id);
        await load();
      },
    });
  }

  // ---- 新建/编辑 ----
  const showModal = ref(false);
  const saving = ref(false);
  const editing = ref<TaskTemplate | null>(null);
  const formRef = ref<FormInst | null>(null);
  const form = ref({ name: '', description: '', category: '', basePoints: 10 });
  const taskType = ref<ParentTaskType | ''>('');
  const typeConfig = ref<Record<string, unknown>>({});
  const allowResubmit = ref(false);
  const maxSubmissions = ref(0);
  const pointsCap = ref(0);

  const rules: FormRules = {
    name: [{ required: true, message: '请输入标题' }],
  };

  function openNew() {
    editing.value = null;
    form.value = { name: '', description: '', category: '', basePoints: 10 };
    taskType.value = '';
    typeConfig.value = {};
    allowResubmit.value = false;
    maxSubmissions.value = 0;
    pointsCap.value = 0;
    showModal.value = true;
  }

  function openEdit(t: TaskTemplate) {
    editing.value = t;
    form.value = {
      name: t.name,
      description: t.description ?? '',
      category: t.category ?? '',
      basePoints: t.difficulties?.[0]?.rewardPoints ?? 10,
    };
    taskType.value = (t.taskType as ParentTaskType) ?? '';
    typeConfig.value = readTypeConfig(t.typeConfig);
    allowResubmit.value = t.allowResubmit ?? false;
    maxSubmissions.value = t.maxSubmissions ?? 0;
    pointsCap.value = t.pointsCap ?? 0;
    showModal.value = true;
  }

  async function handleSave() {
    await formRef.value?.validate();
    saving.value = true;
    try {
      const payload: Record<string, unknown> = {
        name: form.value.name,
        description: form.value.description,
        category: form.value.category,
        difficulties: [
          {
            name: '标准',
            displayOrder: 1,
            rewardPoints: form.value.basePoints || 1,
            enabled: true,
          },
        ],
        allow_resubmit: allowResubmit.value,
        max_submissions: maxSubmissions.value,
        points_cap: pointsCap.value,
      };
      // 添加任务类型和配置
      if (taskType.value) {
        payload.taskType = taskType.value;
        payload.typeConfig = JSON.stringify(typeConfig.value);
      }
      if (editing.value) {
        payload.version = editing.value.version;
        await updateTemplate(editing.value.id, payload);
      } else {
        await createTemplate(payload);
      }
      showModal.value = false;
      message.success('保存成功');
      await load();
    } catch {
      // 失败提示由 http 层全局处理
    } finally {
      saving.value = false;
    }
  }

  onMounted(load);
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
</style>
