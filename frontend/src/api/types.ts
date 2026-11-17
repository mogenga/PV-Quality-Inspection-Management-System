import type { Role } from '../utils/labels';

export interface UserSummary {
  id: number;
  username: string;
  displayName: string;
  role: Role;
  isActive: boolean;
  createdAt: string;
}

export interface LoginResponse {
  token: string;
  user: UserSummary;
}
