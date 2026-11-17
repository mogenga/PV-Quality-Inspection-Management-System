export type Role = 'INSPECTOR' | 'ENGINEER' | 'ADMIN';

export const ROLE_LABELS: Record<Role, string> = {
  INSPECTOR: '检验员',
  ENGINEER: '质量工程师',
  ADMIN: '管理员',
};
