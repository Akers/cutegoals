<template>
  <div>
    <n-spin v-if="loading && items.length === 0" class="page-spin" />
    <n-result v-else-if="error" status="error" title="加载失败" description="请求失败，请稍后重试">
      <template #footer>
        <n-button @click="load">重试</n-button>
      </template>
    </n-result>
    <n-space v-else vertical :size="24">
      <h3 class="page-title">兑换履约</h3>

      <n-data-table
        remote
        :columns="columns"
        :data="items"
        :loading="loading"
        :pagination="pagination"
        :row-key="(row: Exchange) => row.id"
      />
    </n-space>

    <!-- 核销确认 -->
    <n-modal
      :show="confirmId !== null"
      preset="card"
      title="确认核销"
      style="width: 420px"
      @update:show="(v: boolean) => (confirmId = v ? confirmId : null)"
    >
      <span>兑换一旦核销，积分将从孩子账户扣除。请确认已交付奖品。</span>
      <template #footer>
        <n-space justify="end">
          <n-button :disabled="acting" @click="confirmId = null">取消</n-button>
          <n-button
            type="primary"
            :loading="acting"
            @click="confirmId !== null && fulfill(confirmId)"
          >
            确认核销
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 取消兑换 -->
    <n-modal
      :show="cancelRecord !== null"
      preset="card"
      title="取消兑换"
      style="width: 420px"
      @update:show="(v: boolean) => (cancelRecord = v ? cancelRecord : null)"
    >
      <n-space vertical>
        <span>确定取消该兑换记录吗？</span>
        <n-input
          :value="cancelRecord?.reason ?? ''"
          type="textarea"
          placeholder="取消原因（可选）"
          :rows="3"
          @update:value="(v: string) => cancelRecord && (cancelRecord.reason = v)"
        />
      </n-space>
      <template #footer>
        <n-space justify="end">
          <n-button @click="cancelRecord = null">取消</n-button>
          <n-button
            type="primary"
            :loading="acting"
            @click="cancelRecord && cancel(cancelRecord.id, cancelRecord.reason)"
          >
            确认
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script lang="ts" setup>
  import { computed, h, onMounted, ref } from 'vue';
  import dayjs from 'dayjs';
  import type { DataTableColumns } from 'naive-ui';
  import { NButton, NInput, NModal, NResult, NSpace, NSpin, NTag, useMessage } from 'naive-ui';
  import type { Exchange } from '@/types/api';
  import { cancelExchange, fulfillExchange, listExchanges } from '@/api/exchange';
  import { EXCHANGE_STATUS_META } from '../utils';

  defineOptions({ name: 'ParentExchanges' });

  const message = useMessage();

  const loading = ref(false);
  const error = ref(false);
  const items = ref<Exchange[]>([]);
  const page = ref(1);
  const pageSize = ref(10);
  const total = ref(0);
  const acting = ref(false);

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
      const res = await listExchanges({ page: page.value, pageSize: pageSize.value });
      items.value = res.content ?? [];
      total.value = res.totalElements ?? 0;
    } catch {
      error.value = true;
    } finally {
      loading.value = false;
    }
  }

  async function fulfill(id: Exchange['id']) {
    acting.value = true;
    try {
      await fulfillExchange(id);
      confirmId.value = null;
      message.success('已核销');
      await load();
    } catch {
      // 失败提示由 http 层全局处理
    } finally {
      acting.value = false;
    }
  }

  async function cancel(id: Exchange['id'], reason?: string) {
    acting.value = true;
    try {
      await cancelExchange(id, { reason });
      cancelRecord.value = null;
      message.success('已取消');
      await load();
    } catch {
      // 失败提示由 http 层全局处理
    } finally {
      acting.value = false;
    }
  }

  const columns: DataTableColumns<Exchange> = [
    {
      title: '孩子',
      key: 'child',
      render: (row) => `#${row.childId}`,
    },
    {
      title: '奖品',
      key: 'prize',
      render: (row) => (row.type === 'DIRECT' ? `奖品 #${row.prizeId}` : `盲盒 #${row.poolId}`),
    },
    { title: '积分', key: 'costPoints' },
    {
      title: '类型',
      key: 'type',
      render(row) {
        return row.type === 'DIRECT'
          ? h(NTag, { size: 'small' }, { default: () => '直接兑换' })
          : h(NTag, { type: 'info', size: 'small' }, { default: () => '盲盒兑换' });
      },
    },
    {
      title: '状态',
      key: 'status',
      render(row) {
        const meta = EXCHANGE_STATUS_META[row.status];
        return h(
          NTag,
          { type: meta?.tagType ?? 'default', size: 'small' },
          { default: () => meta?.label ?? row.status }
        );
      },
    },
    {
      title: '创建时间',
      key: 'createdAt',
      render: (row) => dayjs(row.createdAt).format('YYYY-MM-DD HH:mm'),
    },
    {
      title: '操作',
      key: 'actions',
      render(row) {
        if (row.status !== 'PENDING_FULFILLMENT') {
          return h('span', { style: 'color: rgba(0,0,0,0.45)' }, '-');
        }
        return h(NSpace, null, {
          default: () => [
            h(
              NButton,
              {
                size: 'small',
                type: 'primary',
                loading: acting.value,
                onClick: () => (confirmId.value = row.id),
              },
              { default: () => '核销' }
            ),
            h(
              NButton,
              {
                size: 'small',
                loading: acting.value,
                onClick: () => (cancelRecord.value = { id: row.id, reason: '' }),
              },
              { default: () => '取消' }
            ),
          ],
        });
      },
    },
  ];

  // ---- 核销/取消弹窗 ----
  const confirmId = ref<Exchange['id'] | null>(null);
  const cancelRecord = ref<{ id: Exchange['id']; reason: string } | null>(null);

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
</style>
