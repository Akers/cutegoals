# Proposal: fix-dayjs-weekday-plugin

## 问题描述

家长端「任务分配」页面在渲染"分配任务"弹窗中的 antd `DatePicker`（`web/src/parent/pages/index.tsx:1448` 的"截止日期"控件）时抛出运行时错误：

```
Something went wrong.
clone.weekday is not a function
```

错误导致整个家长端任务分配页面不可用，家长无法给子女分配一次性任务（包含截止日期的任务）。

## 根因分析

- `dayjs` 默认不包含 `weekday` 插件，必须通过 `dayjs.extend(weekday)` 显式注册后才能调用 `.weekday()` 方法。
- 项目中 **唯一一处** `dayjs.extend(...)` 调用是 `web/src/parent/components/TaskCalendar.tsx:7`，且只注册了 `weekOfYear`，**未注册 `weekday`**。
- 全项目没有任何文件调用过业务层的 `xxx.weekday()`（grep 命中数为 0）；真正的调用方是 antd `DatePicker` 内部的 `rc-picker`（构建产物 `web/dist/index6.js` 中可见 `getWeekFirstDate:function(t,n){return n.locale(Vt(t)).weekday(0)}`），它对 dayjs 实例的 clone 副本调用 `.weekday(0)` 来获取"一周第一天"。
- 由于 `weekday` 插件未注册，dayjs 实例及其 `.clone()` 副本的原型上没有 `weekday` 方法 → 抛出 `clone.weekday is not a function`。
- 项目测试通过 `vi.mock('antd', ...)` 绕过了同一问题（`TaskCalendar.test.tsx:18-20` 注释明确提到 "avoid rc-picker / CSS-in-JS side effects in jsdom"），所以测试一直绿，但真实浏览器渲染时立即踩坑。

补充：`TaskCalendar.tsx` 的 `weekOfYear` 注册是组件局部 side-effect，依赖"日历组件先加载"的隐式顺序，脆弱。dayjs 插件应集中注册在应用入口，保证任何 antd 控件渲染前都已就绪。

## 修复目标

1. 在应用入口统一注册 dayjs 所需插件，使 antd DatePicker / Calendar 等依赖 dayjs 的控件在任何路径下都能正确工作。
2. 至少注册 `weekday` 插件以直接修复本 bug；顺带补齐 antd DatePicker 常用插件（`customParseFormat`、`localizedFormat`、`advancedFormat`、`weekOfYear`），避免后续同类问题。
3. 移除 `TaskCalendar.tsx` 中组件级的局部 `dayjs.extend(weekOfYear)`，消除隐式加载顺序依赖。
4. 提供回归测试证据，确保问题被修复且不会回归。

## 范围与非目标

**范围**：前端 `web/src` 全局 dayjs 初始化与 `TaskCalendar.tsx` 局部注册清理。

**非目标**：
- 不修改任何后端代码或 API。
- 不引入新的 React 组件或业务能力。
- 不改动 antd 主题、DatePicker 业务交互逻辑。
- 不调整测试 mock 策略（测试侧 mock antd 是 jsdom 限制，与生产无关）。

## Breaking Changes

无。仅补齐运行时 side-effect 注册，不改任何 public API 或数据契约。
