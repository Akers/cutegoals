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
        <h3 class="page-title">奖品</h3>
        <n-button @click="openNew">新增奖品</n-button>
      </div>

      <n-data-table
        remote
        :columns="columns"
        :data="items"
        :loading="loading"
        :pagination="pagination"
        :row-key="(row: Prize) => row.id"
      />
    </n-space>

    <!-- 新增/编辑奖品 -->
    <n-modal
      v-model:show="showModal"
      preset="card"
      :title="editing ? '编辑奖品' : '新增奖品'"
      style="width: 640px"
    >
      <n-space vertical :size="16">
        <n-form :model="form" :rules="rules" label-placement="top">
          <n-form-item label="名称" path="name">
            <n-input v-model:value="form.name" />
          </n-form-item>
          <n-form-item label="描述" path="description">
            <n-input v-model:value="form.description" type="textarea" :rows="3" />
          </n-form-item>
          <n-form-item label="积分价格" path="pointsCost">
            <n-input-number v-model:value="form.pointsCost" :precision="0" style="width: 100%" />
          </n-form-item>
          <n-form-item label="库存" path="availableStock">
            <n-input-number
              v-model:value="form.availableStock"
              :precision="0"
              style="width: 100%"
            />
          </n-form-item>
        </n-form>

        <!-- 奖品类型配置表单 -->
        <PrizeTypeConfigForms
          :value="prizeForm"
          :on-upload="handleUpload"
          @update:value="prizeForm = $event"
        />
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
  import type { DataTableColumns, FormRules } from 'naive-ui';
  import {
    NButton,
    NForm,
    NFormItem,
    NInput,
    NInputNumber,
    NModal,
    NResult,
    NSpace,
    NSpin,
    NTag,
    createDiscreteApi,
    useMessage,
  } from 'naive-ui';
  import type { Prize } from '@/types/api';
  import { createPrize, deletePrize, listPrizes, updatePrize, uploadPrizeImage } from '@/api/prize';
  import PrizeTypeConfigForms from '../components/PrizeTypeConfigForms.vue';
  import type { PrizeTypeConfigFormValue } from '../components/PrizeTypeConfigForms.vue';
  import { PRIZE_CATEGORY_LABELS } from '../utils';

  defineOptions({ name: 'ParentPrizes' });

  const message = useMessage();
  // 表格行内删除确认（旧版 Modal.confirm 语义）
  const { dialog } = createDiscreteApi(['dialog']);

  const loading = ref(false);
  const error = ref(false);
  const items = ref<Prize[]>([]);
  const page = ref(1);
  const pageSize = ref(10);
  const total = ref(0);

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
      const res = await listPrizes({ page: page.value, pageSize: pageSize.value });
      items.value = res.content ?? [];
      total.value = res.totalElements ?? 0;
    } catch {
      error.value = true;
    } finally {
      loading.value = false;
    }
  }

  const columns: DataTableColumns<Prize> = [
    { title: '奖品名称', key: 'name' },
    { title: '积分', key: 'pointsCost' },
    { title: '库存', key: 'availableStock' },
    {
      title: '类型',
      key: 'prizeType',
      render(row) {
        if (row.prizeType === 'VIRTUAL') {
          return h(
            NTag,
            { type: 'info', size: 'small' },
            {
              default: () =>
                `虚拟 · ${PRIZE_CATEGORY_LABELS[row.prizeCategory ?? ''] ?? row.prizeCategory}`,
            }
          );
        }
        return h(NTag, { size: 'small' }, { default: () => '实物' });
      },
    },
    {
      title: '状态',
      key: 'enabled',
      render(row) {
        return h(
          NTag,
          { type: row.enabled ? 'success' : 'default', size: 'small' },
          { default: () => (row.enabled ? '启用' : '停用') }
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
              {
                size: 'small',
                type: 'error',
                ghost: true,
                onClick: () =>
                  dialog.warning({
                    title: '确认删除',
                    content: '删除奖品将影响相关兑换记录，确认删除？',
                    positiveText: '确认删除',
                    negativeText: '取消',
                    onPositiveClick: async () => {
                      await deletePrize(row.id);
                      await load();
                    },
                  }),
              },
              { default: () => '删除' }
            ),
          ],
        });
      },
    },
  ];

  async function toggleEnabled(p: Prize) {
    try {
      await updatePrize(p.id, {
        name: p.name,
        description: p.description,
        pointsCost: p.pointsCost,
        availableStock: p.availableStock,
        enabled: !p.enabled,
      });
      await load();
    } catch {
      // 失败提示由 http 层全局处理
    }
  }

  // ---- 新增/编辑 ----
  const showModal = ref(false);
  const saving = ref(false);
  const editing = ref<Prize | null>(null);
  const form = ref({ name: '', description: '', pointsCost: 0, availableStock: 0 });
  const prizeForm = ref<PrizeTypeConfigFormValue>({ prizeType: 'PHYSICAL' });

  const rules: FormRules = {
    name: [{ required: true, message: '请输入名称' }],
  };

  async function handleUpload(file: File): Promise<string> {
    return uploadPrizeImage(file);
  }

  function openNew() {
    editing.value = null;
    form.value = { name: '', description: '', pointsCost: 0, availableStock: 0 };
    prizeForm.value = { prizeType: 'PHYSICAL' };
    showModal.value = true;
  }

  function openEdit(p: Prize) {
    editing.value = p;
    form.value = {
      name: p.name,
      description: p.description ?? '',
      pointsCost: p.pointsCost,
      availableStock: p.availableStock,
    };
    prizeForm.value = {
      prizeType: p.prizeType ?? 'PHYSICAL',
      prizeCategory: p.prizeCategory,
      titleImage: p.titleImage,
      detailImage: p.detailImage,
      validFrom: p.validFrom,
      validTo: p.validTo,
      typeConfig: p.typeConfig ? JSON.stringify(p.typeConfig) : undefined,
    };
    showModal.value = true;
  }

  async function handleSave() {
    saving.value = true;
    try {
      const v = prizeForm.value;
      const payload = {
        name: form.value.name,
        description: form.value.description,
        pointsCost: form.value.pointsCost,
        availableStock: form.value.availableStock,
        prizeType: v.prizeType,
        prizeCategory: v.prizeCategory,
        titleImage: v.titleImage,
        detailImage: v.detailImage,
        validFrom: v.validFrom,
        validTo: v.validTo,
        typeConfig: v.typeConfig,
      };
      if (editing.value) {
        await updatePrize(editing.value.id, payload);
      } else {
        await createPrize(payload);
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
