import { apiClient } from './client';
import type { PageResult, UserSummary } from './types';
import type { Role } from '../utils/labels';

export interface UserQuery {
  keyword?: string;
  role?: Role;
  isActive?: boolean;
  page: number;
  pageSize: number;
}

export interface CreateUserRequest {
  username: string;
  password: string;
  displayName: string;
  role: Role;
}

export interface UpdateUserRequest {
  displayName: string;
  role: Role;
  isActive: boolean;
}

export function listUsers(params: UserQuery): Promise<PageResult<UserSummary>> {
  return apiClient.get<unknown, PageResult<UserSummary>>('/users', { params });
}

export function createUser(req: CreateUserRequest): Promise<UserSummary> {
  return apiClient.post<unknown, UserSummary>('/users', req);
}

export function updateUser(id: number, req: UpdateUserRequest): Promise<UserSummary> {
  return apiClient.put<unknown, UserSummary>(`/users/${id}`, req);
}

export function resetPassword(id: number, newPassword: string): Promise<void> {
  return apiClient.patch<unknown, void>(`/users/${id}/password`, { newPassword });
}
