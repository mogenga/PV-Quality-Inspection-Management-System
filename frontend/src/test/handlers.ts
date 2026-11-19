import { http, HttpResponse } from 'msw';
import type { DefectDetail, InspectionRecordSummary } from '../api/types';

// 登出接口要求 Bearer：记录每次请求头，供测试断言"清登录态发生在请求之后"
export const logoutAuthHeaders: (string | null)[] = [];

const projectRows = [
  {
    id: 1,
    projectCode: 'PV-2026-001',
    projectName: '青海格尔木 100MW',
    productModel: '710W 双玻',
    customerName: '国电投',
    manufacturerName: '晶科能源',
    location: '青海格尔木',
    status: 'ACTIVE',
    remark: null,
    createdAt: '2026-11-01T09:00:00',
  },
  {
    id: 2,
    projectCode: 'PV-2026-002',
    projectName: '宁夏中卫 50MW',
    productModel: '705W 单玻',
    customerName: '华能',
    manufacturerName: '隆基绿能',
    location: '宁夏中卫',
    status: 'CLOSED',
    remark: null,
    createdAt: '2026-11-02T09:00:00',
  },
];

const batchRows = [
  {
    id: 11,
    projectId: 1,
    batchNo: 'B-2026-001',
    plannedQty: 1200,
    startDate: '2026-11-01',
    endDate: '2026-11-10',
    status: 'PRODUCING',
    remark: null,
    createdAt: '2026-11-01T10:00:00',
  },
];

const standardRows = [
  {
    id: 1,
    projectId: 1,
    scene: 'IQC',
    processName: '层压',
    itemCode: 'PMAX-01',
    itemName: '组件功率',
    unit: 'W',
    valueType: 'NUMERIC',
    judgeType: 'STD_TOL',
    lowerLimit: null,
    upperLimit: null,
    targetValue: 710,
    tolerance: 5,
    samplingNote: null,
    isEnabled: true,
    sortOrder: 1,
    remark: null,
    createdAt: '2026-11-03T09:00:00',
  },
  {
    id: 2,
    projectId: 1,
    scene: 'PQC',
    processName: '层压',
    itemCode: 'FRAME-01',
    itemName: '边框宽度',
    unit: 'mm',
    valueType: 'NUMERIC',
    judgeType: 'RANGE',
    lowerLimit: 3,
    upperLimit: 3.5,
    targetValue: null,
    tolerance: null,
    samplingNote: null,
    isEnabled: true,
    sortOrder: 2,
    remark: null,
    createdAt: '2026-11-03T09:05:00',
  },
  {
    id: 3,
    projectId: 1,
    scene: 'OQC',
    processName: '包装',
    itemCode: 'APPEAR-01',
    itemName: '外观检查',
    unit: null,
    valueType: 'TEXT',
    judgeType: 'PASS_FAIL',
    lowerLimit: null,
    upperLimit: null,
    targetValue: null,
    tolerance: null,
    samplingNote: null,
    isEnabled: false,
    sortOrder: 3,
    remark: null,
    createdAt: '2026-11-03T09:10:00',
  },
];

const taskRows = [
  {
    standardId: 2,
    scene: 'PQC',
    processName: '层压',
    itemCode: 'FRAME-01',
    itemName: '边框宽度',
    unit: 'mm',
    valueType: 'NUMERIC',
    judgeType: 'RANGE',
    lowerLimit: 3,
    upperLimit: 3.5,
    targetValue: null,
    tolerance: null,
    sortOrder: 2,
    lastMeasuredValue: 3.2,
    lastResult: 'PASS',
    lastInspectTime: '2026-11-17T10:00:00',
  },
  {
    standardId: 3,
    scene: 'PQC',
    processName: '层压',
    itemCode: 'APPEAR-01',
    itemName: '外观检查',
    unit: null,
    valueType: 'TEXT',
    judgeType: 'PASS_FAIL',
    lowerLimit: null,
    upperLimit: null,
    targetValue: null,
    tolerance: null,
    sortOrder: 3,
    lastMeasuredValue: null,
    lastResult: null,
    lastInspectTime: null,
  },
];

const recordRows = [
  {
    id: 101,
    projectId: 1,
    batchId: 11,
    standardId: 2,
    scene: 'PQC',
    processName: '层压',
    itemName: '边框宽度',
    serialNo: null,
    inspectorId: 3,
    inspectTime: '2026-11-17T10:00:00',
    measuredValue: 3.2,
    textValue: null,
    result: 'PASS',
    stdLower: 3,
    stdUpper: 3.5,
    stdTarget: null,
    stdTolerance: null,
    stdUnit: 'mm',
    isVoid: false,
    voidReason: null,
    remark: null,
    createdAt: '2026-11-17T10:00:00',
  },
] satisfies InspectionRecordSummary[];

// 缺陷列表两行覆盖两种操作形态：OPEN 只有「认领」，VERIFIED 有「验证通过/不通过」
const defectRows = [
  {
    defectId: 1,
    recordId: 101,
    projectId: 1,
    projectCode: 'PV-2026-001',
    projectName: '青海格尔木 100MW',
    batchNo: 'B-2026-001',
    scene: 'PQC',
    processName: '层压',
    itemName: '边框宽度',
    serialNo: null,
    inspectTime: '2026-11-17T10:00:00',
    stdLower: 3,
    stdUpper: 3.5,
    stdTarget: null,
    stdTolerance: null,
    stdUnit: 'mm',
    defectType: 'CRACK',
    defectGrade: 'CRITICAL',
    defectStatus: 'OPEN',
    handleNote: null,
    resolvedAt: null,
    defectCreatedAt: '2026-11-17T10:00:00',
    inspectorName: '李检验员',
    handlerName: null,
  },
  {
    defectId: 2,
    recordId: 102,
    projectId: 1,
    projectCode: 'PV-2026-001',
    projectName: '青海格尔木 100MW',
    batchNo: 'B-2026-001',
    scene: 'OQC',
    processName: '包装',
    itemName: '外观检查',
    serialNo: null,
    inspectTime: '2026-11-17T11:00:00',
    stdLower: null,
    stdUpper: null,
    stdTarget: null,
    stdTolerance: null,
    stdUnit: null,
    defectType: 'BUBBLE',
    defectGrade: 'MINOR',
    defectStatus: 'VERIFIED',
    handleNote: '已更换胶膜',
    resolvedAt: null,
    defectCreatedAt: '2026-11-17T11:00:00',
    inspectorName: '李检验员',
    handlerName: '张工程师',
  },
] satisfies DefectDetail[];

// 看板默认数据：指标卡与两张记录类图表都有值，页面测试只需覆盖自己要变的端点
const summaryData = { totalRecords: 120, passRecords: 110, passRate: 91.7, openDefects: 3, avgHandleHours: 12.5 };

const trendRows = [
  { day: '2026-11-17', total: 10, passed: 9, passRate: 90 },
  { day: '2026-11-18', total: 4, passed: 3, passRate: 75 },
];

const processRows = [
  { processName: '层压', total: 10, passed: 8, passRate: 80 },
  { processName: '包装', total: 6, passed: 6, passRate: 100 },
];

const paretoRows = [
  { defectType: 'CRACK', count: 6, cumulativePercent: 60 },
  { defectType: 'BUBBLE', count: 4, cumulativePercent: 100 },
];

const gradeRows = [
  { scene: 'PQC', defectGrade: 'CRITICAL', count: 2 },
  { scene: 'PQC', defectGrade: 'MAJOR', count: 3 },
  { scene: 'OQC', defectGrade: 'MINOR', count: 1 },
];

const controlChartData = {
  itemName: '边框宽度',
  unit: 'mm',
  ucl: 3.5,
  lcl: 3,
  cl: 3.25,
  points: [
    { inspectTime: '2026-11-17T10:00:00', measuredValue: 3.2 },
    { inspectTime: '2026-11-18T10:00:00', measuredValue: 3.3 },
  ],
};

const workloadRows = [
  { inspectorId: 3, inspectorName: '李检验员', count: 12 },
  { inspectorId: 4, inspectorName: '王检验员', count: 7 },
];

const batchGaugeRows = [
  { batchId: 11, batchNo: 'B-2026-001', total: 10, passed: 8, passRate: 80 },
  { batchId: 12, batchNo: 'B-2026-002', total: 3, passed: 2, passRate: 66.7 },
];

const durationRows = [
  { defectGrade: 'CRITICAL', avgHours: 38.5 },
  { defectGrade: 'MAJOR', avgHours: 19.2 },
  { defectGrade: 'MINOR', avgHours: 6.8 },
];

export const handlers = [
  http.post('/api/auth/login', async ({ request }) => {
    const body = (await request.json()) as { username: string; password: string };
    if (body.password !== 'Engineer@123') {
      return HttpResponse.json({ code: 40100, message: '用户名或密码错误' }, { status: 401 });
    }
    return HttpResponse.json({
      code: 0,
      message: 'ok',
      data: {
        token: 'fake-jwt',
        user: {
          id: 2,
          username: 'engineer',
          displayName: '张工程师',
          role: 'ENGINEER',
          isActive: true,
          createdAt: '2026-11-01T09:00:00',
        },
      },
    });
  }),
  http.post('/api/auth/logout', ({ request }) => {
    const authorization = request.headers.get('Authorization');
    logoutAuthHeaders.push(authorization);
    if (!authorization?.startsWith('Bearer ')) {
      return HttpResponse.json({ code: 40101, message: '未登录' }, { status: 401 });
    }
    return HttpResponse.json({ code: 0, message: 'ok', data: null });
  }),
  http.get('/api/users', () =>
    HttpResponse.json({
      code: 0,
      message: 'ok',
      data: {
        records: [
          {
            id: 1,
            username: 'admin',
            displayName: '系统管理员',
            role: 'ADMIN',
            isActive: true,
            createdAt: '2026-11-01T09:00:00',
          },
          {
            id: 2,
            username: 'engineer',
            displayName: '张工程师',
            role: 'ENGINEER',
            isActive: true,
            createdAt: '2026-11-01T09:00:00',
          },
        ],
        total: 2,
        page: 1,
        pageSize: 10,
      },
    }),
  ),
  http.get('/api/projects', () =>
    HttpResponse.json({ code: 0, message: 'ok', data: { records: projectRows, total: 2, page: 1, pageSize: 10 } }),
  ),
  http.get('/api/batches', () =>
    HttpResponse.json({ code: 0, message: 'ok', data: { records: batchRows, total: 1, page: 1, pageSize: 10 } }),
  ),
  http.get('/api/standards', () =>
    HttpResponse.json({ code: 0, message: 'ok', data: { records: standardRows, total: 3, page: 1, pageSize: 10 } }),
  ),
  // 待检项不传 total：接口返回数组而非分页对象
  http.get('/api/inspection/tasks', () => HttpResponse.json({ code: 0, message: 'ok', data: taskRows })),
  http.get('/api/inspection/records', () =>
    HttpResponse.json({
      code: 0,
      message: 'ok',
      data: { records: recordRows, total: 1, page: 1, pageSize: 10 },
    }),
  ),
  http.get('/api/defects', () =>
    HttpResponse.json({
      code: 0,
      message: 'ok',
      data: { records: defectRows, total: defectRows.length, page: 1, pageSize: 10 },
    }),
  ),
  http.get('/api/dashboard/summary', () => HttpResponse.json({ code: 0, message: 'ok', data: summaryData })),
  http.get('/api/dashboard/pass-rate-trend', () => HttpResponse.json({ code: 0, message: 'ok', data: trendRows })),
  http.get('/api/dashboard/process-comparison', () => HttpResponse.json({ code: 0, message: 'ok', data: processRows })),
  http.get('/api/dashboard/pareto', () => HttpResponse.json({ code: 0, message: 'ok', data: paretoRows })),
  http.get('/api/dashboard/defect-grade', () => HttpResponse.json({ code: 0, message: 'ok', data: gradeRows })),
  http.get('/api/dashboard/control-chart', () => HttpResponse.json({ code: 0, message: 'ok', data: controlChartData })),
  http.get('/api/dashboard/inspector-workload', () =>
    HttpResponse.json({ code: 0, message: 'ok', data: workloadRows }),
  ),
  http.get('/api/dashboard/batch-gauge', () => HttpResponse.json({ code: 0, message: 'ok', data: batchGaugeRows })),
  http.get('/api/dashboard/defect-duration', () => HttpResponse.json({ code: 0, message: 'ok', data: durationRows })),
];
