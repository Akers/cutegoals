<template>
  <div>
    <n-spin v-if="loading" class="page-spin" />
    <n-result v-else-if="error" status="error" title="加载失败" description="请求失败，请稍后重试">
      <template #footer>
        <n-button @click="loadAll">重试</n-button>
      </template>
    </n-result>
    <n-empty v-else-if="!family" description="暂无数据" class="page-empty" />
    <n-space v-else vertical :size="24">
      <div class="page-header">
        <h3 class="page-title">{{ family.name }}</h3>
        <n-space>
          <n-button @click="openEditName">编辑家庭名称</n-button>
          <n-button @click="openInvite">邀请家长</n-button>
          <n-button @click="openNewChild">添加孩子</n-button>
        </n-space>
      </div>

      <!-- 家庭成员 -->
      <n-card title="家庭成员">
        <n-space vertical :size="8">
          <div v-for="member in family.members ?? []" :key="member.id" class="row">
            <div class="row-main">
              <div class="row-title">
                {{ member.nickname ?? maskPhone(member.phone ?? '') }}
              </div>
              <div v-if="member.phone" class="row-sub">{{ maskPhone(member.phone) }}</div>
            </div>
            <n-space :size="8" align="center">
              <n-tag>{{ statusLabel(member.role === 'PARENT' ? 'approved' : 'pending') }}</n-tag>
              <n-popconfirm v-if="isSelf(member)" @positive-click="handleLeave">
                <template #trigger>
                  <n-button size="small" type="error" ghost :loading="actionLoading">
                    退出家庭
                  </n-button>
                </template>
                退出后你将无法管理该家庭，是否继续？
              </n-popconfirm>
              <n-popconfirm
                v-else-if="member.role === 'PARENT'"
                @positive-click="handleRemove(member)"
              >
                <template #trigger>
                  <n-button size="small" type="error" ghost :loading="actionLoading">
                    移除
                  </n-button>
                </template>
                移除后该家长将无法管理此家庭，是否继续？
              </n-popconfirm>
            </n-space>
          </div>
        </n-space>
      </n-card>

      <!-- 孩子 -->
      <n-card title="孩子">
        <n-text v-if="(family.children ?? []).length === 0" depth="3">
          暂无孩子，点击上方「添加孩子」创建档案。
        </n-text>
        <n-space v-else vertical :size="8">
          <div v-for="child in family.children ?? []" :key="child.id" class="row">
            <div class="row-main">
              <div class="row-title">{{ child.nickname }}</div>
              <div v-if="child.birthday" class="row-sub">生日 {{ child.birthday }}</div>
            </div>
            <n-popconfirm @positive-click="handleRemoveChild(child)">
              <template #trigger>
                <n-button size="small" type="error" ghost :loading="actionLoading">移除</n-button>
              </template>
              移除后该孩子将无法继续使用家庭功能，是否继续？
            </n-popconfirm>
          </div>
        </n-space>
      </n-card>

      <!-- 待处理邀请 -->
      <n-card title="待处理邀请">
        <n-text v-if="invitations.length === 0" depth="3">暂无邀请</n-text>
        <n-space v-else vertical :size="8">
          <div v-for="inv in invitations" :key="inv.id" class="row">
            <div class="row-main">
              <div class="row-title">{{ maskPhone(inv.inviteePhone) }}</div>
              <div class="row-sub">{{ inv.createdAt }}</div>
            </div>
            <n-tag>{{ statusLabel(inv.status.toLowerCase()) }}</n-tag>
          </div>
        </n-space>
      </n-card>

      <!-- 邀请家长 -->
      <n-modal v-model:show="showInvite" preset="card" title="邀请家长" style="width: 420px">
        <n-form ref="inviteFormRef" :model="inviteForm" :rules="inviteRules" label-placement="top">
          <n-form-item label="被邀请人手机号" path="inviteePhone">
            <n-input
              v-model:value="inviteForm.inviteePhone"
              placeholder="11 位手机号"
              :maxlength="11"
            />
          </n-form-item>
        </n-form>
        <template #footer>
          <n-button type="primary" block :loading="sending" @click="handleInvite">
            发送邀请
          </n-button>
        </template>
      </n-modal>

      <!-- 添加孩子 -->
      <n-modal v-model:show="showChildModal" preset="card" title="添加孩子" style="width: 420px">
        <n-form ref="childFormRef" :model="childForm" :rules="childRules" label-placement="top">
          <n-form-item label="昵称" path="nickname">
            <n-input v-model:value="childForm.nickname" />
          </n-form-item>
          <n-form-item label="PIN" path="pin">
            <n-input v-model:value="childForm.pin" type="password" show-password-on="click" />
          </n-form-item>
          <n-form-item label="生日" path="birthday">
            <n-date-picker
              v-model:formatted-value="childForm.birthday"
              type="date"
              value-format="yyyy-MM-dd"
              clearable
              style="width: 100%"
            />
          </n-form-item>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="showChildModal = false">取消</n-button>
            <n-button type="primary" :loading="childSaving" @click="handleSaveChild">
              保存
            </n-button>
          </n-space>
        </template>
      </n-modal>

      <!-- 编辑家庭名称 -->
      <n-modal
        v-model:show="showEditNameModal"
        preset="card"
        title="编辑家庭名称"
        style="width: 420px"
      >
        <n-form :model="nameForm" label-placement="top">
          <n-form-item
            label="家庭名称"
            path="name"
            :rule="{ required: true, message: '请输入家庭名称' }"
          >
            <n-input v-model:value="nameForm.name" />
          </n-form-item>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="showEditNameModal = false">取消</n-button>
            <n-button type="primary" :loading="nameSaving" @click="handleEditName">保存</n-button>
          </n-space>
        </template>
      </n-modal>
    </n-space>
  </div>
</template>

<script lang="ts" setup>
  import { onMounted, ref } from 'vue';
  import type { FormInst, FormRules } from 'naive-ui';
  import {
    NButton,
    NCard,
    NDatePicker,
    NEmpty,
    NForm,
    NFormItem,
    NInput,
    NModal,
    NPopconfirm,
    NResult,
    NSpace,
    NSpin,
    NTag,
    NText,
    useMessage,
  } from 'naive-ui';
  import type { ChildProfile, Family, FamilyMember, Invitation } from '@/types/api';
  import {
    createInvitation,
    deleteMember,
    getFamily,
    leaveFamily,
    listInvitations,
    updateFamily,
  } from '@/api/family';
  import { createChild, deleteChild } from '@/api/children';
  import { useUser } from '@/store/modules/user';
  import { maskPhone, statusLabel } from '../utils';

  defineOptions({ name: 'ParentFamily' });

  const message = useMessage();
  const userStore = useUser();

  const loading = ref(false);
  const error = ref(false);
  const family = ref<Family | null>(null);
  const invitations = ref<Invitation[]>([]);
  const actionLoading = ref(false);

  async function loadAll() {
    loading.value = true;
    error.value = false;
    try {
      const [familyData, invitationPage] = await Promise.all([
        getFamily(),
        listInvitations({ page: 1, pageSize: 50 }),
      ]);
      family.value = familyData;
      invitations.value = invitationPage.content ?? [];
    } catch {
      error.value = true;
    } finally {
      loading.value = false;
    }
  }

  async function refetchFamily() {
    family.value = await getFamily();
  }

  function isSelf(member: FamilyMember): boolean {
    const accountId = userStore.info?.accountId;
    return accountId != null && member.accountId === Number(accountId);
  }

  // ---- 邀请家长 ----
  const showInvite = ref(false);
  const sending = ref(false);
  const inviteFormRef = ref<FormInst | null>(null);
  const inviteForm = ref({ inviteePhone: '' });
  const inviteRules: FormRules = {
    inviteePhone: [
      { required: true, message: '请输入手机号' },
      { pattern: /^1\d{10}$/, message: '请输入 11 位手机号' },
    ],
  };

  function openInvite() {
    inviteForm.value.inviteePhone = '';
    showInvite.value = true;
  }

  async function handleInvite() {
    await inviteFormRef.value?.validate();
    sending.value = true;
    try {
      await createInvitation({ inviteePhone: inviteForm.value.inviteePhone });
      showInvite.value = false;
      await loadAll();
    } catch {
      // 失败提示由 http 层全局处理
    } finally {
      sending.value = false;
    }
  }

  // ---- 添加孩子 ----
  const showChildModal = ref(false);
  const childSaving = ref(false);
  const childFormRef = ref<FormInst | null>(null);
  const childForm = ref({ nickname: '', pin: '', birthday: null as string | null });
  const childRules: FormRules = {
    nickname: [{ required: true, message: '请输入昵称' }],
  };

  function openNewChild() {
    childForm.value = { nickname: '', pin: '', birthday: null };
    showChildModal.value = true;
  }

  async function handleSaveChild() {
    await childFormRef.value?.validate();
    childSaving.value = true;
    try {
      await createChild({
        nickname: childForm.value.nickname,
        pin: childForm.value.pin || undefined,
        birthday: childForm.value.birthday || undefined,
      });
      // 刷新家庭概览即可同步成员与孩子（单一数据源）。
      await refetchFamily();
      showChildModal.value = false;
      message.success('保存成功');
    } catch {
      // 失败提示由 http 层全局处理
    } finally {
      childSaving.value = false;
    }
  }

  async function handleRemoveChild(child: ChildProfile) {
    actionLoading.value = true;
    try {
      await deleteChild(child.id);
      await refetchFamily();
    } catch {
      // 失败提示由 http 层全局处理
    } finally {
      actionLoading.value = false;
    }
  }

  // ---- 编辑家庭名称 ----
  const showEditNameModal = ref(false);
  const nameSaving = ref(false);
  const nameForm = ref({ name: '' });

  function openEditName() {
    nameForm.value.name = family.value?.name ?? '';
    showEditNameModal.value = true;
  }

  async function handleEditName() {
    const name = nameForm.value.name.trim();
    if (!name) return;
    nameSaving.value = true;
    try {
      await updateFamily({ name });
      message.success('家庭名称已更新');
      showEditNameModal.value = false;
      await refetchFamily();
    } catch {
      // 失败提示由 http 层全局处理
    } finally {
      nameSaving.value = false;
    }
  }

  // ---- 移除成员 / 退出家庭 ----
  async function handleRemove(member: FamilyMember) {
    actionLoading.value = true;
    try {
      await deleteMember(member.id);
      await refetchFamily();
    } catch {
      // 失败提示由 http 层全局处理
    } finally {
      actionLoading.value = false;
    }
  }

  async function handleLeave() {
    actionLoading.value = true;
    try {
      await leaveFamily();
      await refetchFamily();
    } catch {
      // 失败提示由 http 层全局处理
    } finally {
      actionLoading.value = false;
    }
  }

  onMounted(loadAll);
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

  .row {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .row-main {
      .row-title {
        font-weight: 600;
      }

      .row-sub {
        font-size: 12px;
        color: var(--n-text-color-disabled, #999);
      }
    }
  }
</style>
