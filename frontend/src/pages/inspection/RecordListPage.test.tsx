import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, expect, it, vi } from 'vitest';
import { routes } from '../../routes';
import { renderAt, seedSession } from '../../test/renderWithProviders';
import { server } from '../../test/server';
import { useAuthStore } from '../../stores/authStore';

// 导出用例 mock 了 URL.createObjectURL 与 <a>.click，每个用例后恢复，避免泄漏到其他文件
afterEach(() => {
  vi.restoreAllMocks();
});

const recordRow = {
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
  remark: null,
  createdAt: '2026-11-17T10:00:00',
};

it('记录表同时显示标准值与带单位的实测值', async () => {
  seedSession('INSPECTOR');
  renderAt(routes, '/records');

  const row = (await screen.findByText('边框宽度')).closest('tr')!;
  // 标准值取记录里的快照列，不是当前标准
  expect(within(row).getByText('3 – 3.5 mm')).toBeInTheDocument();
  // 实测值带单位，不能只是裸数字
  expect(within(row).getByText('3.2 mm')).toBeInTheDocument();
  expect(within(row).getByText('合格')).toBeInTheDocument();
});

it('文字型记录显示结论而不是空白实测值', async () => {
  seedSession('INSPECTOR');
  server.use(
    http.get('/api/inspection/records', () =>
      HttpResponse.json({
        code: 0,
        message: 'ok',
        data: {
          records: [{ ...recordRow, id: 102, measuredValue: null, textValue: 'FAIL', result: 'FAIL', stdUnit: null }],
          total: 1,
          page: 1,
          pageSize: 10,
        },
      }),
    ),
  );
  renderAt(routes, '/records');

  const row = (await screen.findByText('边框宽度')).closest('tr')!;
  // 文字型没有实测值，实测值列应直接显示结论；判定列也有一个「不合格」，两者都存在
  expect(within(row).getAllByText('不合格')).toHaveLength(2);
});

it('按结果筛选把 result 传给接口', async () => {
  seedSession('INSPECTOR');
  const queries: URLSearchParams[] = [];
  server.use(
    http.get('/api/inspection/records', ({ request }) => {
      queries.push(new URL(request.url).searchParams);
      return HttpResponse.json({ code: 0, message: 'ok', data: { records: [], total: 0, page: 1, pageSize: 10 } });
    }),
  );
  renderAt(routes, '/records');
  await waitFor(() => expect(queries.length).toBeGreaterThan(0));

  await userEvent.click(screen.getByRole('combobox', { name: '结果' }));
  await userEvent.click(await screen.findByRole('option', { name: '不合格' }));

  await waitFor(() => expect(queries.at(-1)?.get('result')).toBe('FAIL'));
});

it('日期区间把 from 与 to 传给接口', async () => {
  seedSession('INSPECTOR');
  const queries: URLSearchParams[] = [];
  server.use(
    http.get('/api/inspection/records', ({ request }) => {
      queries.push(new URL(request.url).searchParams);
      return HttpResponse.json({ code: 0, message: 'ok', data: { records: [], total: 0, page: 1, pageSize: 10 } });
    }),
  );
  renderAt(routes, '/records');
  await waitFor(() => expect(queries.length).toBeGreaterThan(0));

  await userEvent.type(screen.getByLabelText('开始日期'), '2026-11-01');
  await waitFor(() => expect(queries.at(-1)?.get('from')).toBe('2026-11-01'));
});

it('空结果给出提示而不是空白表格', async () => {
  seedSession('INSPECTOR');
  server.use(
    http.get('/api/inspection/records', () =>
      HttpResponse.json({ code: 0, message: 'ok', data: { records: [], total: 0, page: 1, pageSize: 10 } }),
    ),
  );
  renderAt(routes, '/records');

  expect(await screen.findByText('没有符合条件的校验记录')).toBeInTheDocument();
});

it('管理员看到作废按钮，作废必须填原因', async () => {
  seedSession('ADMIN');
  const posts: { url: string; body: unknown }[] = [];
  server.use(
    http.post('/api/inspection/records/:id/void', async ({ params, request }) => {
      posts.push({ url: `/api/inspection/records/${params.id}/void`, body: await request.json() });
      return HttpResponse.json({ code: 0, message: 'ok', data: null });
    }),
  );
  renderAt(routes, '/records');

  const row = (await screen.findByText('边框宽度')).closest('tr')!;
  await userEvent.click(within(row).getByRole('button', { name: '作废 #101' }));

  expect(await screen.findByRole('heading', { name: '作废记录 #101' })).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '确认作废' }));

  // 原因为空时前端拦下，不发请求
  expect(await screen.findByText('请填写作废原因')).toBeInTheDocument();
  expect(posts).toHaveLength(0);

  await userEvent.type(screen.getByLabelText('作废原因'), '录入错批次');
  await userEvent.click(screen.getByRole('button', { name: '确认作废' }));

  await waitFor(() => expect(posts).toHaveLength(1));
  expect(posts[0]).toEqual({ url: '/api/inspection/records/101/void', body: { reason: '录入错批次' } });
});

it('检验员与工程师看不到作废按钮', async () => {
  for (const role of ['INSPECTOR', 'ENGINEER'] as const) {
    seedSession(role);
    const { unmount } = renderAt(routes, '/records');
    expect(await screen.findByText('边框宽度')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /作废/ })).not.toBeInTheDocument();
    unmount();
  }
});

it('作废行默认不显示，打开含作废开关后显示并标注原因', async () => {
  seedSession('ADMIN');
  const queries: URLSearchParams[] = [];
  server.use(
    http.get('/api/inspection/records', ({ request }) => {
      const params = new URL(request.url).searchParams;
      queries.push(params);
      const records =
        params.get('includeVoid') === 'true' ? [{ ...recordRow, isVoid: true, voidReason: '录入错批次' }] : [recordRow];
      return HttpResponse.json({
        code: 0,
        message: 'ok',
        data: { records, total: records.length, page: 1, pageSize: 10 },
      });
    }),
  );
  renderAt(routes, '/records');

  expect(await screen.findByText('边框宽度')).toBeInTheDocument();
  // 默认查询不带 includeVoid，后端默认排除作废行
  expect(queries.at(-1)?.has('includeVoid')).toBe(false);
  expect(screen.queryByText('已作废')).not.toBeInTheDocument();

  await userEvent.click(screen.getByRole('switch', { name: '含作废' }));

  await waitFor(() => expect(queries.at(-1)?.get('includeVoid')).toBe('true'));
  expect(await screen.findByText('已作废')).toBeInTheDocument();
  expect(screen.getByText('录入错批次')).toBeInTheDocument();
});

it('工程师看到导出按钮，导出带上当前筛选', async () => {
  seedSession('ENGINEER');
  const createObjectURL = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock');
  const revokeObjectURL = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
  const linkClick = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
  const exportQueries: URLSearchParams[] = [];
  const exportAuth: (string | null)[] = [];
  server.use(
    http.get('/api/export/records.csv', ({ request }) => {
      exportQueries.push(new URL(request.url).searchParams);
      // 导出不能用 <a href>：原生导航带不上 Bearer 令牌
      exportAuth.push(request.headers.get('Authorization'));
      return new HttpResponse('批次,结果\n11,合格', {
        status: 200,
        headers: { 'Content-Type': 'text/csv;charset=UTF-8' },
      });
    }),
  );
  renderAt(routes, '/records');
  await screen.findByText('边框宽度');

  await userEvent.click(screen.getByRole('combobox', { name: '结果' }));
  await userEvent.click(await screen.findByRole('option', { name: '不合格' }));
  await userEvent.type(screen.getByLabelText('开始日期'), '2026-11-01');
  await userEvent.click(screen.getByRole('button', { name: '导出 CSV' }));

  await waitFor(() => expect(exportQueries).toHaveLength(1));
  expect(exportQueries[0].get('result')).toBe('FAIL');
  expect(exportQueries[0].get('from')).toBe('2026-11-01');
  expect(exportAuth[0]).toBe('Bearer seeded-token');
  await waitFor(() => expect(createObjectURL).toHaveBeenCalledTimes(1));
  expect(linkClick).toHaveBeenCalledTimes(1);
  expect(revokeObjectURL).toHaveBeenCalledWith('blob:mock');
});

it('管理员与检验员看不到导出按钮', async () => {
  for (const role of ['ADMIN', 'INSPECTOR'] as const) {
    seedSession(role);
    const { unmount } = renderAt(routes, '/records');
    expect(await screen.findByText('边框宽度')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '导出 CSV' })).not.toBeInTheDocument();
    unmount();
  }
});

it('导出文件名带服务器同款日期后缀', async () => {
  seedSession('ENGINEER');
  vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock');
  vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
  const linkClick = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
  server.use(
    http.get(
      '/api/export/records.csv',
      () =>
        new HttpResponse('批次,结果\n11,合格', {
          status: 200,
          headers: { 'Content-Type': 'text/csv;charset=UTF-8' },
        }),
    ),
  );
  renderAt(routes, '/records');
  await screen.findByText('边框宽度');

  await userEvent.click(screen.getByRole('button', { name: '导出 CSV' }));

  await waitFor(() => expect(linkClick).toHaveBeenCalledTimes(1));
  const now = new Date();
  const pad = (value: number) => String(value).padStart(2, '0');
  const expected = `records-${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}.csv`;
  expect(linkClick.mock.instances[0]).toHaveProperty('download', expected);
});

it('导出遇到 401 时清空登录态', async () => {
  seedSession('ENGINEER');
  server.use(
    http.get('/api/export/records.csv', () => HttpResponse.json({ code: 40101, message: '未登录' }, { status: 401 })),
  );
  renderAt(routes, '/records');
  await screen.findByText('边框宽度');
  expect(useAuthStore.getState().token).toBe('seeded-token');

  await userEvent.click(screen.getByRole('button', { name: '导出 CSV' }));

  // 与 axios 拦截器同口径：401 即会话失效，RequireAuth 观察到 token 为空后重定向
  await waitFor(() => expect(useAuthStore.getState().token).toBeNull());
});

it('导出失败时显示后端的中文原因', async () => {
  seedSession('ENGINEER');
  server.use(
    http.get('/api/export/records.csv', () =>
      HttpResponse.json({ code: 40014, message: '导出数据超过 10000 条上限，请缩小筛选范围' }, { status: 400 }),
    ),
  );
  renderAt(routes, '/records');
  await screen.findByText('边框宽度');

  await userEvent.click(screen.getByRole('button', { name: '导出 CSV' }));

  expect(await screen.findByText('导出数据超过 10000 条上限，请缩小筛选范围')).toBeInTheDocument();
  expect(screen.queryByText('网络异常，请稍后重试')).not.toBeInTheDocument();
});
