<template>
  <div class="admin-page">
    <n-h3 class="admin-page-title">账号管理</n-h3>

    <n-result v-if="errorMsg" status="error" title="加载失败" :description="errorMsg">
      <template #footer>
        <n-button type="primary" @click="reload">重试</n-button>
      </template>
    </n-result>

    <n-spin v-else :show="loading">
      <div v-if="loading && !data" class="admin-page-space"></div>
      <n-empty
        v-else-if="!data || data.content.length === 0"
        description="暂无账号"
        class="admin-page-space"
      />
      <template v-else-if="data">
        <n-data-table
          remote
          :columns="columns"
          :data="data.content"
          :row-key="(row: Account) => row.id"
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
  import { disableAccount, enableAccount, listAccounts } from '@/api/admin';
  import type { Account, PageResult } from '@/types/api';
  import { usePageData } from '@/views/admin/composables/usePageData';
  import { maskPhone, statusLabel } from '@/views/admin/utils';

  // 旧版内部为 0 基页码，请求时 +1（后端 1 基）
  const page = ref(0);
  const acting = ref<Account['id'] | null>(null);

  const { data, loading, errorMsg, reload } = usePageData<PageResult<Account>>(() =>
    listAccounts({ page: page.value + 1 })
  );

  const columns: DataTableColumns<Account> = [
    { title: '手机号', key: 'phone', render: (row) => maskPhone(row.phone) },
    { title: '角色', key: 'roles', render: (row) => row.roles.join(', ') },
    {
      title: '状态',
      key: 'status',
      render: (row) =>
        h(
          NTag,
          { type: row.status === 'ACTIVE' ? 'success' : 'error' },
          { default: () => statusLabel(row.status) }
        ),
    },
    {
      title: '操作',
      key: 'actions',
      render: (row) =>
        h(
          NButton,
          {
            size: 'small',
            type: row.status === 'ACTIVE' ? 'default' : 'primary',
            loading: acting.value === row.id,
            onClick: () => toggle(row),
          },
          { default: () => (row.status === 'ACTIVE' ? '停用' : '启用') }
        ),
    },
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

  /** 启用/停用（旧版为直接点击，无二次确认）；失败提示由 http 层弹出，成败均刷新列表 */
  async function toggle(row: Account) {
    acting.value = row.id;
    try {
      if (row.status === 'ACTIVE') {
        await disableAccount(row.id);
      } else {
        await enableAccount(row.id);
      }
    } catch {
      // 保持页面，等待刷新
    } finally {
      await reload();
      acting.value = null;
    }
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
