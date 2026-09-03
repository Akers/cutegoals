<template>
  <div class="admin-page">
    <n-h3 class="admin-page-title">系统配置</n-h3>

    <n-result v-if="errorMsg" status="error" title="加载失败" :description="errorMsg">
      <template #footer>
        <n-button type="primary" @click="reload">重试</n-button>
      </template>
    </n-result>

    <n-spin v-else :show="loading">
      <div v-if="loading && !data" class="admin-page-space"></div>
      <n-empty
        v-else-if="!data || data.length === 0"
        description="暂无配置"
        class="admin-page-space"
      />
      <n-card v-else title="系统配置" :bordered="false">
        <template #header-extra>
          <n-button type="primary" size="small" :loading="saving" @click="handleSave">
            保存配置
          </n-button>
        </template>
        <div class="config-list">
          <div v-for="entry in data" :key="entry.key" class="config-item">
            <div class="config-meta">
              <div class="config-key">{{ entry.key }}</div>
              <div v-if="entry.description" class="config-desc">
                <n-text depth="3">{{ entry.description }}</n-text>
              </div>
            </div>
            <!-- masked 配置项以密码框展示；敏感值不入日志、不回显明文 -->
            <n-input
              v-model:value="values[entry.key]"
              class="config-input"
              :type="entry.masked ? 'password' : 'text'"
            />
          </div>
        </div>
      </n-card>
    </n-spin>
  </div>
</template>

<script lang="ts" setup>
  import { ref } from 'vue';
  import { NButton, NCard, NEmpty, NH3, NInput, NResult, NSpin, NText, useMessage } from 'naive-ui';
  import { getConfig, updateConfig } from '@/api/admin';
  import type { ConfigEntry, ConfigUpdatePayload } from '@/types/api';
  import { usePageData } from '@/views/admin/composables/usePageData';

  const message = useMessage();
  const values = ref<Record<string, string>>({});
  const original = ref<Record<string, string>>({});
  const saving = ref(false);

  const { data, loading, errorMsg, reload } = usePageData<ConfigEntry[]>(async () => {
    const entries = await getConfig();
    const init: Record<string, string> = {};
    for (const entry of entries) {
      init[entry.key] = entry.value ?? '';
    }
    values.value = init;
    original.value = { ...init };
    return entries;
  });

  /** 与旧版一致：只提交被修改的键；masked 未改动的值不会写回 */
  async function handleSave() {
    const payload: ConfigUpdatePayload = {};
    for (const key of Object.keys(values.value)) {
      if (values.value[key] !== original.value[key]) {
        payload[key] = values.value[key];
      }
    }
    if (Object.keys(payload).length === 0) {
      message.info('没有需要保存的变更');
      return;
    }
    saving.value = true;
    try {
      await updateConfig(payload);
      message.success('保存成功');
      await reload();
    } catch {
      // 失败提示由 http 层全局弹出，停留在当前页
    } finally {
      saving.value = false;
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

  .config-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 24px;
    padding: 12px 0;
    border-bottom: 1px solid rgb(239, 239, 245);

    &:last-child {
      border-bottom: none;
    }
  }

  .config-meta {
    flex: 1;
    min-width: 0;
  }

  .config-key {
    font-weight: 600;
    word-break: break-all;
  }

  .config-desc {
    margin-top: 2px;
  }

  .config-input {
    width: 280px;
    flex-shrink: 0;
  }
</style>
