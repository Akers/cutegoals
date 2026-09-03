<template>
  <div>
    <n-spin v-if="loading" class="page-spin" />
    <n-result v-else-if="error" status="error" title="加载失败" description="请求失败，请稍后重试">
      <template #footer>
        <n-button @click="load">重试</n-button>
      </template>
    </n-result>
    <n-empty v-else-if="!family" description="暂无数据" class="page-empty" />
    <n-space v-else vertical :size="24">
      <div class="page-header">
        <h3 class="page-title">家庭</h3>
        <n-space>
          <n-button @click="router.push('/parent/family')">管理家庭</n-button>
          <n-button @click="router.push('/parent/templates')">任务模板</n-button>
        </n-space>
      </div>

      <n-card title="家庭成员">
        <n-space vertical :size="8">
          <div v-for="member in family.members ?? []" :key="member.id" class="member-row">
            <span class="member-name">
              {{ member.nickname ?? maskPhone(member.phone ?? '') }}
            </span>
            <n-tag>{{ statusLabel(member.role === 'PARENT' ? 'approved' : 'pending') }}</n-tag>
          </div>
        </n-space>
      </n-card>
    </n-space>
  </div>
</template>

<script lang="ts" setup>
  import { onMounted, ref } from 'vue';
  import { useRouter } from 'vue-router';
  import { NButton, NCard, NEmpty, NResult, NSpace, NSpin, NTag } from 'naive-ui';
  import type { Family } from '@/types/api';
  import { getFamily } from '@/api/family';
  import { maskPhone, statusLabel } from '../utils';

  defineOptions({ name: 'ParentHome' });

  const router = useRouter();

  const loading = ref(false);
  const error = ref(false);
  const family = ref<Family | null>(null);

  async function load() {
    loading.value = true;
    error.value = false;
    try {
      family.value = await getFamily();
    } catch {
      // 失败提示由 http 层全局处理
      error.value = true;
    } finally {
      loading.value = false;
    }
  }

  onMounted(load);
</script>

<style lang="less" scoped>
  .page-spin {
    display: block;
    margin: 80px auto;
  }

  .page-empty {
    margin: 80px 0;
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

  .member-row {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .member-name {
      font-weight: 600;
    }
  }
</style>
