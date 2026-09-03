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
      <h3 class="page-title">设备管理</h3>

      <!-- 授权新设备 -->
      <n-card title="授权新设备">
        <n-space vertical :size="16">
          <span>输入孩子设备上显示的 deviceId 进行授权：</span>
          <n-input v-model:value="deviceId" placeholder="请输入 deviceId" />
          <n-button :loading="binding" :disabled="!deviceId.trim()" @click="handleBind">
            授权
          </n-button>
        </n-space>
      </n-card>

      <!-- 设备列表 -->
      <n-data-table
        v-if="devices.length > 0"
        :columns="deviceColumns"
        :data="devices"
        :pagination="false"
        :row-key="(row: DeviceBinding) => row.id"
      />

      <!-- 手动解绑（无设备列表时的后备方案） -->
      <n-card v-if="devices.length === 0" title="解绑设备">
        <n-space vertical :size="16">
          <span>输入设备绑定 ID 进行解绑：</span>
          <n-input v-model:value="manualUnbindId" placeholder="请输入设备绑定 ID" />
          <n-button
            type="error"
            secondary
            :loading="unbinding"
            :disabled="!manualUnbindId.trim()"
            @click="handleManualUnbind"
          >
            解绑
          </n-button>
        </n-space>
      </n-card>
    </n-space>

    <!-- 授权成功凭据 -->
    <n-modal
      :show="bindCredential !== null"
      preset="card"
      title="设备授权成功"
      style="width: 480px"
      @update:show="(v: boolean) => (bindCredential = v ? bindCredential : null)"
    >
      <n-space vertical :size="16">
        <n-alert type="success" show-icon>请将此凭据安全传递给孩子的设备</n-alert>
        <div>
          <div class="field-label">一次性凭据</div>
          <n-input
            :value="bindCredential ?? ''"
            type="textarea"
            readonly
            :rows="3"
            class="credential-input"
          />
        </div>
      </n-space>
      <template #footer>
        <n-space justify="end">
          <n-button @click="bindCredential = null">关闭</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 解绑确认 -->
    <n-modal
      :show="unbindId !== null"
      preset="card"
      title="确认解绑"
      style="width: 420px"
      @update:show="(v: boolean) => (unbindId = v ? unbindId : null)"
    >
      <span>解绑后该设备将无法使用家庭功能，是否继续？</span>
      <template #footer>
        <n-space justify="end">
          <n-button @click="unbindId = null">取消</n-button>
          <n-button type="error" secondary :loading="unbinding" @click="handleUnbind"
            >解绑</n-button
          >
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script lang="ts" setup>
  import { computed, h, onMounted, ref } from 'vue';
  import type { DataTableColumns } from 'naive-ui';
  import {
    NAlert,
    NButton,
    NCard,
    NEmpty,
    NInput,
    NModal,
    NResult,
    NSpace,
    NSpin,
    NTag,
    NText,
    useMessage,
  } from 'naive-ui';
  import type { DeviceBinding, Family } from '@/types/api';
  import { bindDevice, unbindDevice } from '@/api/device';
  import { getFamily } from '@/api/family';
  import { statusLabel } from '../utils';

  defineOptions({ name: 'ParentDevices' });

  const message = useMessage();

  const family = ref<Family | null>(null);
  const loading = ref(false);
  const error = ref(false);
  const devices = computed(() => family.value?.devices ?? []);

  async function load() {
    loading.value = true;
    error.value = false;
    try {
      family.value = await getFamily();
    } catch {
      error.value = true;
    } finally {
      loading.value = false;
    }
  }

  // ---- 设备列表 ----
  const deviceColumns: DataTableColumns<DeviceBinding> = [
    {
      title: '设备 ID',
      key: 'deviceId',
      render(row) {
        const id = row.deviceId;
        return h(
          NText,
          { copyable: { text: id } },
          { default: () => (id.length > 20 ? `${id.slice(0, 20)}...` : id) }
        );
      },
    },
    {
      title: '状态',
      key: 'status',
      render: (row) => h(NTag, { size: 'small' }, { default: () => statusLabel(row.status) }),
    },
    { title: '绑定时间', key: 'createdAt' },
    {
      title: '操作',
      key: 'actions',
      render(row) {
        return h(
          NButton,
          {
            size: 'small',
            type: 'error',
            secondary: true,
            onClick: () => (unbindId.value = row.id),
          },
          { default: () => '解绑' }
        );
      },
    },
  ];

  // ---- 授权 ----
  const deviceId = ref('');
  const binding = ref(false);
  const bindCredential = ref<string | null>(null);

  async function handleBind() {
    if (!deviceId.value.trim()) return;
    binding.value = true;
    try {
      const res = await bindDevice({ deviceId: deviceId.value.trim() });
      if (res.credential) {
        bindCredential.value = res.credential;
      }
      deviceId.value = '';
      await load();
    } catch {
      // 失败提示由 http 层全局处理
    } finally {
      binding.value = false;
    }
  }

  // ---- 解绑 ----
  const unbindId = ref<DeviceBinding['id'] | null>(null);
  const unbinding = ref(false);
  const manualUnbindId = ref('');

  async function handleUnbind() {
    if (unbindId.value === null) return;
    unbinding.value = true;
    try {
      await unbindDevice(unbindId.value);
      message.success('设备已解绑');
      unbindId.value = null;
      await load();
    } catch {
      // 失败提示由 http 层全局处理
    } finally {
      unbinding.value = false;
    }
  }

  async function handleManualUnbind() {
    if (!manualUnbindId.value.trim()) return;
    const id = Number(manualUnbindId.value.trim());
    if (Number.isNaN(id)) {
      message.error('请输入有效的设备绑定 ID');
      return;
    }
    unbinding.value = true;
    try {
      await unbindDevice(id);
      message.success('设备已解绑');
      manualUnbindId.value = '';
      await load();
    } catch {
      // 失败提示由 http 层全局处理
    } finally {
      unbinding.value = false;
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
    margin: 80px auto;
  }

  .page-title {
    margin: 0;
    font-size: 22px;
  }

  .field-label {
    display: block;
    margin-bottom: 4px;
    font-weight: 600;
  }

  .credential-input {
    background-color: rgba(0, 0, 0, 0.04);
  }
</style>
