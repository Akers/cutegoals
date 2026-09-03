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
      <h3 class="page-title">任务审核</h3>

      <!-- 待审核 -->
      <n-card title="待审核">
        <n-space vertical size="small">
          <span v-if="pending.length === 0" class="muted">暂无待审核任务</span>
          <n-card
            v-for="item in pending"
            :key="item.attemptId"
            size="small"
            :class="{ overdue: item.isOverdue }"
          >
            <div class="review-item">
              <n-space vertical size="tiny">
                <span class="review-title">{{ item.templateTitle }}</span>
                <span class="review-sub">{{ item.childNickname }} · {{ item.submittedAt }}</span>
                <span v-if="item.notes" class="review-notes">{{ item.notes }}</span>
                <span v-if="item.isOverdue" class="review-overdue">已逾期</span>
              </n-space>
              <n-space vertical size="tiny">
                <n-button size="small" :loading="submitting" @click="decide(item, true)">
                  通过
                </n-button>
                <n-button size="small" type="error" @click="openReject(item)">驳回</n-button>
              </n-space>
            </div>
          </n-card>
        </n-space>
      </n-card>

      <!-- 审核历史 -->
      <n-card title="审核历史">
        <n-space vertical size="small">
          <span v-if="history.length === 0" class="muted">暂无历史</span>
          <n-card v-for="item in history" :key="item.attemptId" size="small">
            <span class="review-title">{{ item.templateTitle }}</span>
            <div class="review-sub"> {{ item.childNickname }} · {{ item.submittedAt }} </div>
          </n-card>
        </n-space>
      </n-card>
    </n-space>

    <!-- 驳回原因 -->
    <n-modal v-model:show="showReject" preset="card" title="驳回原因" style="width: 420px">
      <n-input
        v-model:value="reason"
        type="textarea"
        placeholder="请输入驳回原因"
        :autos="{ minRows: 3 }"
      />
      <template #footer>
        <n-space justify="end">
          <n-button @click="closeReject">取消</n-button>
          <n-button
            type="error"
            :loading="submitting"
            :disabled="!reason.trim()"
            @click="confirmReject"
          >
            确认驳回
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script lang="ts" setup>
  import { onMounted, ref } from 'vue';
  import { NButton, NCard, NInput, NModal, NResult, NSpace, NSpin } from 'naive-ui';
  import type { ReviewItem } from '@/types/api';
  import {
    approveAttempt,
    listPendingReviews,
    listReviewHistory,
    rejectAttempt,
  } from '@/api/review';

  defineOptions({ name: 'ParentReviews' });

  const loading = ref(false);
  const loadError = ref(false);
  const pending = ref<ReviewItem[]>([]);
  const history = ref<ReviewItem[]>([]);
  const submitting = ref(false);

  async function refetch() {
    loading.value = true;
    loadError.value = false;
    try {
      const [p, h] = await Promise.all([listPendingReviews(), listReviewHistory()]);
      pending.value = p.content ?? [];
      history.value = h.content ?? [];
    } catch {
      loadError.value = true;
    } finally {
      loading.value = false;
    }
  }

  async function decide(item: ReviewItem, approved: boolean) {
    submitting.value = true;
    try {
      if (approved) {
        await approveAttempt(item.attemptId);
      } else {
        await rejectAttempt(item.attemptId, { reason: reason.value });
      }
      await refetch();
    } catch {
      // 失败提示由 http 层全局处理
    } finally {
      submitting.value = false;
    }
  }

  // ---- 驳回弹窗 ----
  const showReject = ref(false);
  const reason = ref('');
  const active = ref<ReviewItem | null>(null);

  function openReject(item: ReviewItem) {
    active.value = item;
    reason.value = '';
    showReject.value = true;
  }

  function closeReject() {
    showReject.value = false;
    active.value = null;
    reason.value = '';
  }

  async function confirmReject() {
    if (!active.value) return;
    await decide(active.value, false);
    closeReject();
  }

  onMounted(refetch);
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

  .muted {
    color: rgba(0, 0, 0, 0.45);
  }

  .review-item {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
  }

  .review-title {
    font-weight: 600;
  }

  .review-sub {
    font-size: 12px;
    color: rgba(0, 0, 0, 0.45);
  }

  .review-notes {
    font-size: 12px;
  }

  .review-overdue {
    font-size: 12px;
    font-weight: 600;
    color: #faad14;
  }

  .overdue {
    border-left: 4px solid #faad14;
  }
</style>
