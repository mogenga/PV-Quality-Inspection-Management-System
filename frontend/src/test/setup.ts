import '@testing-library/jest-dom/vitest';
import { cleanup, configure } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { useAuthStore } from '../stores/authStore';
import { server } from './server';

// findBy*/waitFor 默认只等 1 秒：全量并发时一次 MSW 往返排队就可能超过，
// 报出来是「找不到元素」，实际是还没渲染。放宽等待不改变任何断言。
configure({ asyncUtilTimeout: 5000 });

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => {
  cleanup();
  server.resetHandlers();
  // zustand 是模块级单例，仅清 localStorage 不会重置内存中的登录态
  useAuthStore.getState().clear();
  localStorage.clear();
});
afterAll(() => server.close());
