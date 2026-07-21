import { useCallback, useMemo } from 'react';
import { Input, InputNumber, Radio, Select, DatePicker, Space, Typography } from 'antd';
import dayjs from 'dayjs';
import { FormField } from '@shared/components';

// ---- 类型定义 ----

type PrizeType = 'VIRTUAL' | 'PHYSICAL';
type PrizeCategory = 'TV_TIME' | 'COMPUTER_TIME' | 'PARK_PLAY' | 'GENERAL' | 'TRAVEL';

export interface PrizeTypeConfig {
  prizeType: PrizeType;
  prizeCategory?: PrizeCategory;
  titleImage?: string;
  detailImage?: string;
  validFrom?: string;
  validTo?: string;
  typeConfig?: string;
}

export interface PrizeTypeConfigFormsProps {
  value: PrizeTypeConfig;
  onChange: (v: PrizeTypeConfig) => void;
  onUpload: (file: File) => Promise<string>;
}

// ---- 常量 ----

const PRIZE_CATEGORY_OPTIONS: { value: PrizeCategory; label: string }[] = [
  { value: 'TV_TIME', label: '电视时长卡' },
  { value: 'COMPUTER_TIME', label: '电脑时长卡' },
  { value: 'PARK_PLAY', label: '公园游玩卡' },
  { value: 'GENERAL', label: '通用' },
  { value: 'TRAVEL', label: '旅游卡' },
];

type DurationType = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'SUPPLEMENT';

const DURATION_TYPE_OPTIONS: { value: DurationType; label: string }[] = [
  { value: 'DAILY', label: '日卡' },
  { value: 'WEEKLY', label: '周卡' },
  { value: 'MONTHLY', label: '月卡' },
  { value: 'SUPPLEMENT', label: '补签卡' },
];

// ---- VirtualConfigForm ----

interface VirtualConfigFormProps {
  category: PrizeCategory;
  typeConfig?: string;
  onChange: (typeConfig: string) => void;
}

function VirtualConfigForm({ category, typeConfig, onChange }: VirtualConfigFormProps) {
  const config = useMemo<Record<string, unknown>>(() => {
    try {
      return typeConfig ? JSON.parse(typeConfig) : {};
    } catch {
      return {};
    }
  }, [typeConfig]);

  const handleDurationTypeChange = useCallback(
    (v: string) => {
      onChange(JSON.stringify({ ...config, durationType: v }));
    },
    [config, onChange],
  );

  const handleDurationChange = useCallback(
    (v: number | null) => {
      onChange(JSON.stringify({ ...config, duration: v ?? 0 }));
    },
    [config, onChange],
  );

  const handleAvailableTimesChange = useCallback(
    (v: number | null) => {
      onChange(JSON.stringify({ ...config, availableTimes: v ?? 0 }));
    },
    [config, onChange],
  );

  const handleDestinationChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      onChange(JSON.stringify({ ...config, destination: e.target.value }));
    },
    [config, onChange],
  );

  const handleTravelDaysChange = useCallback(
    (v: number | null) => {
      onChange(JSON.stringify({ ...config, travelDays: v ?? 0 }));
    },
    [config, onChange],
  );

  const handleTravelNightsChange = useCallback(
    (v: number | null) => {
      onChange(JSON.stringify({ ...config, travelNights: v ?? 0 }));
    },
    [config, onChange],
  );

  const handleActualValueChange = useCallback(
    (v: number | null) => {
      onChange(JSON.stringify({ ...config, actualValue: v ?? 0 }));
    },
    [config, onChange],
  );

  if (category === 'TV_TIME' || category === 'COMPUTER_TIME') {
    return (
      <Space direction="vertical" size="small" style={{ width: '100%' }}>
        <FormField label="时长类型" htmlFor="prize-duration-type">
          <Select
            id="prize-duration-type"
            value={(config.durationType as string) ?? undefined}
            onChange={handleDurationTypeChange}
            style={{ width: '100%' }}
          >
            {DURATION_TYPE_OPTIONS.map((opt) => (
              <Select.Option key={opt.value} value={opt.value}>
                {opt.label}
              </Select.Option>
            ))}
          </Select>
        </FormField>
        <FormField label="时长" htmlFor="prize-duration">
          <InputNumber
            id="prize-duration"
            value={(config.duration as number) ?? 0}
            onChange={handleDurationChange}
            min={0}
            style={{ width: '100%' }}
          />
        </FormField>
      </Space>
    );
  }

  if (category === 'PARK_PLAY' || category === 'GENERAL') {
    return (
      <FormField label="可用次数" htmlFor="prize-available-times">
        <InputNumber
          id="prize-available-times"
          value={(config.availableTimes as number) ?? 0}
          onChange={handleAvailableTimesChange}
          min={0}
          style={{ width: '100%' }}
        />
      </FormField>
    );
  }

  if (category === 'TRAVEL') {
    return (
      <Space direction="vertical" size="small" style={{ width: '100%' }}>
        <FormField label="目的地" htmlFor="prize-destination">
          <Input
            id="prize-destination"
            value={(config.destination as string) ?? ''}
            onChange={handleDestinationChange}
          />
        </FormField>
        <FormField label="旅行天数" htmlFor="prize-travel-days">
          <InputNumber
            id="prize-travel-days"
            value={(config.travelDays as number) ?? 0}
            onChange={handleTravelDaysChange}
            min={0}
            style={{ width: '100%' }}
          />
        </FormField>
        <FormField label="旅行夜数" htmlFor="prize-travel-nights">
          <InputNumber
            id="prize-travel-nights"
            value={(config.travelNights as number) ?? 0}
            onChange={handleTravelNightsChange}
            min={0}
            style={{ width: '100%' }}
          />
        </FormField>
        <FormField label="实际价值" htmlFor="prize-travel-actual-value">
          <InputNumber
            id="prize-travel-actual-value"
            value={(config.actualValue as number) ?? 0}
            onChange={handleActualValueChange}
            min={0}
            style={{ width: '100%' }}
          />
        </FormField>
      </Space>
    );
  }

  return null;
}

// ---- PhysicalConfigForm ----

interface PhysicalConfigFormProps {
  typeConfig?: string;
  onChange: (typeConfig: string) => void;
}

function PhysicalConfigForm({ typeConfig, onChange }: PhysicalConfigFormProps) {
  const config = useMemo<Record<string, unknown>>(() => {
    try {
      return typeConfig ? JSON.parse(typeConfig) : {};
    } catch {
      return {};
    }
  }, [typeConfig]);

  const handleChange = useCallback(
    (v: number | null) => {
      onChange(JSON.stringify({ actualValue: v ?? 0 }));
    },
    [onChange],
  );

  return (
    <FormField label="实际价值" htmlFor="prize-physical-value">
      <InputNumber
        id="prize-physical-value"
        value={(config.actualValue as number) ?? 0}
        onChange={handleChange}
        min={0}
        style={{ width: '100%' }}
      />
    </FormField>
  );
}

// ---- 图片上传组件 ----

interface ImageUploadFieldProps {
  label: string;
  id: string;
  value?: string;
  onUpload: (file: File) => Promise<string>;
  onUrlReady: (url: string) => void;
}

function ImageUploadField({ label, id, value, onUpload, onUrlReady }: ImageUploadFieldProps) {
  const handleFileChange = useCallback(
    async (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];
      if (!file) return;
      try {
        const url = await onUpload(file);
        onUrlReady(url);
      } catch {
        // 上传失败，静默处理
      }
    },
    [onUpload, onUrlReady],
  );

  return (
    <FormField label={label} htmlFor={id}>
      <input id={id} type="file" accept="image/*" onChange={handleFileChange} />
      {value && (
        <div style={{ marginTop: 8 }}>
          <img src={value} alt={`${label}预览`} style={{ maxWidth: 200, display: 'block' }} />
        </div>
      )}
    </FormField>
  );
}

// ---- 主组件 ----

/**
 * PrizeTypeConfigForms —— 奖品类型配置表单。
 *
 * 用法：
 * ```tsx
 * const [prizeConfig, setPrizeConfig] = useState<PrizeTypeConfig>({
 *   prizeType: 'VIRTUAL',
 * });
 *
 * <PrizeTypeConfigForms
 *   value={prizeConfig}
 *   onChange={setPrizeConfig}
 *   onUpload={async (file) => { ... }}
 * />
 * ```
 */
export function PrizeTypeConfigForms({
  value,
  onChange,
  onUpload,
}: PrizeTypeConfigFormsProps) {
  const handlePrizeTypeChange = useCallback(
    (e: any) => {
      const v = e.target.value as PrizeType;
      onChange({
        ...value,
        prizeType: v,
        // 切换奖品类型时清除分类和类型配置
        prizeCategory: undefined,
        typeConfig: undefined,
      });
    },
    [value, onChange],
  );

  const handleCategoryChange = useCallback(
    (v: PrizeCategory) => {
      onChange({
        ...value,
        prizeCategory: v,
        // 切换分类时清除类型配置
        typeConfig: undefined,
      });
    },
    [value, onChange],
  );

  const handleTitleImageReady = useCallback(
    (url: string) => {
      onChange({ ...value, titleImage: url });
    },
    [value, onChange],
  );

  const handleDetailImageReady = useCallback(
    (url: string) => {
      onChange({ ...value, detailImage: url });
    },
    [value, onChange],
  );

  const handleValidFromChange = useCallback(
    (date: dayjs.Dayjs | null) => {
      onChange({ ...value, validFrom: date ? date.toISOString() : undefined });
    },
    [value, onChange],
  );

  const handleValidToChange = useCallback(
    (date: dayjs.Dayjs | null) => {
      onChange({ ...value, validTo: date ? date.toISOString() : undefined });
    },
    [value, onChange],
  );

  const handleTypeConfigChange = useCallback(
    (typeConfig: string) => {
      onChange({ ...value, typeConfig });
    },
    [value, onChange],
  );

  return (
    <Space direction="vertical" size="small" style={{ width: '100%' }}>
      {/* 奖品类型选择 */}
      <FormField label="奖品类型" htmlFor="prize-type">
        <Radio.Group
          id="prize-type"
          value={value.prizeType}
          onChange={handlePrizeTypeChange}
        >
          <Radio value="VIRTUAL">虚拟奖品</Radio>
          <Radio value="PHYSICAL">实物奖品</Radio>
        </Radio.Group>
      </FormField>

      {/* 图片上传 */}
      <ImageUploadField
        label="标题图"
        id="prize-title-image"
        value={value.titleImage}
        onUpload={onUpload}
        onUrlReady={handleTitleImageReady}
      />
      <ImageUploadField
        label="详情图"
        id="prize-detail-image"
        value={value.detailImage}
        onUpload={onUpload}
        onUrlReady={handleDetailImageReady}
      />

      {/* 有效期 */}
      <Space>
        <FormField label="开始日期" htmlFor="prize-valid-from">
          <DatePicker
            id="prize-valid-from"
            value={value.validFrom ? dayjs(value.validFrom) : null}
            onChange={handleValidFromChange}
            format="YYYY-MM-DD"
          />
        </FormField>
        <Typography.Text style={{ lineHeight: '32px' }}>至</Typography.Text>
        <FormField label="结束日期" htmlFor="prize-valid-to">
          <DatePicker
            id="prize-valid-to"
            value={value.validTo ? dayjs(value.validTo) : null}
            onChange={handleValidToChange}
            format="YYYY-MM-DD"
          />
        </FormField>
      </Space>

      {/* 虚拟奖品：分类 + 分类配置 */}
      {value.prizeType === 'VIRTUAL' && (
        <>
          <FormField label="奖品分类" htmlFor="prize-category">
            <Select
              id="prize-category"
              value={value.prizeCategory}
              onChange={handleCategoryChange}
              style={{ width: '100%' }}
            >
              {PRIZE_CATEGORY_OPTIONS.map((opt) => (
                <Select.Option key={opt.value} value={opt.value}>
                  {opt.label}
                </Select.Option>
              ))}
            </Select>
          </FormField>
          {value.prizeCategory && (
            <VirtualConfigForm
              category={value.prizeCategory}
              typeConfig={value.typeConfig}
              onChange={handleTypeConfigChange}
            />
          )}
        </>
      )}

      {/* 实物奖品：配置 */}
      {value.prizeType === 'PHYSICAL' && (
        <PhysicalConfigForm
          typeConfig={value.typeConfig}
          onChange={handleTypeConfigChange}
        />
      )}
    </Space>
  );
}
