<template>
  <div class="auth-layout">
    <div class="auth-layout__panel">
      <div class="auth-layout__brand">{{ websiteConfig.title }}</div>
      <n-card class="auth-layout__card" :bordered="false">
        <div class="auth-layout__title">{{ title }}</div>
        <div class="auth-layout__subtitle">{{ subtitle }}</div>
        <slot></slot>
        <div class="auth-layout__footer">
          <slot name="footer">首次部署请使用初始化向导</slot>
        </div>
      </n-card>
    </div>
  </div>
</template>

<script lang="ts" setup>
  import { NCard } from 'naive-ui';
  import { websiteConfig } from '@/config/website.config';

  defineProps({
    title: { type: String, required: true },
    subtitle: { type: String, default: '' },
  });
</script>

<style lang="less" scoped>
  .auth-layout {
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 100vh;
    background: #f0f2f5;

    &__panel {
      display: flex;
      flex-direction: column;
      align-items: center;
      width: 400px;
      max-width: calc(100vw - 32px);
    }

    &__brand {
      margin-bottom: 24px;
      font-size: 22px;
      font-weight: 600;
      color: rgba(0, 0, 0, 0.85);
      text-align: center;
    }

    &__card {
      width: 100%;
      border-radius: 8px;
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
    }

    &__title {
      margin-bottom: 4px;
      font-size: 20px;
      font-weight: 600;
      text-align: center;
    }

    &__subtitle {
      margin-bottom: 20px;
      font-size: 14px;
      color: rgba(0, 0, 0, 0.45);
      text-align: center;
    }

    &__footer {
      margin-top: 16px;
      font-size: 13px;
      color: rgba(0, 0, 0, 0.45);
      text-align: center;
    }
  }
</style>
