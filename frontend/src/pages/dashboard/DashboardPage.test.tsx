import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { expect, it, vi } from 'vitest';
import { routes } from '../../routes';
import { renderAt, seedSession } from '../../test/renderWithProviders';
import { server } from '../../test/server';

// jsdom 没有 canvas：页面测试用替身。图表 option 的映射在 options.test.ts，
// EChart 自身的可访问名与生命周期在 EChart.test.tsx。
vi.mock('../../components/EChart', () => ({
  default: ({ ariaLabel }: { ariaLabel: string }) => <div role="img" aria-label={ariaLabel} />,
}));

const summary = { totalRecords: 120, passRecords: 110, passRate: 91.7, openDefects: 3, avgHandleHours: 12.5 };

function ok<T>(data: T) {
  return HttpResponse.json({ code: 0, message: 'ok', data });
}

function metricCard(title: string): HTMLElement {
  return screen.getByText(title).closest('.MuiPaper-root') as HTMLElement;
}

it('指标卡按汇总接口显示记录数、合格率、未关闭缺陷与平均处理时长', async () => {
  seedSession('ENGINEER');
  renderAt(routes, '/dashboard');

  expect(await screen.findByRole('heading', { name: '质量看板' })).toBeInTheDocument();
  expect(await screen.findByText('120')).toBeInTheDocument();
  expect(within(metricCard('合格率')).getByText('91.7%')).toBeInTheDocument();
  expect(within(metricCard('未关闭缺陷')).getByText('3')).toBeInTheDocument();
  expect(within(metricCard('平均处理时长')).getByText('12.5 小时')).toBeInTheDocument();
});

it('平均处理时长为 null 时显示破折号而不是 0', async () => {
  seedSession('ENGINEER');
  server.use(http.get('/api/dashboard/summary', () => ok({ ...summary, passRate: 100, avgHandleHours: null })));
  renderAt(routes, '/dashboard');

  // 先等汇总落地：初始渲染的破折号只是「还没加载」，不能算数
  expect(await screen.findByText('100%')).toBeInTheDocument();
  const card = metricCard('平均处理时长');
  expect(within(card).getByText('—')).toBeInTheDocument();
  expect(within(card).queryByText('0')).not.toBeInTheDocument();
});

it('接口返回数据时趋势图与工序对比图渲染为图表', async () => {
  seedSession('INSPECTOR');
  renderAt(routes, '/dashboard');

  expect(await screen.findByRole('img', { name: '每日合格率趋势' })).toBeInTheDocument();
  expect(screen.getByRole('img', { name: '工序合格率对比' })).toBeInTheDocument();
});

it('everyChartShowsEmptyStateWithoutData', async () => {
  seedSession('ADMIN');
  // 全部八个数据端点都成功但无数据：八张图都应显示空状态，而不是空白坐标轴
  server.use(
    http.get('/api/dashboard/summary', () =>
      ok({ totalRecords: 0, passRecords: 0, passRate: 0, openDefects: 0, avgHandleHours: null }),
    ),
    http.get('/api/dashboard/pass-rate-trend', () => ok([])),
    http.get('/api/dashboard/pareto', () => ok([])),
    http.get('/api/dashboard/process-comparison', () => ok([])),
    http.get('/api/dashboard/control-chart', () =>
      ok({ itemName: '边框宽度', unit: 'mm', ucl: 3.5, lcl: 3, cl: 3.25, points: [] }),
    ),
    http.get('/api/dashboard/defect-grade', () => ok([])),
    http.get('/api/dashboard/batch-gauge', () => ok([])),
    http.get('/api/dashboard/inspector-workload', () => ok([])),
    http.get('/api/dashboard/defect-duration', () => ok([])),
  );
  renderAt(routes, '/dashboard');

  // 未选检验项时控制图卡片提示的是「还没选」，不是「没数据」，也不是「加载中」
  const controlCard = (await screen.findByText('参数控制图')).closest('.MuiPaper-root') as HTMLElement;
  expect(within(controlCard).getByText('请选择检验项')).toBeInTheDocument();
  expect(within(controlCard).queryByText('加载中…')).not.toBeInTheDocument();

  // 选中项目与检验项，让控制图也进入「已选但没数据」的状态
  await userEvent.click(screen.getByRole('combobox', { name: '项目' }));
  await userEvent.click(await screen.findByRole('option', { name: '青海格尔木 100MW' }));
  await userEvent.click(await screen.findByRole('combobox', { name: '检验项' }));
  await userEvent.click(await screen.findByRole('option', { name: '边框宽度' }));

  await waitFor(() => expect(screen.getAllByText('暂无数据')).toHaveLength(8));
  expect(screen.queryByRole('img')).not.toBeInTheDocument();
});

it('queryErrorShowsErrorNotEmptyState', async () => {
  seedSession('ENGINEER');
  server.use(
    http.get('/api/dashboard/pareto', () => HttpResponse.json({ code: 50000, message: '服务异常' }, { status: 500 })),
  );
  renderAt(routes, '/dashboard');

  const card = (await screen.findByText('缺陷帕累托')).closest('.MuiPaper-root') as HTMLElement;
  // 请求失败时卡片显示错误原因；告诉用户「暂无数据」会把故障伪装成空结果
  expect(await within(card).findByText('服务异常')).toBeInTheDocument();
  expect(within(card).queryByText('暂无数据')).not.toBeInTheDocument();
});

it('批次合格率表按批号列出合格率', async () => {
  seedSession('ENGINEER');
  renderAt(routes, '/dashboard');

  const table = await screen.findByRole('table', { name: '批次合格率汇总' });
  expect(await within(table).findByText('B-2026-002')).toBeInTheDocument();
  // 表头 + 两条批次
  expect(within(table).getAllByRole('row')).toHaveLength(3);
  expect(within(table).getByText('B-2026-001')).toBeInTheDocument();
  expect(within(table).getByText('80%')).toBeInTheDocument();
});

it('未选项目时检验项下拉禁用，控制图提示先选择检验项', async () => {
  seedSession('ENGINEER');
  renderAt(routes, '/dashboard');

  expect(screen.getByRole('combobox', { name: '检验项' })).toHaveAttribute('aria-disabled', 'true');
  const card = (await screen.findByText('参数控制图')).closest('.MuiPaper-root') as HTMLElement;
  expect(within(card).getByText('请选择检验项')).toBeInTheDocument();
});

it('筛选联动：先选项目才启用批次，选中的条件传给看板接口', async () => {
  seedSession('ENGINEER');
  const queries: URLSearchParams[] = [];
  server.use(
    http.get('/api/dashboard/summary', ({ request }) => {
      queries.push(new URL(request.url).searchParams);
      return ok(summary);
    }),
  );
  renderAt(routes, '/dashboard');
  await waitFor(() => expect(queries.length).toBeGreaterThan(0));
  // MUI 的 Select 渲染成 div，禁用态落在 aria-disabled 上
  expect(screen.getByRole('combobox', { name: '批次' })).toHaveAttribute('aria-disabled', 'true');

  await userEvent.click(screen.getByRole('combobox', { name: '项目' }));
  await userEvent.click(await screen.findByRole('option', { name: '青海格尔木 100MW' }));
  await waitFor(() => expect(queries.at(-1)?.get('projectId')).toBe('1'));
  await waitFor(() =>
    expect(screen.getByRole('combobox', { name: '批次' })).not.toHaveAttribute('aria-disabled', 'true'),
  );

  await userEvent.click(screen.getByRole('combobox', { name: '批次' }));
  await userEvent.click(await screen.findByRole('option', { name: 'B-2026-001' }));
  await waitFor(() => expect(queries.at(-1)?.get('batchId')).toBe('11'));

  await userEvent.type(screen.getByLabelText('开始日期'), '2026-11-01');
  await waitFor(() => expect(queries.at(-1)?.get('from')).toBe('2026-11-01'));
});

it('接口失败时显示后端原因', async () => {
  seedSession('ENGINEER');
  server.use(
    http.get('/api/dashboard/summary', () => HttpResponse.json({ code: 50000, message: '服务异常' }, { status: 500 })),
  );
  renderAt(routes, '/dashboard');

  expect(await screen.findByText('服务异常')).toBeInTheDocument();
});

it('管理员看到初始化演示数据按钮，其他人看不到', async () => {
  // FR-16 只对管理员开放：入口按角色隐藏，真正的拦截在后端接口上
  for (const role of ['ADMIN', 'ENGINEER', 'INSPECTOR'] as const) {
    seedSession(role);
    const { unmount } = renderAt(routes, '/dashboard');
    expect(await screen.findByRole('heading', { name: '质量看板' })).toBeInTheDocument();
    if (role === 'ADMIN') {
      expect(screen.getByRole('button', { name: '初始化演示数据' })).toBeInTheDocument();
    } else {
      expect(screen.queryByRole('button', { name: '初始化演示数据' })).not.toBeInTheDocument();
    }
    unmount();
  }
});

it('初始化成功后提示记录数并刷新看板', async () => {
  seedSession('ADMIN');
  const posts: string[] = [];
  const summaryQueries: URLSearchParams[] = [];
  server.use(
    http.post('/api/demo/seed', ({ request }) => {
      posts.push(new URL(request.url).pathname);
      return HttpResponse.json({
        code: 0,
        message: 'ok',
        data: { recordCount: 360, defectCount: 40, projectCount: 2 },
      });
    }),
    http.get('/api/dashboard/summary', ({ request }) => {
      summaryQueries.push(new URL(request.url).searchParams);
      return ok(summary);
    }),
  );
  renderAt(routes, '/dashboard');
  await waitFor(() => expect(summaryQueries.length).toBeGreaterThan(0));
  const before = summaryQueries.length;

  await userEvent.click(await screen.findByRole('button', { name: '初始化演示数据' }));
  // 弹窗文案说清会创建什么，确认按钮不是「确认删除」——这不是删除操作
  expect(await screen.findByRole('heading', { name: '初始化演示数据' })).toBeInTheDocument();
  expect(screen.getByText('将创建 2 个项目、6 个批次、360 条校验记录，仅用于演示')).toBeInTheDocument();
  expect(posts).toHaveLength(0);
  await userEvent.click(screen.getByRole('button', { name: '开始生成' }));

  await waitFor(() => expect(posts).toEqual(['/api/demo/seed']));
  expect(await screen.findByText('已生成 360 条校验记录')).toBeInTheDocument();
  // 生成后看板必须重拉，否则用户看到的还是旧数字
  await waitFor(() => expect(summaryQueries.length).toBeGreaterThan(before));
});

it('初始化失败时显示后端原因', async () => {
  seedSession('ADMIN');
  server.use(
    http.post('/api/demo/seed', () =>
      HttpResponse.json({ code: 40015, message: '演示数据已存在，请先清理' }, { status: 400 }),
    ),
  );
  renderAt(routes, '/dashboard');

  await userEvent.click(await screen.findByRole('button', { name: '初始化演示数据' }));
  await userEvent.click(await screen.findByRole('button', { name: '开始生成' }));

  // 后端原因原样展示，不在前端另造一套文案
  expect(await screen.findByText('演示数据已存在，请先清理')).toBeInTheDocument();
});
