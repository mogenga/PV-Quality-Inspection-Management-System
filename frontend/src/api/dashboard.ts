import { apiClient } from './client';
import type { DashboardSummary, ProcessComparison, TrendPoint } from './types';

/** 看板四个筛选条件，空值表示不限。 */
export interface DashboardFilter {
  projectId?: number;
  batchId?: number;
  from?: string;
  to?: string;
}

export function getSummary(params: DashboardFilter): Promise<DashboardSummary> {
  return apiClient.get<unknown, DashboardSummary>('/dashboard/summary', { params });
}

// 后端已按 day 升序返回，前端不再排序
export function getPassRateTrend(params: DashboardFilter): Promise<TrendPoint[]> {
  return apiClient.get<unknown, TrendPoint[]>('/dashboard/pass-rate-trend', { params });
}

// 后端已按 passRate 升序返回（瓶颈在前）
export function getProcessComparison(params: DashboardFilter): Promise<ProcessComparison[]> {
  return apiClient.get<unknown, ProcessComparison[]>('/dashboard/process-comparison', { params });
}
