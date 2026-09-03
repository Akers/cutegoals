<template>
  <div class="parent-workbench">
    <n-card :bordered="false" title="家长端工作台占位">
      <n-empty description="业务页面将在后续批次中实现（TODO）。" />
    </n-card>
  </div>
</template>

<script lang="ts" setup>
  // 家长端工作台占位页：仅用于让布局/菜单在批次 1 中可渲染。
  // 后续批次将替换为真实业务页面。
</script>

<style lang="less" scoped>
  .parent-workbench {
    padding: 16px;
  }
</style>
