import { fireEvent, screen, waitFor, within } from '@testing-library/react';
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

// 演示数据播种前后的项目列表：播种前只有既有项目，播种后多一个 DEMO 项目
const baseProjectRecords = [
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
];

function projectPage(records: typeof baseProjectRecords) {
  return { records, total: records.length, page: 1, pageSize: 100 };
}

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

  // 未选项目时控制图卡片要指出下一步动作，不是含糊的「请选择检验项」，
  // 也不是「没数据」或「加载中」
  const controlCard = (await screen.findByText('参数控制图')).closest('.MuiPaper-root') as HTMLElement;
  expect(within(controlCard).getByText('请先在上方选择项目，再选择检验项')).toBeInTheDocument();
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

it('未选项目时检验项禁用，但界面说清为什么、怎么才能选', async () => {
  seedSession('ENGINEER');
  renderAt(routes, '/dashboard');

  // 检验项属于项目：禁用是正确行为，但理由必须写在界面上
  expect(screen.getByRole('combobox', { name: '检验项' })).toHaveAttribute('aria-disabled', 'true');
  expect(await screen.findByText(/批次与检验项需要先在上方选择「项目」/)).toBeInTheDocument();

  const card = (await screen.findByText('参数控制图')).closest('.MuiPaper-root') as HTMLElement;
  // 卡片文案要指向下一步动作，不是含糊的「请选择检验项」
  expect(within(card).getByText('请先在上方选择项目，再选择检验项')).toBeInTheDocument();
});

it('点击被禁用的检验项会弹出原因，而不是毫无反应', async () => {
  seedSession('ENGINEER');
  const { container } = renderAt(routes, '/dashboard');

  // MUI 的 disabled 控件吞掉鼠标事件，点上去没有任何反馈——
  // 外面那层可点击的壳负责把原因说出来。
  // jsdom 的 userEvent 会拒绝点击 pointer-events:none 的元素（真实浏览器里
  // 事件会冒泡到外层壳），所以这里直接点外层。
  await screen.findByRole('combobox', { name: '检验项' });
  const shell = container.querySelectorAll('[data-disabled-hint]');
  expect(shell.length).toBe(2); // 批次与检验项各一层
  fireEvent.click(shell[1]);

  expect(await screen.findByText('检验项属于项目，请先选择项目')).toBeInTheDocument();
});

it('选了项目后检验项可展开，控制图提示变成「请选择检验项」', async () => {
  seedSession('ENGINEER');
  renderAt(routes, '/dashboard');

  await userEvent.click(screen.getByRole('combobox', { name: '项目' }));
  await userEvent.click(await screen.findByRole('option', { name: '青海格尔木 100MW' }));

  await waitFor(() =>
    expect(screen.getByRole('combobox', { name: '检验项' })).not.toHaveAttribute('aria-disabled', 'true'),
  );
  expect(screen.queryByText(/批次与检验项需要先在上方选择「项目」/)).not.toBeInTheDocument();
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

it('初始化成功后四个查询键都重拉，演示项目立即可在下拉中选择', async () => {
  seedSession('ADMIN');
  const counts = { dashboard: 0, projects: 0, batches: 0, standards: 0 };
  let seeded = false;
  server.use(
    http.post('/api/demo/seed', () => {
      seeded = true;
      return HttpResponse.json({
        code: 0,
        message: 'ok',
        data: { recordCount: 360, defectCount: 40, projectCount: 2 },
      });
    }),
    http.get('/api/dashboard/summary', () => {
      counts.dashboard += 1;
      return ok(summary);
    }),
    http.get('/api/projects', () => {
      counts.projects += 1;
      // 播种后多出一个演示项目；下拉必须自动带上它，用户不用刷新页面
      const records = seeded
        ? [
            ...baseProjectRecords,
            {
              id: 501,
              projectCode: 'DEMO-2026-001',
              projectName: '青海格尔木 100MW 演示项目',
              productModel: '710W 双玻',
              customerName: '国电投',
              manufacturerName: '晶科能源',
              location: '青海格尔木',
              status: 'ACTIVE',
              remark: null,
              createdAt: '2026-11-19T09:00:00',
            },
          ]
        : baseProjectRecords;
      return ok(projectPage(records));
    }),
    http.get('/api/batches', () => {
      counts.batches += 1;
      return ok({ records: [], total: 0, page: 1, pageSize: 100 });
    }),
    http.get('/api/standards', () => {
      counts.standards += 1;
      return ok({ records: [], total: 0, page: 1, pageSize: 100 });
    }),
  );
  renderAt(routes, '/dashboard');
  await waitFor(() => expect(counts.projects).toBeGreaterThan(0));

  await userEvent.click(await screen.findByRole('button', { name: '初始化演示数据' }));
  await userEvent.click(await screen.findByRole('button', { name: '开始生成' }));
  await screen.findByText('已生成 360 条校验记录');

  // 只失效 ['dashboard'] 时，下面三个计数会停在 0（或首次值），演示项目不会进下拉
  await waitFor(() => expect(counts.dashboard).toBeGreaterThan(1));
  await waitFor(() => expect(counts.projects).toBeGreaterThan(1));
  // 弹窗关闭有过渡，未退场前它把页面其余部分标成 aria-hidden
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  await userEvent.click(screen.getByRole('combobox', { name: '项目' }));
  expect(await screen.findByRole('option', { name: '青海格尔木 100MW 演示项目' })).toBeInTheDocument();
});

it('初始化失败时显示后端原因', async () => {
  seedSession('ADMIN');
  server.use(
    http.post('/api/demo/seed', () =>
      HttpResponse.json(
        { code: 40015, message: '演示数据已存在，如需重新生成请先由管理员清理数据库' },
        { status: 400 },
      ),
    ),
  );
  renderAt(routes, '/dashboard');

  await userEvent.click(await screen.findByRole('button', { name: '初始化演示数据' }));
  await userEvent.click(await screen.findByRole('button', { name: '开始生成' }));

  // 后端原因原样展示，不在前端另造一套文案
  expect(await screen.findByText('演示数据已存在，如需重新生成请先由管理员清理数据库')).toBeInTheDocument();
});

it('场景与工序下拉把筛选条件传给看板接口', async () => {
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

  await userEvent.click(screen.getByRole('combobox', { name: '场景' }));
  await userEvent.click(await screen.findByRole('option', { name: '过程检验' }));
  await waitFor(() => expect(queries.at(-1)?.get('scene')).toBe('PQC'));

  // 工序选项来自工序对比接口的行，两个工序名都应在下拉里
  await userEvent.click(screen.getByRole('combobox', { name: '工序' }));
  await userEvent.click(await screen.findByRole('option', { name: '层压' }));
  await waitFor(() => expect(queries.at(-1)?.get('processName')).toBe('层压'));
  // 已选的场景不能因为再选工序而丢失
  expect(queries.at(-1)?.get('scene')).toBe('PQC');
});

it('筛到空结果的工序仍保留在下拉里，其他工序仍可选', async () => {
  seedSession('ENGINEER');
  const processQueries: URLSearchParams[] = [];
  server.use(
    http.get('/api/dashboard/process-comparison', ({ request }) => {
      const params = new URL(request.url).searchParams;
      processQueries.push(params);
      // 层压筛出空结果：工序选项必须来自不带 processName 的那次查询，
      // 否则下拉塌缩成「全部工序 + 层压」，甚至因空结果连层压都不在列表里
      const rows =
        params.get('processName') === '层压'
          ? []
          : [
              { processName: '层压', total: 10, passed: 8, passRate: 80 },
              { processName: '包装', total: 6, passed: 6, passRate: 100 },
            ];
      return ok(rows);
    }),
  );
  renderAt(routes, '/dashboard');
  await screen.findByRole('img', { name: '工序合格率对比' });

  await userEvent.click(screen.getByRole('combobox', { name: '工序' }));
  await userEvent.click(await screen.findByRole('option', { name: '层压' }));
  await waitFor(() => expect(processQueries.some((params) => params.get('processName') === '层压')).toBe(true));
  // 选项查询是一次不带 processName 的同口径请求，不受当前工序筛选影响
  await waitFor(() => expect(processQueries.some((params) => params.get('processName') === null)).toBe(true));

  // 图变空，但控件仍显示「层压」——筛选还在生效，不能显示成空白
  expect(await screen.findByText('暂无数据')).toBeInTheDocument();
  expect(screen.getByRole('combobox', { name: '工序' })).toHaveTextContent('层压');

  // 其他工序仍可选：选项不能塌缩成「全部工序 + 已选」
  await userEvent.click(screen.getByRole('combobox', { name: '工序' }));
  expect(await screen.findByRole('option', { name: '包装' })).toBeInTheDocument();
});

it('项目下拉加载失败时显示错误，而不是静默留一个「全部项目」', async () => {
  seedSession('ENGINEER');
  server.use(http.get('/api/projects', () => HttpResponse.json({ code: 50000, message: '服务异常' }, { status: 500 })));
  renderAt(routes, '/dashboard');

  // 批次/检验项会永久禁用，用户必须知道为什么
  expect(await screen.findByText('项目列表加载失败：服务异常')).toBeInTheDocument();
  expect(screen.getByRole('combobox', { name: '批次' })).toHaveAttribute('aria-disabled', 'true');
});

it('选中检验项后控制图标题带上检验项名', async () => {
  seedSession('ENGINEER');
  renderAt(routes, '/dashboard');

  // 未选时标题就是图名本身
  expect(await screen.findByText('参数控制图')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('combobox', { name: '项目' }));
  await userEvent.click(await screen.findByRole('option', { name: '青海格尔木 100MW' }));
  await userEvent.click(await screen.findByRole('combobox', { name: '检验项' }));
  await userEvent.click(await screen.findByRole('option', { name: '边框宽度' }));

  // 页面上有多张图，只写「参数控制图」看不出画的是哪个检验项
  expect(await screen.findByText('参数控制图 · 边框宽度')).toBeInTheDocument();
});
