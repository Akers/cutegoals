<template>
  <div>
    <n-spin v-if="loading" class="page-spin" />
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
      <h3 class="page-title">积分</h3>

      <!-- 选择孩子 -->
      <n-card title="选择孩子">
        <n-select
          v-model:value="selectedChild"
          :options="childOptions"
          placeholder="请选择孩子"
          clearable
          style="width: 100%"
        />
      </n-card>

      <template v-if="selectedChild">
        <!-- 积分余额 -->
        <n-card title="积分余额">
          <div class="balance">{{ ledger?.currentBalance ?? 0 }} 积分</div>
        </n-card>

        <!-- 积分调整 -->
        <n-card title="积分调整">
          <n-space vertical :size="16">
            <n-form label-placement="top" :show-feedback="false">
              <n-form-item label="调整数量（正数奖励、负数扣除）">
                <n-input-number
                  v-model:value="amount"
                  :precision="0"
                  placeholder="请输入调整数量"
                  style="width: 100%"
                />
              </n-form-item>
              <n-form-item label="原因">
                <n-input v-model:value="reason" placeholder="请输入原因" />
              </n-form-item>
            </n-form>
            <n-button :loading="adjusting" @click="handleAdjust">确认调整</n-button>
          </n-space>
        </n-card>

        <!-- 流水 -->
        <n-card title="流水">
          <n-space vertical size="small">
            <div v-for="tx in ledger?.content ?? []" :key="String(tx.id)" class="tx-row">
              <n-space vertical size="tiny">
                <span>{{ tx.reason ?? tx.type }}</span>
                <span class="tx-sub">{{ tx.createdAt }}</span>
              </n-space>
              <span class="tx-amount" :style="{ color: tx.amount >= 0 ? '#52c41a' : '#ff4d4f' }">
                {{ tx.amount > 0 ? '+' : '' }}{{ tx.amount }}
              </span>
            </div>
            <span v-if="(ledger?.content ?? []).length === 0" class="muted">暂无流水</span>
          </n-space>
        </n-card>
      </template>
    </n-space>
  </div>
</template>

<script lang="ts" setup>
  import { computed, onMounted, ref, watch } from 'vue';
  import {
    NButton,
    NCard,
    NForm,
    NFormItem,
    NInput,
    NInputNumber,
    NResult,
    NSelect,
    NSpace,
    NSpin,
    useMessage,
  } from 'naive-ui';
  import type { ChildProfile, PointsLedger } from '@/types/api';
  import { createAdjustment, getLedger } from '@/api/points';
  import { listChildren } from '@/api/children';

  defineOptions({ name: 'ParentPoints' });

  const message = useMessage();

  const loading = ref(false);
  const loadError = ref(false);
  const children = ref<ChildProfile[]>([]);
  const selectedChild = ref<string | null>(null);
  const ledger = ref<PointsLedger | null>(null);

  const childOptions = computed(() =>
    children.value.map((c) => ({ label: c.nickname, value: String(c.id) }))
  );

  async function loadChildren() {
    try {
      const res = await listChildren();
      children.value = res.content ?? [];
    } catch {
      // 孩子列表加载失败时下拉为空，不阻塞页面
    }
  }

  async function refetch() {
    if (!selectedChild.value) {
      ledger.value = null;
      return;
    }
    loading.value = true;
    loadError.value = false;
    try {
      ledger.value = await getLedger(selectedChild.value);
    } catch {
      loadError.value = true;
    } finally {
      loading.value = false;
    }
  }

  watch(selectedChild, () => {
    void refetch();
  });

  // ---- 手工调整 ----
  const adjusting = ref(false);
  const amount = ref<number | null>(null);
  const reason = ref('');

  async function handleAdjust() {
    if (!selectedChild.value) return;
    const amt = Number(amount.value);
    if (!amt || !reason.value.trim()) {
      message.error('请填写调整数量和原因');
      return;
    }
    const businessRef = `${Date.now()}_${Math.random().toString(36).substring(2, 8)}`;
    adjusting.value = true;
    try {
      await createAdjustment({
        childId: Number(selectedChild.value),
        amount: amt,
        reason: reason.value,
        businessRef,
      });
      amount.value = null;
      reason.value = '';
      message.success('积分已调整');
      await refetch();
    } catch {
      // 失败提示由 http 层全局处理
    } finally {
      adjusting.value = false;
    }
  }

  onMounted(loadChildren);
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

  .balance {
    font-size: 28px;
    font-weight: 600;
  }

  .tx-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .tx-sub {
    font-size: 12px;
    color: rgba(0, 0, 0, 0.45);
  }

  .tx-amount {
    font-weight: 600;
  }

  .muted {
    color: rgba(0, 0, 0, 0.45);
  }
</style>
