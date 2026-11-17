import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { CssBaseline, ThemeProvider } from '@mui/material';
import { render } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import type { RouteObject } from 'react-router';
import { useAuthStore } from '../stores/authStore';
import { muiTheme } from '../theme/muiTheme';
import type { Role } from '../utils/labels';

// 直接写 store：zustand persist 在模块导入时已同步 rehydrate，测试里再写 localStorage 不会更新内存态
export function seedSession(role: Role): void {
  useAuthStore.getState().setSession('seeded-token', {
    id: 99,
    username: 'seed-user',
    displayName: '测试管理员',
    role,
    isActive: true,
    createdAt: '2026-11-01T09:00:00',
  });
}

export function renderAt(routeObjects: RouteObject[], path: string) {
  const router = createMemoryRouter(routeObjects, { initialEntries: [path] });
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={muiTheme}>
        <CssBaseline />
        <RouterProvider router={router} />
      </ThemeProvider>
    </QueryClientProvider>,
  );
}
