import { apiClient } from './client';
import type { JudgeType, PageResult, Scene, StandardSummary, ValueType } from './types';

export interface StandardQuery {
  projectId?: number;
  scene?: Scene;
  keyword?: string;
  enabled?: boolean;
  page: number;
  pageSize: number;
}

export interface CreateStandardRequest {
  projectId: number;
  scene: Scene;
  processName: string;
  itemCode: string;
  itemName: string;
  unit?: string | null;
  valueType: ValueType;
  judgeType: JudgeType;
  lowerLimit?: number | null;
  upperLimit?: number | null;
  targetValue?: number | null;
  tolerance?: number | null;
  samplingNote?: string | null;
  isEnabled: boolean;
  sortOrder: number;
  remark?: string | null;
}

// 标准不允许换项目：检验记录里冗余存了 project_id，换项目会让历史数据错位
export type UpdateStandardRequest = Omit<CreateStandardRequest, 'projectId'>;

export function listStandards(params: StandardQuery): Promise<PageResult<StandardSummary>> {
  return apiClient.get<unknown, PageResult<StandardSummary>>('/standards', { params });
}

export function createStandard(req: CreateStandardRequest): Promise<StandardSummary> {
  return apiClient.post<unknown, StandardSummary>('/standards', req);
}

export function updateStandard(id: number, req: UpdateStandardRequest): Promise<StandardSummary> {
  return apiClient.put<unknown, StandardSummary>(`/standards/${id}`, req);
}

export function setStandardEnabled(id: number, isEnabled: boolean): Promise<StandardSummary> {
  return apiClient.patch<unknown, StandardSummary>(`/standards/${id}/enabled`, { isEnabled });
}
