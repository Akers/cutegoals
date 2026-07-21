import { defineConfig } from 'vitest/config';
import { resolve } from 'path';

export default defineConfig({
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      '@shared': resolve(__dirname, 'src/shared'),
      '@admin': resolve(__dirname, 'src/admin'),
      '@parent': resolve(__dirname, 'src/parent'),
      '@child': resolve(__dirname, 'src/child'),
      'react-router-dom': resolve(__dirname, 'node_modules/@umijs/renderer-react/node_modules/react-router-dom'),
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/__tests__/setup.ts',
    css: true,
  },
});
