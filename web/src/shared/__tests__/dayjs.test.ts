import { describe, it, expect } from 'vitest';
import dayjs from 'dayjs';

// 载入全局 side-effect 注册：shared/dayjs.ts 负责 extend 所有 antd DatePicker
// 所依赖的插件（weekday / weekOfYear / customParseFormat / localizedFormat /
// advancedFormat）。该 import 必须早于 antd DatePicker 渲染；真实入口在
// `src/app.tsx` 顶部，这里在测试中显式触发以验证注册逻辑。
import '../dayjs';

describe('shared/dayjs global plugins', () => {
  it('weekday 插件可用（antd DatePicker 内部依赖）', () => {
    expect(() => dayjs().weekday(0)).not.toThrow();
    // weekday(n) 必须返回合法 dayjs 实例，且能往返格式化
    const today = dayjs('2026-07-22');
    const shifted = today.weekday(0);
    expect(shifted.isValid()).toBe(true);
    // 周日=0, 周一=1, ..., 周六=6（dayjs 默认 en locale）
    expect(shifted.day()).toBe(0);
  });

  it('weekOfYear 插件可用', () => {
    expect(() => dayjs().week()).not.toThrow();
    expect(typeof dayjs().week()).toBe('number');
  });

  it('customParseFormat 插件可用', () => {
    const parsed = dayjs('2026-07-22', 'YYYY-MM-DD');
    expect(parsed.isValid()).toBe(true);
    expect(parsed.format('YYYY-MM-DD')).toBe('2026-07-22');
  });
});
