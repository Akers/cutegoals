<template>
  <div class="admin-page">
    <div class="health-header">
      <n-h3 class="admin-page-title">健康面板</n-h3>
      <n-button :loading="loading" @click="reload">刷新</n-button>
    </div>

    <n-result v-if="errorMsg" status="error" title="加载失败" :description="errorMsg">
      <template #footer>
        <n-button type="primary" @click="reload">重试</n-button>
      </template>
    </n-result>

    <n-spin v-else :show="loading">
      <div v-if="loading && !data" class="admin-page-space"></div>
      <n-empty v-else-if="!data" description="暂无健康数据" class="admin-page-space" />
      <template v-else-if="data">
        <n-card title="整体状态" size="small" :bordered="false" class="health-block">
          <n-tag :type="overall ? 'success' : 'error'">{{ overall ? '正常' : '异常' }}</n-tag>
        </n-card>
        <n-card
          v-for="check in checks"
          :key="check.name"
          size="small"
          :bordered="false"
          class="health-block"
        >
          <div class="health-check-row">
            <span class="health-check-name">{{ check.name }}</span>
            <n-tag size="small" :type="check.healthy ? 'success' : 'error'">
              {{ check.healthy ? '正常' : '异常' }}
            </n-tag>
          </div>
          <n-text depth="3" class="health-check-msg">{{ check.message }}</n-text>
        </n-card>
      </template>
    </n-spin>
  </div>
</template>

<script lang="ts" setup>
  import { computed } from 'vue';
  import { NButton, NCard, NEmpty, NH3, NResult, NSpin, NTag, NText } from 'naive-ui';
  import { getHealth } from '@/api/admin';
  import type { HealthData } from '@/types/api';
  import { usePageData } from '@/views/admin/composables/usePageData';

  interface HealthCheck {
    name: string;
    healthy: boolean;
    message: string;
  }

  const { data, loading, errorMsg, reload } = usePageData<HealthData>(() => getHealth());

  const overall = computed(() => data.value?.status === 'UP');

  // 依赖健康检查项（数据库 / 备份 / 恢复演练 / RPO 警告），与旧版展示逻辑一致
  const checks = computed<HealthCheck[]>(() => {
    const d = data.value;
    if (!d) return [];
    const list: HealthCheck[] = [];

    const db = d.database;
    list.push({
      name: '数据库',
      healthy: db?.status === 'UP',
      message: db ? `类型: ${db.type ?? '未知'}` : '未连接',
    });

    const backup = d.backup;
    list.push({
      name: '备份',
      healthy: backup?.lastBackupStatus === 'SUCCESS',
      message:
        !backup?.lastBackupTime || backup.lastBackupStatus === 'NEVER'
          ? '从未备份'
          : `上次备份: ${backup.lastBackupTime} (${backup.lastBackupStatus})`,
    });

    const drill = d.recoveryDrill;
    list.push({
      name: '恢复演练',
      healthy: drill?.lastRecoveryDrillStatus === 'SUCCESS',
      message:
        !drill?.lastRecoveryDrillTime || drill.lastRecoveryDrillStatus === 'NEVER'
          ? '从未演练'
          : `上次演练: ${drill.lastRecoveryDrillTime} (${drill.lastRecoveryDrillStatus})`,
    });

    if (d.rpoWarning) {
      list.push({
        name: 'RPO 警告',
        healthy: false,
        message: d.rpoWarningMessage ?? 'RPO 超出阈值',
      });
    }
    return list;
  });
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

  .health-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .health-block {
    margin-bottom: 12px;
  }

  .health-check-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 4px;
  }

  .health-check-name {
    font-weight: 600;
  }

  .health-check-msg {
    display: inline-block;
  }
</style>
