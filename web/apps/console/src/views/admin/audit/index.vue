<template>
  <div class="admin-page">
    <n-h3 class="admin-page-title">审计日志</n-h3>

    <n-result v-if="errorMsg" status="error" title="加载失败" :description="errorMsg">
      <template #footer>
        <n-button type="primary" @click="reload">重试</n-button>
      </template>
    </n-result>

    <n-spin v-else :show="loading">
      <div v-if="loading && !data" class="admin-page-space"></div>
      <n-empty
        v-else-if="!data || data.content.length === 0"
        description="暂无审计日志"
        class="admin-page-space"
      />
      <template v-else-if="data">
        <n-data-table
          remote
          :columns="columns"
          :data="data.content"
          :row-key="(row: AuditLog) => row.id"
          :pagination="pagination"
          @update:page="handlePageChange"
        />
      </template>
    </n-spin>
  </div>
</template>

<script lang="ts" setup>
  import { computed, h, ref } from 'vue';
  import { NButton, NDataTable, NEmpty, NH3, NResult, NSpin, NTag } from 'naive-ui';
  import type { DataTableColumns, PaginationProps } from 'naive-ui';
  import { listAuditLogs } from '@/api/admin';
  import type { AuditLog, PageResult } from '@/types/api';
  import { usePageData } from '@/views/admin/composables/usePageData';
  import { statusLabel } from '@/views/admin/utils';

  // 旧版内部为 0 基页码，请求时 +1（后端 1 基）
  const page = ref(0);

  const { data, loading, errorMsg, reload } = usePageData<PageResult<AuditLog>>(() =>
    listAuditLogs({ page: page.value + 1 })
  );

  const columns: DataTableColumns<AuditLog> = [
    { title: '时间', key: 'createdAt' },
    { title: '操作者', key: 'actorId', render: (row) => `${row.actorType}#${row.actorId}` },
    { title: '动作', key: 'eventType' },
    {
      title: '结果',
      key: 'result',
      render: (row) => h(NTag, null, { default: () => statusLabel(row.result) }),
    },
    { title: '对象', key: 'objectId', render: (row) => `${row.objectType}#${row.objectId}` },
  ];

  // 与旧版一致：仅一页时不展示分页器
  const pagination = computed<false | PaginationProps>(() => {
    const d = data.value;
    if (!d || d.totalPages <= 1) return false;
    return { page: d.page, pageSize: d.pageSize, itemCount: d.totalElements };
  });

  function handlePageChange(current: number) {
    page.value = current - 1;
    void reload();
  }
</script>

<style lang="less" scoped>
  .admin-page {
    padding: 16px;
  }

  .admin-page-title {
    margin-top: 0;
  }

  .admin-page-space {
    min-height: 240px;
    display: flex;
    align-items: center;
    justify-content: center;
  }
</style>
