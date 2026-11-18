import { http, HttpResponse } from 'msw';

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
];
