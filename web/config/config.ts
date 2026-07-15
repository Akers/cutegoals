import { defineConfig } from 'umi';
import routes from './routes';

export default defineConfig({
  npmClient: 'npm',
  // 使用 Vite 作为打包器（与原有 Vite 生态兼容）
  vite: {},
  // 路径别名
  alias: {
    '@': require('path').resolve(__dirname, '../src'),
    '@shared': require('path').resolve(__dirname, '../src/shared'),
    '@admin': require('path').resolve(__dirname, '../src/admin'),
    '@parent': require('path').resolve(__dirname, '../src/parent'),
    '@child': require('path').resolve(__dirname, '../src/child'),
  },
  // 代理：从 vite.config.ts 迁移
  proxy: {
    '/api': {
      target: process.env.API_PROXY_TARGET ?? 'http://localhost:8981',
      changeOrigin: true,
    },
  },
  // 构建输出目录（与 Vite 一致）
  outputPath: 'dist',
  // 路由配置
  routes,
});
