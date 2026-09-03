<template>
  <n-card :bordered="false" title="任务类型筛选" size="small">
    <n-checkbox-group :value="selected" @update:value="handleUpdate">
      <n-space>
        <n-checkbox
          v-for="opt in typeOptions"
          :key="opt.value"
          :value="opt.value"
          :label="opt.label"
        />
      </n-space>
    </n-checkbox-group>
  </n-card>
</template>

<script lang="ts" setup>
  import { NCard, NCheckbox, NCheckboxGroup, NSpace } from 'naive-ui';
  import type { ParentTaskType } from '../utils';

  defineProps<{
    selected: ParentTaskType[];
  }>();

  const emit = defineEmits<{
    (e: 'update:selected', value: ParentTaskType[]): void;
  }>();

  const typeOptions: { value: ParentTaskType; label: string }[] = [
    { value: 'LIMITED', label: '限时任务' },
    { value: 'REPEAT', label: '重复任务' },
    { value: 'STANDING', label: '常驻任务' },
  ];

  function handleUpdate(value: ParentTaskType[]) {
    emit('update:selected', value);
  }
</script>
