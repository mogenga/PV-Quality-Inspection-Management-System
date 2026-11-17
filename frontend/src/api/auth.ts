import { apiClient } from './client';
import type { LoginResponse } from './types';

export interface LoginRequest {
  username: string;
  password: string;
}

export function login(req: LoginRequest): Promise<LoginResponse> {
  return apiClient.post<unknown, LoginResponse>('/auth/login', req);
}

export function logout(): Promise<void> {
  return apiClient.post<unknown, void>('/auth/logout');
}
