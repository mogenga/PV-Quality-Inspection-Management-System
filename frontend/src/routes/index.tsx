import { createBrowserRouter, Navigate } from 'react-router';
import type { RouteObject } from 'react-router';
import AppLayout from '../components/AppLayout';
import RequireAuth from '../components/RequireAuth';
import RequireRole from '../components/RequireRole';
import LoginPage from '../pages/LoginPage';
import PlaceholderPage from '../pages/PlaceholderPage';
import { useAuthStore } from '../stores/authStore';
import { HOME_BY_ROLE } from '../utils/labels';

function HomeRedirect() {
  const role = useAuthStore((state) => state.user?.role);
  return <Navigate to={role ? HOME_BY_ROLE[role] : '/login'} replace />;
}

const pages: { path: string; title: string }[] = [
  { path: 'dashboard', title: '质量看板' },
  { path: 'projects', title: '项目与批次' },
  { path: 'standards', title: '校验标准' },
  { path: 'inspection', title: '校验录入' },
  { path: 'defects', title: '缺陷处理' },
];

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: <RequireAuth />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { index: true, element: <HomeRedirect /> },
          ...pages.map(({ path, title }) => ({ path, element: <PlaceholderPage title={title} /> })),
          {
            path: 'users',
            element: (
              <RequireRole roles={['ADMIN']}>
                <PlaceholderPage title="用户管理" />
              </RequireRole>
            ),
          },
          { path: '*', element: <Navigate to="/" replace /> },
        ],
      },
    ],
  },
];

export function createAppRouter() {
  return createBrowserRouter(routes);
}
