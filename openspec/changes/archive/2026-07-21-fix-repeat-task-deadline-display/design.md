# Design: 重复任务列表显示频率标签

- 新增 `repeatTaskLabel()` 函数，解析 `snapshotTemplateTaskType` + `snapshotTemplateTypeConfig`
- 列表渲染根据函数返回值显示频率标签或 `截止 {deadline}`
- 仅改 `web/src/parent/pages/index.tsx`
