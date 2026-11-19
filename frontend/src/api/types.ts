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

/** 看板指标卡：平均处理时长无已关闭缺陷时为 null（「没有数据」而非 0 小时）。 */
export interface DashboardSummary {
  totalRecords: number;
  passRecords: number;
  passRate: number;
  openDefects: number;
  avgHandleHours: number | null;
}

/** CH-01 每日合格率趋势的一个点。 */
export interface TrendPoint {
  day: string;
  total: number;
  passed: number;
  passRate: number;
}

/** CH-03 工序合格率对比的一行。 */
export interface ProcessComparison {
  processName: string;
  total: number;
  passed: number;
  passRate: number;
}

/** CH-02 缺陷帕累托的一行：次数与累计百分比（末项恒为 100）。 */
export interface ParetoItem {
  defectType: DefectType;
  count: number;
  cumulativePercent: number;
}

/** CH-05 缺陷等级构成的一行：场景 × 等级计数。 */
export interface GradeSceneCount {
  scene: Scene;
  defectGrade: DefectGrade;
  count: number;
}

/** CH-04 参数控制图的一个点。 */
export interface ControlPoint {
  inspectTime: string;
  measuredValue: number;
}

/**
 * CH-04 参数控制图。控制限取自标准（SRS 6.3）：单侧判定方式没有的那一侧为 null
 * （GT 无上限、LT 无下限），前端据此只画存在的线。
 */
export interface ControlChart {
  itemName: string;
  unit: string | null;
  ucl: number | null;
  lcl: number | null;
  cl: number;
  points: ControlPoint[];
}

/** CH-07 检验员工作量的一行。 */
export interface InspectorWorkload {
  inspectorId: number;
  inspectorName: string;
  count: number;
}

/** CH-06 批次合格率的一行，同时服务 FR-13 的批次汇总表。 */
export interface BatchPassRate {
  batchId: number;
  batchNo: string;
  total: number;
  passed: number;
  passRate: number;
}

/** CH-08 缺陷处理时长的一行：某等级已关闭缺陷的平均小时数。 */
export interface DefectDuration {
  defectGrade: DefectGrade;
  avgHours: number | null;
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
  voidReason: string | null;
  remark: string | null;
  createdAt: string;
}
