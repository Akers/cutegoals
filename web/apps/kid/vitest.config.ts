import { defineConfig } from 'vitest/config';
import { resolve, dirname } from 'path';
import { createRequire } from 'module';

const req = createRequire(import.meta.url);
// 在 pnpm 严格隔离布局下，react-router-dom 提升到 web/node_modules 根，
// 从 app 的 node_modules 向上解析即可定位；测试中需要 umi 内部使用同一实例
const nestedRrd = req.resolve('react-router-dom', { paths: [resolve(__dirname, '../../node_modules')] });

export default defineConfig({
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      '@shared': resolve(__dirname, '../../packages/shared/src'),
      'react-router-dom': nestedRrd,
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    css: true,
  },
});
