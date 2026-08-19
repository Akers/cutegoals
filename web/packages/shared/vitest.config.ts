import { defineConfig } from 'vitest/config';
import { resolve, dirname } from 'path';
import { createRequire } from 'module';

const req = createRequire(import.meta.url);
// umi 内部使用自身嵌套的 react-router-dom；测试中保持一致实例
const rendererReactPkg = req.resolve('@umijs/renderer-react/package.json');
const nestedRrd = resolve(dirname(rendererReactPkg), 'node_modules/react-router-dom');

export default defineConfig({
  resolve: {
    alias: {
      '@shared': resolve(__dirname, 'src'),
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
