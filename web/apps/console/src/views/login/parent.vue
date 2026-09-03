<template>
  <AuthLayout title="家长登录" subtitle="使用手机号与密码登录">
    <n-form ref="formRef" :model="formInline" :rules="rules" label-placement="left" size="large">
      <n-form-item path="phone">
        <n-input
          v-model:value="formInline.phone"
          placeholder="11 位手机号"
          @keyup.enter="handleSubmit"
        >
          <template #prefix>
            <n-icon size="18" color="#808695">
              <PersonOutline />
            </n-icon>
          </template>
        </n-input>
      </n-form-item>
      <n-form-item path="password">
        <n-input
          v-model:value="formInline.password"
          type="password"
          show-password-on="click"
          placeholder="请输入密码"
          @keyup.enter="handleSubmit"
        >
          <template #prefix>
            <n-icon size="18" color="#808695">
              <LockClosedOutline />
            </n-icon>
          </template>
        </n-input>
      </n-form-item>
      <div v-if="errorMsg" class="auth-error">{{ errorMsg }}</div>
      <n-button type="primary" size="large" :loading="loading" block @click="handleSubmit">
        登录
      </n-button>
    </n-form>
    <template #footer>
      首次部署请使用
      <router-link to="/admin/init">初始化向导</router-link>
    </template>
  </AuthLayout>
</template>

<script lang="ts" setup>
  import { reactive, ref } from 'vue';
  import { useRouter } from 'vue-router';
  import { FormInst, FormItemRule } from 'naive-ui';
  import { PersonOutline, LockClosedOutline } from '@vicons/ionicons5';
  import AuthLayout from './components/AuthLayout.vue';
  import { useUser } from '@/store/modules/user';
  import { PageEnum } from '@/enums/pageEnum';

  const formRef = ref<FormInst | null>(null);
  const loading = ref(false);
  const errorMsg = ref('');
  const router = useRouter();
  const userStore = useUser();

  const formInline = reactive({
    phone: '',
    password: '',
  });

  const rules: Record<string, FormItemRule[]> = {
    phone: [
      { required: true, message: '请输入手机号', trigger: 'blur' },
      { pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' },
    ],
    password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  };

  async function handleSubmit(e?: Event) {
    e?.preventDefault();
    errorMsg.value = '';
    formRef.value?.validate(async (errors) => {
      if (errors) return;
      loading.value = true;
      try {
        await userStore.loginParent({
          phone: formInline.phone,
          password: formInline.password,
        });
        router.replace(PageEnum.BASE_HOME);
      } catch (err) {
        errorMsg.value = (err as Error)?.message || '手机号或密码错误';
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
