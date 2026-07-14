# 验证报告：fix-parent-task-template-and-batch-assignment

## 变更摘要

- 家长端任务模板列表增加“已启用 / 已停用”状态标签显示。
- 家长端批量分配弹窗对齐后端 `POST /api/task-assignments/batch` 契约，新增难度选择、开始/结束日期和孩子多选，发送正确的批量请求体。
- 顺手修复了同文件中 4 处 `catch (err: any)` 类型错误，使 lint 通过。

## 改动文件

- `web/src/parent/pages/index.tsx`（功能修复与格式整理）
- `openspec/changes/fix-parent-task-template-and-batch-assignment/`（OpenSpec 产物）

## 验证项

| 检查项 | 命令 | 结果 |
|---|---|---|
| tasks.md 全部完成 | `grep -c '\- \[x\]' tasks.md` | 9/9 完成 |
| 前端 Lint | `npm run lint` | 0 errors（6 个 pre-existing warnings） |
| 前端构建 | `npm run build` | 通过 |
| 前端测试 | `npm run test` | 120 测试全部通过 |
| 后端构建 | `mvn install -DskipTests` | 通过 |
| 后端相关测试 | `mvn test -Dtest=TaskAssignmentServiceTest` | 24 测试全部通过 |
| 安全问题 | diff 检查 | 无硬编码密钥、无新增 unsafe 操作 |
| 代码审查 | `review_mode: off` | 本次跳过自动代码审查 |

## 已知事项

- Lint 仍有 6 个既有 warning（均与本次改动无关，位于 `AuthContext.tsx`、`Toast.tsx`、`components/index.tsx`）。
- 未创建 delta spec，因为本次修复未变更已有 spec 的验收场景。

## 分支处理

用户选择：保持现状（当前已在 main 分支提交完成，不推送、不合并、不丢弃）。

## 结论

验证通过，可进入归档。
