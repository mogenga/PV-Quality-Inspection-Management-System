import { apiClient } from './client';
import type {
  BatchPassRate,
  ControlChart,
  DashboardSummary,
  DefectDuration,
  GradeSceneCount,
  InspectorWorkload,
  ParetoItem,
  ProcessComparison,
  TrendPoint,
} from './types';

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

// 后端已按次数降序返回，末项 cumulativePercent 恒为 100
export function getPareto(params: DashboardFilter): Promise<ParetoItem[]> {
  return apiClient.get<unknown, ParetoItem[]>('/dashboard/pareto', { params });
}

export function getDefectGrade(params: DashboardFilter): Promise<GradeSceneCount[]> {
  return apiClient.get<unknown, GradeSceneCount[]>('/dashboard/defect-grade', { params });
}

// 控制图只按标准取数，不参与页面的项目/批次/时间筛选
export function getControlChart(standardId: number): Promise<ControlChart> {
  return apiClient.get<unknown, ControlChart>('/dashboard/control-chart', { params: { standardId } });
}

export function getInspectorWorkload(params: DashboardFilter): Promise<InspectorWorkload[]> {
  return apiClient.get<unknown, InspectorWorkload[]>('/dashboard/inspector-workload', { params });
}

export function getBatchGauge(params: DashboardFilter): Promise<BatchPassRate[]> {
  return apiClient.get<unknown, BatchPassRate[]>('/dashboard/batch-gauge', { params });
}

export function getDefectDuration(params: DashboardFilter): Promise<DefectDuration[]> {
  return apiClient.get<unknown, DefectDuration[]>('/dashboard/defect-duration', { params });
}
