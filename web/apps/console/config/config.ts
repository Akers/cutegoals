import { defineConfig } from 'umi';
import routes from './routes';

export default defineConfig({
  npmClient: 'pnpm',
  // 使用 Vite 作为打包器
  vite: {},
  // 路径别名
  alias: {
    '@': require('path').resolve(__dirname, '../src'),
    '@shared': require('path').resolve(__dirname, '../../../packages/shared/src'),
    '@admin': require('path').resolve(__dirname, '../src/admin'),
    '@parent': require('path').resolve(__dirname, '../src/parent'),
  },
  // 开发代理：API 直连后端
  proxy: {
    '/api': {
      target: process.env.API_PROXY_TARGET ?? 'http://localhost:8080',
      changeOrigin: true,
    },
  },
  // 构建输出目录
  outputPath: 'dist',
  // 路由配置
  routes,
  // 部署在域名根路径
  publicPath: '/',
});
