<template>
  <AuthLayout title="初始化 CuteGoals" subtitle="创建首位管理员账号与家庭">
    <n-spin :show="checking">
      <n-form ref="formRef" :model="formInline" :rules="rules" label-placement="left" size="large">
        <n-form-item path="token">
          <n-input
            v-model:value="formInline.token"
            type="password"
            show-password-on="click"
            placeholder="从部署命令获取"
            @keyup.enter="handleSubmit"
          />
        </n-form-item>
        <n-form-item path="phone">
          <n-input
            v-model:value="formInline.phone"
            placeholder="11 位手机号"
            @keyup.enter="handleSubmit"
          />
        </n-form-item>
        <n-form-item path="password">
          <n-input
            v-model:value="formInline.password"
            type="password"
            show-password-on="click"
            placeholder="至少 8 位，需包含字母和数字"
            @keyup.enter="handleSubmit"
          />
        </n-form-item>
        <n-form-item path="confirmPassword">
          <n-input
            v-model:value="formInline.confirmPassword"
            type="password"
            show-password-on="click"
            placeholder="再次输入密码"
            @keyup.enter="handleSubmit"
          />
        </n-form-item>
        <div v-if="errorMsg" class="auth-error">{{ errorMsg }}</div>
        <n-button type="primary" size="large" :loading="loading" block @click="handleSubmit">
          完成初始化
        </n-button>
      </n-form>
    </n-spin>
    <template #footer>
      已完成初始化？前往
      <router-link to="/admin/login">管理员登录</router-link>
    </template>
  </AuthLayout>
</template>

<script lang="ts" setup>
  import { onMounted, reactive, ref } from 'vue';
  import { useRouter } from 'vue-router';
  import { FormInst, FormItemRule } from 'naive-ui';
  import AuthLayout from './components/AuthLayout.vue';
  import { getInstanceStatus } from '@/api/admin';
  import { useUser } from '@/store/modules/user';
  import { setInstanceInitialized } from '@/router/guards';
  import { PageEnum } from '@/enums/pageEnum';

  const formRef = ref<FormInst | null>(null);
  const loading = ref(false);
  const checking = ref(false);
  const errorMsg = ref('');
  const router = useRouter();
  const userStore = useUser();

  const formInline = reactive({
    token: '',
    phone: '',
    password: '',
    confirmPassword: '',
  });

  const rules: Record<string, FormItemRule[]> = {
    token: [{ required: true, message: '请输入初始化令牌', trigger: 'blur' }],
    phone: [
      { required: true, message: '请输入手机号', trigger: 'blur' },
      { pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' },
    ],
    password: [
      { required: true, message: '请输入密码', trigger: 'blur' },
      { min: 8, message: '密码至少 8 位', trigger: 'blur' },
      {
        validator: (_rule: FormItemRule, value: string) =>
          /[A-Za-z]/.test(value) && /\d/.test(value),
        message: '密码需同时包含字母和数字',
        trigger: 'blur',
      },
    ],
    confirmPassword: [
      { required: true, message: '请再次输入密码', trigger: 'blur' },
      {
        validator: (_rule: FormItemRule, value: string) => value === formInline.password,
        message: '两次输入的密码不一致',
        trigger: 'blur',
      },
    ],
  };

  // 实例已初始化时无需初始化，直接去管理员登录页（与旧版 AdminInitPage 行为一致）
  onMounted(async () => {
    checking.value = true;
    try {
      const status = await getInstanceStatus();
      const initialized = status?.instanceStatus === 'INITIALIZED' || status?.initialized === true;
      if (initialized) {
        setInstanceInitialized(true);
        router.replace(PageEnum.ADMIN_LOGIN);
      }
    } catch {
      // 状态查询失败时停留在初始化页，提交时再提示具体错误
    } finally {
      checking.value = false;
    }
  });

  async function handleSubmit(e?: Event) {
    e?.preventDefault();
    errorMsg.value = '';
    formRef.value?.validate(async (errors) => {
      if (errors) return;
      loading.value = true;
      try {
        await userStore.adminInit({
          token: formInline.token,
          phone: formInline.phone,
          password: formInline.password,
        });
        setInstanceInitialized(true);
        router.replace(PageEnum.ADMIN_HOME);
      } catch (err) {
        errorMsg.value = (err as Error)?.message || '初始化失败';
      } finally {
        loading.value = false;
      }
    });
  }
</script>

<style lang="less" scoped>
  .auth-error {
    margin-bottom: 12px;
    font-size: 13px;
    color: #d03050;
    text-align: center;
  }
</style>
