<template>
  <n-space vertical :size="12">
    <!-- 奖品类型 -->
    <n-form-item label="奖品类型" path="prizeType">
      <n-radio-group :value="value.prizeType" @update:value="handlePrizeTypeChange">
        <n-space>
          <n-radio value="VIRTUAL">虚拟奖品</n-radio>
          <n-radio value="PHYSICAL">实物奖品</n-radio>
        </n-space>
      </n-radio-group>
    </n-form-item>

    <!-- 图片上传 -->
    <n-form-item label="标题图" path="titleImage">
      <div class="image-upload-field">
        <n-upload accept="image/*" :show-file-list="false" :custom-request="makeUploader('title')">
          <n-button size="small">上传图片</n-button>
        </n-upload>
        <img
          v-if="value.titleImage"
          :src="value.titleImage"
          alt="标题图预览"
          class="image-preview"
        />
      </div>
    </n-form-item>
    <n-form-item label="详情图" path="detailImage">
      <div class="image-upload-field">
        <n-upload accept="image/*" :show-file-list="false" :custom-request="makeUploader('detail')">
          <n-button size="small">上传图片</n-button>
        </n-upload>
        <img
          v-if="value.detailImage"
          :src="value.detailImage"
          alt="详情图预览"
          class="image-preview"
        />
      </div>
    </n-form-item>

    <!-- 有效期 -->
    <n-form-item label="有效期" path="validFrom">
      <n-space align="center">
        <n-date-picker
          :value="validFromTs"
          type="date"
          clearable
          placeholder="开始日期"
          @update:formatted-value="handleValidFromChange"
        />
        <span>至</span>
        <n-date-picker
          :value="validToTs"
          type="date"
          clearable
          placeholder="结束日期"
          @update:formatted-value="handleValidToChange"
        />
      </n-space>
    </n-form-item>

    <!-- 虚拟奖品：分类 + 分类配置 -->
    <template v-if="value.prizeType === 'VIRTUAL'">
      <n-form-item label="奖品分类" path="prizeCategory">
        <n-select
          :value="value.prizeCategory"
          :options="categoryOptions"
          placeholder="请选择分类"
          style="width: 100%"
          clearable
          @update:value="handleCategoryChange"
        />
      </n-form-item>
      <template v-if="value.prizeCategory === 'TV_TIME' || value.prizeCategory === 'COMPUTER_TIME'">
        <n-form-item label="时长类型" path="durationType">
          <n-select
            :value="durationTypeValue"
            :options="durationTypeOptions"
            style="width: 100%"
            clearable
            @update:value="(v: string | null) => emitConfig({ ...cfg, durationType: v ?? undefined })"
          />
        </n-form-item>
        <n-form-item label="时长" path="duration">
          <n-input-number
            :value="(cfg.duration as number) ?? 0"
            :min="0"
            style="width: 100%"
            @update:value="(v: number | null) => emitConfig({ ...cfg, duration: v ?? 0 })"
          />
        </n-form-item>
      </template>
      <n-form-item
        v-if="value.prizeCategory === 'PARK_PLAY' || value.prizeCategory === 'GENERAL'"
        label="可用次数"
        path="availableTimes"
      >
        <n-input-number
          :value="(cfg.availableTimes as number) ?? 0"
          :min="0"
          style="width: 100%"
          @update:value="(v: number | null) => emitConfig({ ...cfg, availableTimes: v ?? 0 })"
        />
      </n-form-item>
      <template v-if="value.prizeCategory === 'TRAVEL'">
        <n-form-item label="目的地" path="destination">
          <n-input
            :value="(cfg.destination as string) ?? ''"
            @update:value="(v: string) => emitConfig({ ...cfg, destination: v })"
          />
        </n-form-item>
        <n-form-item label="旅行天数" path="travelDays">
          <n-input-number
            :value="(cfg.travelDays as number) ?? 0"
            :min="0"
            style="width: 100%"
            @update:value="(v: number | null) => emitConfig({ ...cfg, travelDays: v ?? 0 })"
          />
        </n-form-item>
        <n-form-item label="旅行夜数" path="travelNights">
          <n-input-number
            :value="(cfg.travelNights as number) ?? 0"
            :min="0"
            style="width: 100%"
            @update:value="(v: number | null) => emitConfig({ ...cfg, travelNights: v ?? 0 })"
          />
        </n-form-item>
        <n-form-item label="实际价值" path="actualValue">
          <n-input-number
            :value="(cfg.actualValue as number) ?? 0"
            :min="0"
            style="width: 100%"
            @update:value="(v: number | null) => emitConfig({ ...cfg, actualValue: v ?? 0 })"
          />
        </n-form-item>
      </template>
    </template>

    <!-- 实物奖品：配置 -->
    <n-form-item v-if="value.prizeType === 'PHYSICAL'" label="实际价值" path="actualValue">
      <n-input-number
        :value="(cfg.actualValue as number) ?? 0"
        :min="0"
        style="width: 100%"
        @update:value="(v: number | null) => emitConfig({ actualValue: v ?? 0 })"
      />
    </n-form-item>
  </n-space>
</template>

<script lang="ts" setup>
  import { computed } from 'vue';
  import {
    NButton,
    NDatePicker,
    NFormItem,
    NInput,
    NInputNumber,
    NRadio,
    NRadioGroup,
    NSelect,
    NSpace,
    NUpload,
  } from 'naive-ui';
  import { readTypeConfig } from '../utils';

  /** 奖品类型配置表单值（typeConfig 保持 JSON 字符串，与后端 wire 格式一致） */
  export interface PrizeTypeConfigFormValue {
    prizeType: 'VIRTUAL' | 'PHYSICAL';
    prizeCategory?: string;
    titleImage?: string;
    detailImage?: string;
    validFrom?: string;
    validTo?: string;
    typeConfig?: string;
  }

  const props = defineProps<{
    value: PrizeTypeConfigFormValue;
    onUpload: (file: File) => Promise<string>;
  }>();

  const emit = defineEmits<{
    (e: 'update:value', value: PrizeTypeConfigFormValue): void;
  }>();

  const message = useMessage();

  const categoryOptions = [
    { value: 'TV_TIME', label: '电视时长卡' },
    { value: 'COMPUTER_TIME', label: '电脑时长卡' },
    { value: 'PARK_PLAY', label: '公园游玩卡' },
    { value: 'GENERAL', label: '通用' },
    { value: 'TRAVEL', label: '旅游卡' },
  ];

  const durationTypeOptions = [
    { value: 'DAILY', label: '日卡' },
    { value: 'WEEKLY', label: '周卡' },
    { value: 'MONTHLY', label: '月卡' },
    { value: 'SUPPLEMENT', label: '补签卡' },
  ];

  /** 解析后的类型配置对象（读取用），写回时重新序列化为 JSON 字符串 */
  const cfg = computed<Record<string, unknown>>(() => readTypeConfig(props.value.typeConfig));

  /** 时长类型当前值（模板内不做 TS 断言，避免被误判为过滤器） */
  const durationTypeValue = computed(() =>
    typeof cfg.value.durationType === 'string' ? cfg.value.durationType : undefined
  );

  function emitConfig(patch: Record<string, unknown>) {
    emit('update:value', { ...props.value, typeConfig: JSON.stringify(patch) });
  }

  function change(partial: Partial<PrizeTypeConfigFormValue>) {
    emit('update:value', { ...props.value, ...partial });
  }

  // ---- 类型/分类切换（切换时清空分类与类型配置，与旧版一致） ----
  function handlePrizeTypeChange(v: 'VIRTUAL' | 'PHYSICAL') {
    change({ prizeType: v, prizeCategory: undefined, typeConfig: undefined });
  }

  function handleCategoryChange(v: string | null) {
    change({ prizeCategory: v ?? undefined, typeConfig: undefined });
  }

  // ---- 图片上传 ----
  function makeUploader(kind: 'title' | 'detail') {
    return ({ file }: { file: unknown }) => {
      const raw = (file as { file?: File })?.file;
      if (!raw) return;
      props
        .onUpload(raw)
        .then((url) => {
          if (kind === 'title') change({ titleImage: url });
          else change({ detailImage: url });
        })
        .catch(() => {
          // 失败已由 http 层全局提示，这里静默处理（与旧版一致）
        });
    };
  }

  // ---- 有效期（存 ISO 字符串，与旧版 toISOString 一致） ----
  const validFromTs = computed(() =>
    props.value.validFrom ? Date.parse(props.value.validFrom) || null : null
  );
  const validToTs = computed(() =>
    props.value.validTo ? Date.parse(props.value.validTo) || null : null
  );

  function isoOf(formatted: string | null): string | undefined {
    return formatted ? new Date(formatted + 'T00:00:00').toISOString() : undefined;
  }

  function handleValidFromChange(formatted: string | null) {
    change({ validFrom: isoOf(formatted) });
  }

  function handleValidToChange(formatted: string | null) {
    change({ validTo: isoOf(formatted) });
  }
</script>

<style lang="less" scoped>
  .image-upload-field {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .image-preview {
    max-width: 200px;
    display: block;
    border-radius: 4px;
  }
</style>
