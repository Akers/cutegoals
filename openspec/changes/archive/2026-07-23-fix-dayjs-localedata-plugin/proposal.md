# Proposal: 修复 antd DatePicker dayjs localeData 插件未注册

## Why

家长端任务分配页面在上一轮 hotfix（`fix-dayjs-weekday-plugin`，归档于 `archive/2026-07-22-fix-dayjs-weekday-plugin`）修复了 `clone.weekday is not a function` 之后，再次崩溃并报：

```
Something went wrong.
clone.localeData is not a function
```

错误边界再次被触发，根因仍是 antd `DatePicker` / rc-picker 内部依赖的 dayjs 插件未被全局注册，只是这次缺的方法变成 `localeData`。

## 根因分析

`web/dist/index6.js`（构建产物）中 antd 依赖的 rc-picker 调用链：

```js
localeData().firstDayOfWeek()
localeData().monthsShort()
localeData().weekdaysMin()
localeData=function(){ ... }
```

即：rc-picker 内部通过 `generateConfig` 调用 dayjs 实例的 `.localeData()` 来获取一周首日、月份短名、星期短名等本地化数据。dayjs 默认不加载 `localeData` 插件，未 `dayjs.extend(localeData)` 时该方法不存在 → `TypeError: clone.localeData is not a function`。

**官方权威清单**（来源：`@rc-component/picker/src/generate/dayjs.ts`，antd 5.x DatePicker / TimePicker / RangePicker / Calendar 内部使用的 dayjs generateConfig）注册 6 个插件：

| 插件 | 当前状态 |
|---|---|
| weekday | ✅ 已注册（上一轮 hotfix 修复） |
| weekOfYear | ✅ 已注册 |
| advancedFormat | ✅ 已注册 |
| customParseFormat | ✅ 已注册 |
| **localeData** | ❌ **缺失（本次 bug 直接原因）** |
| **weekYear** | ❌ **缺失（rc-picker `YYYY-wo` 解析所需，潜在后续 bug）** |

dayjs 单实例已验证（上一轮 weekday 修复生效即说明 rc-picker 与业务侧共享同一 dayjs prototype），所以本次只需补齐缺失插件即可，无需解决多实例问题。

## What Changes

- 在 `web/src/shared/dayjs.ts` 的 side-effect 注册清单中追加 `localeData` 与 `weekYear` 两个插件，对齐 rc-picker 官方 6 插件集。
- 在 `web/src/shared/__tests__/dayjs.test.ts` 追加回归断言，覆盖 `.localeData().firstDayOfWeek()` / `.monthsShort()` / `.weekdaysMin()`，以及 `.weekYear()`，防止再次回退。

## 影响

- 改动 2 个文件（`shared/dayjs.ts` + `shared/__tests__/dayjs.test.ts`），均在 hotfix 范围内。
- 不涉及新 capability、public API、schema 变更、跨模块协调或深层架构问题。
- 不改变任何业务行为，仅补齐 antd 运行时所需的 dayjs 原型方法。

## 非目标

- 不引入其他非 rc-picker 必需的 dayjs 插件（如 `isoWeek` / `dayOfYear` / `quarterOfYear` / `updateLocale` / `timezone` 等），避免范围蔓延。
- 不调整 `web/src/app.tsx` 入口结构（已正确 side-effect import `@/shared/dayjs`）。
- 不重构 `TaskCalendar.tsx` 或其他业务模块。
