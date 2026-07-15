# 验证报告：batch-assign-disabled-template-filter

## 变更目标

家长端“批量分配任务”弹窗的模板下拉框应仅展示启用中的任务模板，已停用模板不应出现。

## 改动范围

- `web/src/parent/pages/index.tsx`: 将批量分配任务弹窗的模板列表请求从 `/task-templates` 改为 `/task-templates?enabled=true`。
- 模板管理页面仍使用无 `enabled` 筛选的 `usePaginatedData('/task-templates')`，行为不变。
- 新增 Comet hotfix 产物：`proposal.md`、`design.md`、`tasks.md`、`.comet.yaml` 及相关状态文件。

## 验证检查项

| 检查项 | 验证方式 | 结果 |
|---|---|---|
| 前端请求路径正确 | `grep "task-templates?enabled=true" web/src/parent/pages/index.tsx` | ✅ 匹配 |
| 模板管理页面未受影响 | `grep "usePaginatedData.*'/task-templates'" web/src/parent/pages/index.tsx` | ✅ 仍使用无 `enabled` 参数的路径 |
| TypeScript 类型检查 | `npm run build`（`tsc -b`） | ✅ 通过 |
| 生产构建 | `npm run build`（`vite build`） | ✅ 通过，无错误 |
| 代码审查 | `review_mode: off`，手动复核 diff | ✅ 仅 1 行功能改动，无安全问题 |
| 后端接口未变更 | `git diff --stat HEAD~2..HEAD` | ✅ 仅 web 前端与 Comet 产物 |

## 运行命令

```bash
cd /home/akers/projects/cutegoals/web
npm run build
```

输出：

```
vite v5.4.21 building for production...
transforming...
✓ 78 modules transformed.
rendering chunks...
computing gzip size...
dist/index.html                   0.40 kB │ gzip:  0.27 kB
dist/child.html                   0.55 kB │ gzip:  0.38 kB
dist/admin.html                   0.55 kB │ gzip:  0.39 kB
dist/parent.html                  0.55 kB │ gzip:  0.38 kB
✓ built in 918ms
```

## 限制说明

- 本验证仅覆盖前端构建和静态路径检查；未运行浏览器级端到端测试。
- 实际运行时需在家长端验证：停用模板后，打开“批量分配任务”弹窗，已停用模板不再出现在下拉框中；模板管理页面仍能看到该模板并可重新启用。

## 结论

验证通过，可进入 archive。
