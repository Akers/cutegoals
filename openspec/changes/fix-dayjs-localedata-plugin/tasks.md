# Tasks: 修复 antd DatePicker dayjs localeData 插件未注册

- [x] Task 1: 在 `web/src/shared/__tests__/dayjs.test.ts` 追加 RED 回归用例（localeData + weekYear），运行确认失败
- [x] Task 2: 在 `web/src/shared/dayjs.ts` 追加 `localeData` 与 `weekYear` 插件的 import + `dayjs.extend(...)`，使测试转绿
- [x] Task 3: 跑 `pnpm --filter web test` 与 `pnpm --filter web build`，确认全量回归通过
