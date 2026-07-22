# Tasks: fix-dayjs-weekday-plugin

> Capability: `web-app`（前端运行时初始化）
>
> 修复模式：hotfix / direct build。所有任务初始为未完成，按依赖顺序执行。

- [x] 1. 新增 `web/src/shared/dayjs.ts`，集中注册 dayjs 插件（weekday、weekOfYear、customParseFormat、localizedFormat、advancedFormat）
  - 产物：`web/src/shared/dayjs.ts` 存在且包含 5 个 `dayjs.extend(...)` 调用
  - 证据：新增回归测试 `web/src/shared/__tests__/dayjs.test.ts`，断言 `dayjs().weekday(0)`、`dayjs().week()`、`dayjs('2026-07-22', 'YYYY-MM-DD').format('YYYY-MM-DD')` 均不抛错；运行 `pnpm --filter web test` 该用例通过
- [ ] 2. 在 `web/src/app.tsx` 顶部添加 `import '@/shared/dayjs'` side-effect
  - 产物：`web/src/app.tsx` 第 1 行附近出现该 import，且早于 antd/AuthProvider 相关 import 执行
  - 证据：`pnpm --filter web build` 成功；dev server 启动后家长端任务分配页面可正常打开
- [ ] 3. 移除 `web/src/parent/components/TaskCalendar.tsx` 中的局部 `dayjs.extend(weekOfYear)` 与对应插件 import
  - 产物：`TaskCalendar.tsx` 不再包含 `dayjs.extend(weekOfYear)` 与 `import weekOfYear from 'dayjs/plugin/weekOfYear'`，但保留 `import dayjs from 'dayjs'`
  - 证据：`pnpm --filter web test` 中 `TaskCalendar.test.tsx` 通过；`computeWeekNumbers` 仍正常工作（依赖全局注册的 weekOfYear）
- [ ] 4. 运行前端完整测试与构建，验证修复
  - 证据：`pnpm --filter web test` 全部通过；`pnpm --filter web build` 成功；手工或 E2E 验证家长端任务分配弹窗 DatePicker 可正常选择日期
