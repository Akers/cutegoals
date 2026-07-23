# Design: 修复 antd DatePicker dayjs localeData 插件未注册

## 修复方案

在 `web/src/shared/dayjs.ts` 追加两个 `dayjs.extend(...)` 注册，使其与 rc-picker 官方 6 插件集完全对齐：

```ts
import localeData from 'dayjs/plugin/localeData';
import weekYear from 'dayjs/plugin/weekYear';

dayjs.extend(localeData);
dayjs.extend(weekYear);
```

不修改模块的 side-effect 加载方式（仍由 `web/src/app.tsx` 顶部 `import '@/shared/dayjs'` 触发，且早于 antd 任何 import）。

## 为什么是这两个插件

- **`localeData`**：rc-picker 在 `getWeekDay`、`locale.getShortWeekDays`、`locale.getShortMonths`、`locale.getWeekFirstDay` 等位置调用 `.localeData().firstDayOfWeek()` / `.weekdaysMin()` / `.monthsShort()`。这是本次报错的直接原因。
- **`weekYear`**：rc-picker 在 `locale.parse` 中处理 `YYYY-wo` 格式（`picker="week"`、用户键入 week 字符串解析）时需要 `weekYear` 提供的 `weekYear()` 方法与 `advancedFormat` 配合的 `wo` token。当前未触发，但属于同一官方 6 插件集，缺失即为潜在回归点。

## 备选方案对比

| 方案 | 取舍 |
|---|---|
| **A. 只补 `localeData`**（最小修） | 修当前 bug，但 `weekYear` 仍缺，下次用户进入 week picker 或自定义 `YYYY-wo` 解析时再次崩溃 |
| **B. 补 `localeData` + `weekYear`**（推荐） | 对齐 rc-picker 官方 6 插件集，彻底消除该类崩溃；改动仍为单文件 + 测试 |
| C. 全量注册 dayjs 所有插件 | 引入大量未使用插件，污染 prototype，增加维护面 |

选 **B**：在保持 hotfix 最小范围的同时，一次补齐 rc-picker 官方完整插件集，避免短期内第三次同模式崩溃。

## 验证策略

1. **RED**：在 `web/src/shared/__tests__/dayjs.test.ts` 追加断言（先确认当前失败）：
   - `dayjs().localeData().firstDayOfWeek()` 不抛错且返回数字
   - `dayjs().localeData().weekdaysMin()` 返回长度 7 的数组
   - `dayjs().localeData().monthsShort()` 返回长度 12 的数组
   - `dayjs('2026-01-15').weekYear()` 不抛错且返回数字
2. **GREEN**：在 `shared/dayjs.ts` 追加两个 `extend`，测试转绿。
3. **回归**：跑 `pnpm --filter web test` 与 `pnpm --filter web build`，确认 173+ 测试与构建均通过。

## 风险

- 改动是上轮 hotfix 的同模式延伸，根因明确（librarian 给出 rc-picker 源码引用）。
- dayjs 单实例已验证，无多 bundle 实例风险。
- `weekYear` 插件提供 `.weekYear()` 方法，与已注册的 `weekOfYear` 协同；不会与现有方法冲突。
