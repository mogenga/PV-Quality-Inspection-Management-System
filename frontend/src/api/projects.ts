import { apiClient } from './client';
import type { PageResult, ProjectStatus, ProjectSummary } from './types';

export interface ProjectQuery {
  keyword?: string;
  status?: ProjectStatus;
  page: number;
  pageSize: number;
}

export interface ProjectRequest {
  projectCode: string;
  projectName: string;
  productModel?: string | null;
  customerName?: string | null;
  manufacturerName?: string | null;
  location?: string | null;
  status: ProjectStatus;
  remark?: string | null;
}

export function listProjects(params: ProjectQuery): Promise<PageResult<ProjectSummary>> {
  return apiClient.get<unknown, PageResult<ProjectSummary>>('/projects', { params });
}

export function createProject(req: ProjectRequest): Promise<ProjectSummary> {
  return apiClient.post<unknown, ProjectSummary>('/projects', req);
}

export function updateProject(id: number, req: ProjectRequest): Promise<ProjectSummary> {
  return apiClient.put<unknown, ProjectSummary>(`/projects/${id}`, req);
}

export function deleteProject(id: number): Promise<void> {
  return apiClient.delete<unknown, void>(`/projects/${id}`);
}
