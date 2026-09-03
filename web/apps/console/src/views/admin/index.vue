<template>
  <div class="admin-page">
    <n-h3 class="admin-page-title">实例概览</n-h3>

    <n-result v-if="errorMsg" status="error" title="加载失败" :description="errorMsg">
      <template #footer>
        <n-button type="primary" @click="reload">重试</n-button>
      </template>
    </n-result>

    <n-spin v-else :show="loading">
      <div v-if="loading && !data" class="admin-page-space"></div>
      <n-empty v-else-if="!data" description="暂无数据" class="admin-page-space" />
      <template v-else-if="data">
        <n-grid cols="1 m:2" :x-gap="16" :y-gap="16">
          <n-grid-item>
            <n-card title="初始化状态" :bordered="false">
              <n-space align="center">
                <n-tag :type="initialized ? 'success' : 'default'">
                  {{ initialized ? '已初始化' : '未初始化' }}
                </n-tag>
                <n-text depth="3">{{ data.version ?? '—' }}</n-text>
              </n-space>
            </n-card>
          </n-grid-item>
          <n-grid-item>
            <n-card title="备份状态" :bordered="false">
              <n-space v-if="data.lastBackupAt" align="center">
                <n-tag :type="data.lastBackupStatus === 'SUCCESS' ? 'success' : 'error'">
                  {{ data.lastBackupStatus === 'SUCCESS' ? '成功' : '失败' }}
                </n-tag>
                <n-text depth="3">{{ data.lastBackupAt }}</n-text>
              </n-space>
              <n-text v-else depth="3">暂无备份记录</n-text>
            </n-card>
          </n-grid-item>
          <n-grid-item>
            <n-card title="恢复演练" :bordered="false">
              <n-space v-if="data.recoveryDrill" vertical size="small">
                <n-space align="center">
                  <span>结果</span>
                  <n-tag :type="data.recoveryDrill.success ? 'success' : 'error'">
                    {{ data.recoveryDrill.success ? '成功' : '失败' }}
                  </n-tag>
                </n-space>
                <n-text depth="3">RPO: {{ data.recoveryDrill.rpo }}</n-text>
                <n-text depth="3">RTO: {{ data.recoveryDrill.rto }}</n-text>
                <n-text depth="3">时间: {{ data.recoveryDrill.ranAt }}</n-text>
              </n-space>
              <n-text v-else depth="3">尚未运行恢复演练</n-text>
            </n-card>
          </n-grid-item>
          <n-grid-item>
            <n-card title="快捷入口" :bordered="false">
              <n-space>
                <n-button @click="router.push('/admin/accounts')">账号管理</n-button>
                <n-button @click="router.push('/admin/audit')">审计日志</n-button>
                <n-button @click="router.push('/admin/health')">健康面板</n-button>
                <n-button @click="router.push('/admin/config')">系统配置</n-button>
              </n-space>
            </n-card>
          </n-grid-item>
        </n-grid>
      </template>
    </n-spin>
  </div>
</template>

<script lang="ts" setup>
  import { computed } from 'vue';
  import { useRouter } from 'vue-router';
  import {
    NButton,
    NCard,
    NEmpty,
    NGrid,
    NGridItem,
    NH3,
    NResult,
    NSpin,
    NSpace,
    NTag,
    NText,
  } from 'naive-ui';
  import { getInstanceStatus } from '@/api/admin';
  import type { InstanceStatus } from '@/types/api';
  import { usePageData } from '@/views/admin/composables/usePageData';

  const router = useRouter();
  const { data, loading, errorMsg, reload } = usePageData<InstanceStatus>(() =>
    getInstanceStatus()
  );

  // 与旧版一致：initialized 为空时回退按 instanceStatus 判断
  const initialized = computed(
    () => data.value?.initialized ?? (data.value?.instanceStatus === 'INITIALIZED' || false)
  );
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
