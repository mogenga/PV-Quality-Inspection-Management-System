import { createBrowserRouter, Navigate } from 'react-router';
import type { RouteObject } from 'react-router';
import AppLayout from '../components/AppLayout';
import RequireAuth from '../components/RequireAuth';
import RequireRole from '../components/RequireRole';
import LoginPage from '../pages/LoginPage';
import UserListPage from '../pages/users/UserListPage';
import ProjectBatchPage from '../pages/projects/ProjectBatchPage';
import StandardListPage from '../pages/standards/StandardListPage';
import InspectionPage from '../pages/inspection/InspectionPage';
import RecordListPage from '../pages/inspection/RecordListPage';
import DefectListPage from '../pages/defects/DefectListPage';
import DashboardPage from '../pages/dashboard/DashboardPage';
import { useAuthStore } from '../stores/authStore';
import { HOME_BY_ROLE } from '../utils/labels';

function HomeRedirect() {
  const role = useAuthStore((state) => state.user?.role);
  return <Navigate to={role ? HOME_BY_ROLE[role] : '/login'} replace />;
}

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
          { path: 'dashboard', element: <DashboardPage /> },
          { path: 'projects', element: <ProjectBatchPage /> },
          {
            path: 'inspection',
            element: (
              <RequireRole roles={['ENGINEER', 'INSPECTOR']}>
                <InspectionPage />
              </RequireRole>
            ),
          },
          { path: 'records', element: <RecordListPage /> },
          // 缺陷列表对全部角色开放，流转操作在页面内按角色控制可见性
          { path: 'defects', element: <DefectListPage /> },
          {
            path: 'standards',
            element: (
              <RequireRole roles={['ENGINEER', 'ADMIN']}>
                <StandardListPage />
              </RequireRole>
            ),
          },
          {
            path: 'users',
            element: (
              <RequireRole roles={['ADMIN']}>
                <UserListPage />
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
