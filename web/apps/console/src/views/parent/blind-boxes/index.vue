<template>
  <div>
    <n-spin v-if="loading && items.length === 0" class="page-spin" />
    <n-result v-else-if="error" status="error" title="加载失败" description="请求失败，请稍后重试">
      <template #footer>
        <n-button @click="load">重试</n-button>
      </template>
    </n-result>
    <n-space v-else vertical :size="24">
      <h3 class="page-title">盲盒</h3>

      <n-data-table
        remote
        :columns="columns"
        :data="items"
        :loading="loading"
        :pagination="pagination"
        :row-key="(row: BlindBox) => row.id"
      />

      <!-- 概率预览 -->
      <n-card v-if="selected" title="概率预览">
        <n-spin v-if="candidatesLoading" class="preview-spin" />
        <n-space v-else vertical size="small" style="width: 100%">
          <div v-for="c in candidates" :key="String(c.prizeId)" class="candidate-row">
            <span>{{ c.prizeName }}</span>
            <span class="candidate-probability">{{ (c.probability * 100).toFixed(1) }}%</span>
          </div>
          <span v-if="candidates.length === 0" class="muted">暂无候选</span>
        </n-space>
      </n-card>
    </n-space>
  </div>
</template>

<script lang="ts" setup>
  import { computed, h, onMounted, ref, watch } from 'vue';
  import type { DataTableColumns } from 'naive-ui';
  import { NButton, NCard, NResult, NSpace, NSpin, NTag } from 'naive-ui';
  import type { BlindBox, BlindBoxCandidate } from '@/types/api';
  import { getBlindBoxCandidates, listBlindBoxes } from '@/api/prize';

  defineOptions({ name: 'ParentBlindBoxes' });

  const loading = ref(false);
  const error = ref(false);
  const items = ref<BlindBox[]>([]);
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
      const res = await listBlindBoxes({ page: page.value, pageSize: pageSize.value });
      items.value = res.content ?? [];
      total.value = res.totalElements ?? 0;
    } catch {
      error.value = true;
    } finally {
      loading.value = false;
    }
  }

  // ---- 概率预览 ----
  const selected = ref<BlindBox | null>(null);
  const candidatesLoading = ref(false);
  const candidates = ref<BlindBoxCandidate[]>([]);

  const columns: DataTableColumns<BlindBox> = [
    { title: '名称', key: 'name' },
    { title: '价格', key: 'cost', render: (row) => `${row.cost} 积分` },
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
        return h(
          NButton,
          {
            size: 'small',
            type: selected.value?.id === row.id ? 'primary' : 'default',
            onClick: () => (selected.value = row),
          },
          { default: () => '查看概率' }
        );
      },
    },
  ];

  watch(selected, async (box) => {
    if (!box) {
      candidates.value = [];
      return;
    }
    candidatesLoading.value = true;
    try {
      const res = await getBlindBoxCandidates(box.id);
      candidates.value = res.candidates ?? [];
    } catch {
      candidates.value = [];
    } finally {
      candidatesLoading.value = false;
    }
  });

  onMounted(load);
</script>

<style lang="less" scoped>
  .page-spin {
    display: block;
    margin: 80px auto;
  }

  .page-title {
    margin: 0;
    font-size: 22px;
  }

  .preview-spin {
    display: block;
    margin: 24px auto;
  }

  .candidate-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .candidate-probability {
    font-weight: 600;
  }

  .muted {
    color: rgba(0, 0, 0, 0.45);
  }
</style>
