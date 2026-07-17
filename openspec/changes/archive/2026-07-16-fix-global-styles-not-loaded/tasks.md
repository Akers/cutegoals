# Tasks: fix-global-styles-not-loaded

- [x] 在 `web/src/app.tsx` 顶部添加 `import '@/styles/index.css';`（在现有 React/antd import 之前）
- [x] `cd web && npm run lint` 通过（tsc --noEmit）
- [x] `cd web && npm run build` 通过（umi build）+ grep dist 确认 `.cg-login-bg{` 进入打包产物（dist/umi.css 7.1K 含完整规则）
- [x] `cd web && npm run test` 通过（vitest 86/86，预存在 2 collection 失败可接受）
- [x] `/parent/login` CSS 加载确认：dev server CSS 模块 curl 返回完整规则（`__vite__updateStyle` 注入 `.cg-login-bg{min-height:100vh;display:flex;align-items:center;justify-content:center;padding:1.5rem}`）+ dist/umi.css 7.1K 含规则（agent-browser CLI 未安装，未做 computed style 检查；dev server :8000 在线，用户可自行访问确认视觉）
- [x] `/child/login`、`/admin/login` 共享相同 `.cg-login-bg` CSS 模式，同步生效
- [x] .cg-page 页面（AuthGuard/AdminInitPage/ChildBindPage）CSS 加载确认：dist/umi.css 含 `.cg-page{max-width:80rem;margin:0 auto...}`；预存在 tailwind 失效类不在本次范围
- [x] 提交（verify 阶段分支处理时执行，commit message: `fix: 引入全局样式表修复登录页布局`）
