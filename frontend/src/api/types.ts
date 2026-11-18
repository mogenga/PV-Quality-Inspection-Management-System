import type { Role } from '../utils/labels';

export interface PageResult<T> {
  records: T[];
  total: number;
  page: number;
  pageSize: number;
}

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

export type ProjectStatus = 'ACTIVE' | 'CLOSED';

export interface ProjectSummary {
  id: number;
  projectCode: string;
  projectName: string;
  productModel: string | null;
  customerName: string | null;
  manufacturerName: string | null;
  location: string | null;
  status: ProjectStatus;
  remark: string | null;
  createdAt: string;
}

export type BatchStatus = 'PLANNED' | 'PRODUCING' | 'COMPLETED';

export type Scene = 'IQC' | 'PQC' | 'OQC';

export type ValueType = 'NUMERIC' | 'TEXT';

export type JudgeType = 'GT' | 'LT' | 'RANGE' | 'STD_TOL' | 'PASS_FAIL';

export interface StandardSummary {
  id: number;
  projectId: number;
  scene: Scene;
  processName: string;
  itemCode: string;
  itemName: string;
  unit: string | null;
  valueType: ValueType;
  judgeType: JudgeType;
  lowerLimit: number | null;
  upperLimit: number | null;
  targetValue: number | null;
  tolerance: number | null;
  samplingNote: string | null;
  isEnabled: boolean;
  sortOrder: number;
  remark: string | null;
  createdAt: string;
}

export interface BatchSummary {
  id: number;
  projectId: number;
  batchNo: string;
  plannedQty: number;
  startDate: string | null;
  endDate: string | null;
  status: BatchStatus;
  remark: string | null;
  createdAt: string;
}
