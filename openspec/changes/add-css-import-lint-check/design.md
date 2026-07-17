# Design: CSS 加载检查脚本

## 根因回顾

前几次 hotfix 暴露的盲区：CSS 文件存在 + 含正确规则 + className 引用正确，但**没有模块 import 它**。lint (tsc) / build (umi) / test (vitest jsdom) 全部 green，但浏览器实际无样式。需要一个专门检测"CSS 是否被 import"的守护。

## 方案：自定义 Node 脚本（零依赖）

### 脚本逻辑（check-css-loads.mjs）

核心步骤：

1. 用 `node:fs/promises.readdir` 递归列出 `src/styles/` 下所有 `.css` 文件（当前：`index.css`、`themes.css`）
2. 用 `node:fs/promises.readdir` 递归列出 `src/` 下所有 `.ts` / `.tsx` / `.css` 文件内容
3. 对每个 styles 下的 CSS 文件，检查是否存在某源文件含有引用它的语句：
   - JS/TS：`import\s+['"][^'"]*<basename>['"]` 或 `import\s+\w+\s+from\s+['"][^'"]*<basename>['"]`
   - CSS：`@import\s+['"][^'"]*<basename>['"]`（themes.css 被 index.css @import 也算）
4. 按 basename 匹配（如 `index.css` 匹配 `@/styles/index.css` / `./index.css` / `../styles/index.css`），不解析路径别名
5. 排除 CSS 文件自身（`index.css` 不会因含自己的 basename 而误判为"已引用"）
6. 全部命中 → exit 0；任一未命中 → 输出错误清单 + exit 1

### 检测规则细节

| 场景 | 示例 | 是否算引用 |
|---|---|---|
| side-effect import | `import '@/styles/index.css';` | ✅ |
| 命名 import | `import styles from './x.css'` | ✅（兼容） |
| CSS @import | `@import './themes.css';` | ✅ |
| 动态 import | `import(variable)` | ❌（项目无此场景） |
| 自引用 | `index.css` 内含 `index.css` 字符串 | ❌（排除自身） |

### exit code

- `0`：所有 CSS 文件都被引用
- `1`：存在未引用的 CSS 文件（stderr 输出清单 + 修复建议）

## 替代方案对比

| 方案 | 优点 | 缺点 | 选择 |
|---|---|---|---|
| **自定义脚本（本方案）** | 零依赖、检测准确、~50 行、专针对 CSS | 自己维护脚本 | ✅ |
| eslint-plugin-import + `import/no-unused-modules` | ESLint 生态集成 | 规则不适用于 CSS（side-effect import 不算使用），需额外配置，引入依赖 | ❌ |
| ESLint 自定义 plugin | ESLint 生态集成最强 | 过度工程，~150 行 plugin 代码，收益不抵成本 | ❌ |
| pre-commit hook | 提交前拦截 | 不在 CI 中，依赖本地 hook 安装 | ❌（互补不替代 CI） |

## 边界

- 仅检测 `src/styles/*.css`（不递归子目录，当前无子目录需求）
- 仅检测 `.ts` / `.tsx` / `.css` 源码（不检测 `.js` / `.jsx`，项目主要是 TS）
- 不检测 CSS 内容正确性（那是 stylelint 的职责）
- 不检测动态 import（项目无此场景）
- basename 匹配（不解析路径别名），简单但足够覆盖现有 `@/styles/x.css` / `./x.css` 模式

## 集成到 `npm run lint`

将 `lint` script 从 `"tsc --noEmit"` 改为 `"tsc --noEmit && npm run lint:css"`：

- CI / hotfix 流程调 `npm run lint` 会自动包含 CSS 检查
- 单独运行 `npm run lint:css` 仍可独立检查 CSS
- 前 4 次 hotfix 的 `npm run lint` 验证结果不受影响（tsc 部分不变）

## 测试策略

1. 运行 `npm run lint:css`：当前 `index.css`（被 app.tsx import）+ `themes.css`（被 index.css @import）都应 PASS（exit 0）
2. 临时删除 app.tsx 的 `import '@/styles/index.css';` 行，运行 `npm run lint:css`：应 FAIL（exit 1）报告 `index.css` 未被任何源码引用
3. 恢复 app.tsx，确认再次 PASS
4. 运行 `npm run lint`（tsc + lint:css）确认整体 PASS
