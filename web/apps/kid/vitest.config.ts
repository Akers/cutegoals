import { defineConfig } from 'vitest/config';
import { resolve, dirname } from 'path';
import { createRequire } from 'module';

const req = createRequire(import.meta.url);
const rendererReactPkg = req.resolve('@umijs/renderer-react/package.json');
const nestedRrd = resolve(dirname(rendererReactPkg), 'node_modules/react-router-dom');

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
