import { apiClient } from './client';
import type {
  DefectGrade,
  DefectType,
  InspectionRecordSummary,
  PageResult,
  RecordResult,
  Scene,
  StandardTask,
} from './types';

export interface RecordQuery {
  projectId?: number;
  batchId?: number;
  scene?: Scene;
  processName?: string;
  result?: RecordResult;
  from?: string;
  to?: string;
  page: number;
  pageSize: number;
}

// 不含 result / inspectTime：判定与时间都由服务端决定（BR-01）
export interface CreateRecordRequest {
  batchId: number;
  standardId: number;
  measuredValue?: number | null;
  textValue?: string | null;
  serialNo?: string | null;
  defectType?: DefectType | null;
  defectGrade?: DefectGrade | null;
  remark?: string | null;
}

export interface TaskQuery {
  projectId: number;
  batchId: number;
  scene?: Scene;
  processName?: string;
}

export interface JudgeRequest {
  standardId: number;
  measuredValue?: number | null;
  textValue?: string | null;
}

export function listTasks(params: TaskQuery): Promise<StandardTask[]> {
  return apiClient.get<unknown, StandardTask[]>('/inspection/tasks', { params });
}

export function judge(req: JudgeRequest): Promise<{ result: RecordResult }> {
  return apiClient.post<unknown, { result: RecordResult }>('/inspection/judge', req);
}

export function createRecord(req: CreateRecordRequest): Promise<InspectionRecordSummary> {
  return apiClient.post<unknown, InspectionRecordSummary>('/inspection/records', req);
}

export function listRecords(params: RecordQuery): Promise<PageResult<InspectionRecordSummary>> {
  return apiClient.get<unknown, PageResult<InspectionRecordSummary>>('/inspection/records', { params });
}
