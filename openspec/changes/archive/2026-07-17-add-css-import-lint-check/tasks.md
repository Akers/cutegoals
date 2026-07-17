# Tasks

- [x] 创建 `web/scripts/` 目录
- [x] 写 `web/scripts/check-css-loads.mjs`（零依赖：`node:fs/promises` + `node:path`，递归 readdir + 正则匹配 basename）
- [x] `web/package.json` 新增 `"lint:css": "node scripts/check-css-loads.mjs"`
- [x] `web/package.json` 将 `lint` 改为 `"tsc --noEmit && npm run lint:css"`
- [x] 运行 `npm run lint:css` 验证当前状态 PASS（index.css 被 app.tsx import，themes.css 被 index.css @import）
- [x] 临时删除 app.tsx 的 CSS import 行，验证脚本 FAIL 报告 index.css 未引用，然后恢复
- [x] 运行 `npm run lint`（tsc + lint:css）确认整体 PASS
- [x] 提交（commit message: `tweak: 新增 CSS 加载检查脚本预防样式未 import 回归`，verify 阶段分支处理时执行）
