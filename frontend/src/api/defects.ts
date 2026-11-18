import { apiClient } from './client';
import type { DefectDetail, DefectGrade, DefectStatus, PageResult } from './types';

export interface DefectQuery {
  grade?: DefectGrade;
  status?: DefectStatus;
  processName?: string;
  from?: string;
  to?: string;
  page: number;
  pageSize: number;
}

export function listDefects(params: DefectQuery): Promise<PageResult<DefectDetail>> {
  return apiClient.get<unknown, PageResult<DefectDetail>>('/defects', { params });
}

// 三个写操作的 body 同形：说明可为空（认领/提交整改），必填与否由后端按流转类型判定
export function processDefect(id: number, note?: string): Promise<void> {
  return apiClient.post<unknown, void>(`/defects/${id}/process`, { note: note ?? null });
}

export function verifyDefect(id: number, note?: string): Promise<void> {
  return apiClient.post<unknown, void>(`/defects/${id}/verify`, { note: note ?? null });
}

export function closeDefect(id: number, note?: string): Promise<void> {
  return apiClient.post<unknown, void>(`/defects/${id}/close`, { note: note ?? null });
}
