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
  },
  // 开发代理：孩子端 API 走 /child/api 前缀，dev server 剥离后转发后端
  // （与生产拓扑一致：网关 /child/api/* → kid 容器 → 白名单 → 后端 /api/*）
  proxy: {
    '/child/api': {
      target: process.env.API_PROXY_TARGET ?? 'http://localhost:8080',
      changeOrigin: true,
      pathRewrite: { '^/child/api': '/api' },
    },
  },
  // 构建输出目录
  outputPath: 'dist',
  // 路由配置（保留 /child 前缀，由网关按路径分流到本应用容器）
  routes,
  // 静态资源公共路径：生产构建为 /child/（kid 容器 nginx 以 alias 剥离）；
  // 开发服务器从根路径提供服务，资源亦在根路径下。
  publicPath: process.env.NODE_ENV === 'production' ? '/child/' : '/',
});
