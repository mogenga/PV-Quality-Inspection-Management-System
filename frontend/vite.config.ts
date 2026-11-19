/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    // 13 个测试文件并发跑时，默认 5 秒单测超时与 1 秒 findBy 超时不够：
    // 交互密集的用例（多次 userEvent.type + MUI 下拉）单次要 2–3 秒，
    // 并发争抢下会偶发超时，看起来像断言失败。放宽超时不改变任何断言。
    testTimeout: 20000,
    hookTimeout: 20000,
  },
});
