import type { BatchStatus, JudgeType, ProjectStatus, Scene } from '../api/types';

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

export const SCENE_LABELS: Record<Scene, string> = {
  IQC: '来料检验',
  PQC: '过程检验',
  OQC: '出货检验',
};

export const JUDGE_TYPE_LABELS: Record<JudgeType, string> = {
  GT: '大于',
  LT: '小于',
  RANGE: '区间',
  STD_TOL: '标准值±公差',
  PASS_FAIL: '人工判定',
};

export const HOME_BY_ROLE: Record<Role, string> = {
  ENGINEER: '/projects',
  INSPECTOR: '/inspection',
  ADMIN: '/users',
};
