import type { ReactNode } from 'react';
import { Navigate } from 'react-router';
import { useAuthStore } from '../stores/authStore';
import { HOME_BY_ROLE } from '../utils/labels';
import type { Role } from '../utils/labels';

export default function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const role = useAuthStore((state) => state.user?.role);
  if (!role) return <Navigate to="/login" replace />;
  if (!roles.includes(role)) return <Navigate to={HOME_BY_ROLE[role]} replace />;
  return children;}
