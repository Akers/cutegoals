# Design: fix-dayjs-weekday-plugin

## 方案概述

在 Umi 运行时入口 `web/src/app.tsx` 顶部通过 side-effect import 引入新建的 `web/src/shared/dayjs.ts`，集中注册 dayjs 所需插件；同时从 `TaskCalendar.tsx` 中移除组件级局部注册。

## 关键决策

### 决策 1：集中注册位置 → `web/src/shared/dayjs.ts`

**选择**：新建独立模块 `web/src/shared/dayjs.ts`，只做 side-effect，不导出任何内容；在 `web/src/app.tsx` 顶部 `import '@/shared/dayjs'`。

**理由**：
- `web/src/shared/` 是项目现有的跨域共享层（已存在 `theme.ts`、`role.ts`、`auth/` 等），放这里语义一致。
- 独立模块比直接在 `app.tsx` 内写 `dayjs.extend(...)` 更易于后续扩展（未来加 i18n、自定义插件时单点维护）。
- side-effect import 保证 `app.tsx` 顶层（Umi runtime 入口）执行时插件就绪，早于任何 React 组件渲染，包括 `rootContainer` 内的 `AuthProvider`/`App`。

### 决策 2：注册的插件清单

| 插件 | 用途 | 是否必须 |
|---|---|---|
| `weekday` | 直接修复本 bug（rc-picker 调用 `.weekday(0)`） | 必须 |
| `weekOfYear` | `TaskCalendar.tsx` 原 `computeWeekNumbers` 依赖；从组件级搬到全局，保证加载顺序 | 必须 |
| `customParseFormat` | antd DatePicker 的 `format` prop 与受控 `value` 解析依赖此插件 | 强烈建议 |
| `localizedFormat` | antd DatePicker 面板本地化文案依赖 | 建议 |
| `advancedFormat` | 支持 `wo`、`Do`、`kmm` 等高级格式 token，避免面板渲染报错 | 建议 |

**理由**：antd 官方 README 明确要求使用 DatePicker 时注册上述插件。仅注册 `weekday` 虽能修本 bug，但下次家长在 DatePicker 里选日期时仍可能因 `customParseFormat` 缺失而踩坑，一次性补齐避免回归。

### 决策 3：清理 `TaskCalendar.tsx` 局部注册

**选择**：删除 `TaskCalendar.tsx:2-3,7` 的 `weekOfYear` import 与 `dayjs.extend(weekOfYear)` 调用。

**理由**：
- 全局已注册，组件级重复注册是冗余。
- 组件级 side-effect 依赖"组件被 import 才执行"，若未来有页面先于 `TaskCalendar` 渲染了 antd DatePicker，又会复现同类问题。集中注册消除这一隐式顺序依赖。

`TaskCalendar.tsx` 仍保留 `import dayjs from 'dayjs'`（组件内部用 dayjs 做日期计算），只是不再做插件注册。

## 风险与回滚

**风险**：
- 低风险。dayjs 插件注册是幂等的（重复 extend 同一插件无副作用）。
- 新增 side-effect import 会在应用启动时多执行几行代码，对启动性能影响可忽略（< 1ms）。

**回滚**：直接 `git revert` 该 change 的提交即可，无数据迁移、无环境变更。

## 测试策略

- **回归测试**：在 `web/src/shared/__tests__/` 下新增 `dayjs.test.ts`，断言注册后 `dayjs().weekday()`、`dayjs().week()`、`dayjs('2026-07-22', 'YYYY-MM-DD').format(...)` 等方法可用。
- **现有测试**：运行 `web/src/parent/pages/__tests__/ParentTasksPage.test.tsx` 与 `web/src/parent/components/__tests__/TaskCalendar.test.tsx` 确认不回归。
- **手工验证**：`npm run build` 后或 dev server 中打开家长端任务分配页面，点击"分配任务"打开弹窗，确认 DatePicker 正常渲染、可选择日期、不再报错。
