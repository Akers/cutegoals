/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@shared': resolve(__dirname, 'src/shared'),
      '@admin': resolve(__dirname, 'src/admin'),
      '@parent': resolve(__dirname, 'src/parent'),
      '@child': resolve(__dirname, 'src/child'),
    },
  },
  build: {
    rollupOptions: {
      input: {
        admin: resolve(__dirname, 'admin.html'),
        parent: resolve(__dirname, 'parent.html'),
        child: resolve(__dirname, 'index.html'),
      },
    },
    outDir: 'dist',
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/__tests__/setup.ts',
    css: true,
  },
});
