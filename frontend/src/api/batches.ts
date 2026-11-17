import { apiClient } from './client';
import type { BatchStatus, BatchSummary, PageResult } from './types';

export interface BatchQuery {
  projectId?: number;
  keyword?: string;
  status?: BatchStatus;
  page: number;
  pageSize: number;
}

export interface CreateBatchRequest {
  projectId: number;
  batchNo: string;
  plannedQty: number;
  startDate?: string | null;
  endDate?: string | null;
  status: BatchStatus;
  remark?: string | null;
}

// 批次不允许换项目：更新体不含 projectId，否则等于前端以为可以搬项目
export type UpdateBatchRequest = Omit<CreateBatchRequest, 'projectId'>;

export function listBatches(params: BatchQuery): Promise<PageResult<BatchSummary>> {
  return apiClient.get<unknown, PageResult<BatchSummary>>('/batches', { params });
}

export function createBatch(req: CreateBatchRequest): Promise<BatchSummary> {
  return apiClient.post<unknown, BatchSummary>('/batches', req);
}

export function updateBatch(id: number, req: UpdateBatchRequest): Promise<BatchSummary> {
  return apiClient.put<unknown, BatchSummary>(`/batches/${id}`, req);
}

export function deleteBatch(id: number): Promise<void> {
  return apiClient.delete<unknown, void>(`/batches/${id}`);
}
