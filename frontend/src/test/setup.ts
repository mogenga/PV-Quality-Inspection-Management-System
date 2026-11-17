import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { useAuthStore } from '../stores/authStore';
import { server } from './server';

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => {
  cleanup();
  server.resetHandlers();
  // zustand 是模块级单例，仅清 localStorage 不会重置内存中的登录态
  useAuthStore.getState().clear();
  localStorage.clear();
});
afterAll(() => server.close());
