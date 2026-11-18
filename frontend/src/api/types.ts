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

export type RecordResult = 'PASS' | 'FAIL';

export type DefectGrade = 'CRITICAL' | 'MAJOR' | 'MINOR';

export type DefectType = 'CRACK' | 'SOLDER' | 'BUBBLE' | 'DELAMINATION' | 'LOW_POWER' | 'APPEARANCE' | 'OTHER';

export type DefectStatus = 'OPEN' | 'PROCESSING' | 'VERIFIED' | 'CLOSED';

/**
 * 缺陷列表行，对应后端视图 v_defect_detail：缺陷字段 + 记录的标准快照 +
 * 项目/批次/检验员/处理人名称。字段名与后端 DTO 逐一对齐。
 */
export interface DefectDetail {
  defectId: number;
  recordId: number;
  projectId: number;
  projectCode: string;
  projectName: string;
  batchNo: string;
  scene: Scene;
  processName: string;
  itemName: string;
  serialNo: string | null;
  inspectTime: string;
  stdLower: number | null;
  stdUpper: number | null;
  stdTarget: number | null;
  stdTolerance: number | null;
  stdUnit: string | null;
  defectType: DefectType;
  defectGrade: DefectGrade;
  defectStatus: DefectStatus;
  handleNote: string | null;
  resolvedAt: string | null;
  defectCreatedAt: string;
  inspectorName: string;
  handlerName: string | null;
}

/** 录入页的待检项：标准信息 + 该批次上一次实测值（无记录时为 null）。 */
export interface StandardTask {
  standardId: number;
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
  sortOrder: number;
  lastMeasuredValue: number | null;
  lastResult: RecordResult | null;
  lastInspectTime: string | null;
}

export interface InspectionRecordSummary {
  id: number;
  projectId: number;
  batchId: number;
  standardId: number;
  scene: Scene;
  processName: string;
  itemName: string;
  serialNo: string | null;
  inspectorId: number;
  inspectTime: string;
  measuredValue: number | null;
  textValue: string | null;
  result: RecordResult;
  stdLower: number | null;
  stdUpper: number | null;
  stdTarget: number | null;
  stdTolerance: number | null;
  stdUnit: string | null;
  isVoid: boolean;
  remark: string | null;
  createdAt: string;
}
