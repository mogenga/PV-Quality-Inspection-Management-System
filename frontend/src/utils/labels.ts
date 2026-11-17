import type { BatchStatus, ProjectStatus } from '../api/types';

export type Role = 'INSPECTOR' | 'ENGINEER' | 'ADMIN';

export const ROLE_LABELS: Record<Role, string> = {
  INSPECTOR: '检验员',
  ENGINEER: '质量工程师',
  ADMIN: '管理员',
};

export const PROJECT_STATUS_LABELS: Record<ProjectStatus, string> = {
  ACTIVE: '进行中',
  CLOSED: '已关闭',
};

export const BATCH_STATUS_LABELS: Record<BatchStatus, string> = {
  PLANNED: '计划',
  PRODUCING: '生产中',
  COMPLETED: '已完成',
};

export const HOME_BY_ROLE: Record<Role, string> = {
  ENGINEER: '/projects',
  INSPECTOR: '/inspection',
  ADMIN: '/users',
};
