import { defineConfig } from 'umi';

export default defineConfig({
  npmClient: 'npm',
  // 路径别名
  alias: {
    '@': require('path').resolve(__dirname, '../src'),
  },
  // 代理：从 vite.config.ts 迁移
  proxy: {
    '/api': {
      target: process.env.API_PROXY_TARGET ?? 'http://localhost:8981',
      changeOrigin: true,
    },
  },
  // 开发服务器端口（与 Vite 保持一致）
  devServer: {
    port: 5173,
  },
  // 构建输出目录（与 Vite 一致）
  outputPath: 'dist',
  // 约定路由（先给空数组，Task 4.1 填充）
  routes: [],
});
