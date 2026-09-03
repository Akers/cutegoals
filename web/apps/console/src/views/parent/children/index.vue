<template>
  <div>
    <n-spin v-if="loading && items.length === 0" class="page-spin" />
    <n-result v-else-if="error" status="error" title="加载失败" description="请求失败，请稍后重试">
      <template #footer>
        <n-button @click="load">重试</n-button>
      </template>
    </n-result>
    <n-space v-else vertical :size="24">
      <div class="page-header">
        <h3 class="page-title">孩子档案</h3>
        <n-button @click="openNew">新增档案</n-button>
      </div>

      <n-data-table
        remote
        :columns="columns"
        :data="items"
        :loading="loading"
        :pagination="pagination"
        :row-key="(row: ChildProfile) => row.id"
      />
    </n-space>

    <!-- 新增/编辑档案 -->
    <n-modal
      v-model:show="showModal"
      preset="card"
      :title="editing ? '编辑档案' : '新增档案'"
      style="width: 420px"
    >
      <n-form ref="formRef" :model="form" :rules="rules" label-placement="top">
        <n-form-item label="昵称" path="nickname">
          <n-input v-model:value="form.nickname" />
        </n-form-item>
        <n-form-item :label="editing ? '新 PIN（留空不修改）' : 'PIN'" path="pin">
          <n-input v-model:value="form.pin" type="password" show-password-on="click" />
        </n-form-item>
        <n-form-item label="生日" path="birthday">
          <n-date-picker
            v-model:formatted-value="form.birthday"
            type="date"
            value-format="yyyy-MM-dd"
            clearable
            style="width: 100%"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleSave">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script lang="ts" setup>
  import { computed, h, onMounted, reactive, ref } from 'vue';
  import type { DataTableColumns, FormInst, FormRules } from 'naive-ui';
  import {
    NButton,
    NDatePicker,
    NForm,
    NFormItem,
    NInput,
    NModal,
    NPopconfirm,
    NResult,
    NSpace,
    NSpin,
    useMessage,
  } from 'naive-ui';
  import type { ChildProfile } from '@/types/api';
  import { createChild, deleteChild, listChildren, updateChild } from '@/api/children';

  defineOptions({ name: 'ParentChildren' });

  const message = useMessage();

  const loading = ref(false);
  const error = ref(false);
  const items = ref<ChildProfile[]>([]);
  const page = ref(1);
  const pageSize = ref(10);
  const total = ref(0);

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
      const res = await listChildren({ page: page.value, pageSize: pageSize.value });
      items.value = res.content ?? [];
      total.value = res.totalElements ?? 0;
    } catch {
      error.value = true;
    } finally {
      loading.value = false;
    }
  }

  const columns: DataTableColumns<ChildProfile> = [
    { title: '名称', key: 'nickname' },
    { title: '年龄', key: 'birthday', render: (row) => row.birthday ?? '-' },
    {
      title: '操作',
      key: 'actions',
      render(row) {
        return h(NSpace, null, {
          default: () => [
            h(
              NButton,
              { text: true, size: 'small', onClick: () => openEdit(row) },
              { default: () => '编辑' }
            ),
            h(
              NPopconfirm,
              { onPositiveClick: () => handleDelete(row.id) },
              {
                trigger: () =>
                  h(
                    NButton,
                    { size: 'small', type: 'error', ghost: true },
                    { default: () => '删除' }
                  ),
                default: () => `确定删除孩子「${row.nickname}」的档案吗？`,
              }
            ),
          ],
        });
      },
    },
  ];

  // ---- 新增/编辑 ----
  const showModal = ref(false);
  const saving = ref(false);
  const editing = ref<ChildProfile | null>(null);
  const formRef = ref<FormInst | null>(null);
  const form = ref({ nickname: '', pin: '', birthday: null as string | null });
  const rules: FormRules = {
    nickname: [{ required: true, message: '请输入昵称' }],
  };

  function openNew() {
    editing.value = null;
    form.value = { nickname: '', pin: '', birthday: null };
    showModal.value = true;
  }

  function openEdit(child: ChildProfile) {
    editing.value = child;
    form.value = { nickname: child.nickname, pin: '', birthday: child.birthday ?? null };
    showModal.value = true;
  }

  async function handleSave() {
    await formRef.value?.validate();
    saving.value = true;
    try {
      const payload = {
        nickname: form.value.nickname,
        pin: form.value.pin || undefined,
        birthday: form.value.birthday || undefined,
      };
      if (editing.value) {
        await updateChild(editing.value.id, payload);
      } else {
        await createChild(payload);
      }
      showModal.value = false;
      message.success('保存成功');
      await load();
    } catch {
      // 失败提示由 http 层全局处理
    } finally {
      saving.value = false;
    }
  }

  async function handleDelete(id: ChildProfile['id']) {
    try {
      await deleteChild(id);
      message.success('删除成功');
      await load();
    } catch {
      // 失败提示由 http 层全局处理
    }
  }

  onMounted(load);
</script>

<style lang="less" scoped>
  .page-spin {
    display: block;
    margin: 80px auto;
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
</style>
