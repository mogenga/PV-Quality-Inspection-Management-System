import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { expect, it } from 'vitest';
import type { DefectDetail } from '../../api/types';
import { routes } from '../../routes';
import { renderAt, seedSession } from '../../test/renderWithProviders';
import { server } from '../../test/server';

const baseDefect: DefectDetail = {
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
};

function defectRow(overrides: Partial<DefectDetail>): DefectDetail {
  return { ...baseDefect, ...overrides };
}

/** 覆盖缺陷列表接口：不同用例需要不同状态的行，默认处理器只给两行。 */
function mockDefects(rows: DefectDetail[], onQuery?: (params: URLSearchParams) => void) {
  server.use(
    http.get('/api/defects', ({ request }) => {
      onQuery?.(new URL(request.url).searchParams);
      return HttpResponse.json({
        code: 0,
        message: 'ok',
        data: { records: rows, total: rows.length, page: 1, pageSize: 10 },
      });
    }),
  );
}

it('列出缺陷并用中文显示等级与状态', async () => {
  seedSession('ENGINEER');
  renderAt(routes, '/defects');

  expect(await screen.findByRole('heading', { name: '缺陷处理' })).toBeInTheDocument();
  const row = (await screen.findByText('边框宽度')).closest('tr')!;
  expect(within(row).getByText('青海格尔木 100MW')).toBeInTheDocument();
  expect(within(row).getByText('B-2026-001')).toBeInTheDocument();
  expect(within(row).getByText('层压')).toBeInTheDocument();
  expect(within(row).getByText('隐裂')).toBeInTheDocument();
  expect(within(row).getByText('致命')).toBeInTheDocument();
  expect(within(row).getByText('待处理')).toBeInTheDocument();
  expect(within(row).getByText('李检验员')).toBeInTheDocument();
});

it('工程师按状态看到不同操作按钮', async () => {
  seedSession('ENGINEER');
  mockDefects([
    defectRow({ defectId: 1, itemName: '待处理项', defectStatus: 'OPEN' }),
    defectRow({ defectId: 2, itemName: '处理中项', defectStatus: 'PROCESSING', handlerName: '张工程师' }),
    defectRow({ defectId: 3, itemName: '待验证项', defectStatus: 'VERIFIED', handleNote: '已返工' }),
    defectRow({ defectId: 4, itemName: '已关闭项', defectStatus: 'CLOSED', handleNote: '返工完成' }),
  ]);
  renderAt(routes, '/defects');

  const openRow = (await screen.findByText('待处理项')).closest('tr')!;
  expect(within(openRow).getByRole('button', { name: /认领/ })).toBeInTheDocument();
  expect(within(openRow).queryByRole('button', { name: /提交整改/ })).not.toBeInTheDocument();

  const processingRow = screen.getByText('处理中项').closest('tr')!;
  expect(within(processingRow).getByRole('button', { name: /提交整改/ })).toBeInTheDocument();
  expect(within(processingRow).queryByRole('button', { name: /认领/ })).not.toBeInTheDocument();

  // 验证通过即关闭，不应另有一个「关闭」按钮
  const verifiedRow = screen.getByText('待验证项').closest('tr')!;
  expect(within(verifiedRow).getByRole('button', { name: /验证通过/ })).toBeInTheDocument();
  expect(within(verifiedRow).getByRole('button', { name: /验证不通过/ })).toBeInTheDocument();
  expect(within(verifiedRow).queryByRole('button', { name: /关闭/ })).not.toBeInTheDocument();

  const closedRow = screen.getByText('已关闭项').closest('tr')!;
  expect(within(closedRow).queryAllByRole('button')).toHaveLength(0);
});

it('检验员与管理员的缺陷列表没有操作按钮', async () => {
  for (const role of ['INSPECTOR', 'ADMIN'] as const) {
    seedSession(role);
    const { unmount } = renderAt(routes, '/defects');
    expect(await screen.findByText('边框宽度')).toBeInTheDocument();
    for (const name of [/认领/, /提交整改/, /验证通过/, /验证不通过/]) {
      expect(screen.queryByRole('button', { name })).not.toBeInTheDocument();
    }
    unmount();
  }
});

it('认领发出 process 请求并刷新列表', async () => {
  seedSession('ENGINEER');
  const queries: URLSearchParams[] = [];
  const posts: { url: string; body: unknown }[] = [];
  mockDefects([defectRow({ defectId: 1, defectStatus: 'OPEN' })], (params) => queries.push(params));
  server.use(
    http.post('/api/defects/:id/process', async ({ params, request }) => {
      posts.push({ url: `/api/defects/${params.id}/process`, body: await request.json() });
      return HttpResponse.json({ code: 0, message: 'ok', data: null });
    }),
  );
  renderAt(routes, '/defects');
  await waitFor(() => expect(queries.length).toBeGreaterThan(0));
  const before = queries.length;

  const row = (await screen.findByText('边框宽度')).closest('tr')!;
  await userEvent.click(within(row).getByRole('button', { name: /认领/ }));
  await userEvent.type(screen.getByLabelText('处理说明'), '已安排返工');
  await userEvent.click(screen.getByRole('button', { name: '确定' }));

  await waitFor(() => expect(posts).toHaveLength(1));
  expect(posts[0]).toEqual({ url: '/api/defects/1/process', body: { note: '已安排返工' } });
  // 成功后重拉列表，状态与操作按钮才不会停在旧值
  await waitFor(() => expect(queries.length).toBeGreaterThan(before));
});

it('验证通过即关闭，必须填处理说明，空说明不发请求', async () => {
  seedSession('ENGINEER');
  const posts: { url: string; body: unknown }[] = [];
  mockDefects([
    defectRow({
      defectId: 2,
      itemName: '外观检查',
      defectType: 'BUBBLE',
      defectGrade: 'MINOR',
      defectStatus: 'VERIFIED',
      handleNote: '已返工',
      handlerName: '张工程师',
    }),
  ]);
  server.use(
    http.post('/api/defects/:id/close', async ({ params, request }) => {
      posts.push({ url: `/api/defects/${params.id}/close`, body: await request.json() });
      return HttpResponse.json({ code: 0, message: 'ok', data: null });
    }),
  );
  renderAt(routes, '/defects');

  const row = (await screen.findByText('外观检查')).closest('tr')!;
  await userEvent.click(within(row).getByRole('button', { name: /验证通过/ }));
  await userEvent.click(screen.getByRole('button', { name: '确定' }));

  expect(await screen.findByText('请填写处理说明')).toBeInTheDocument();
  expect(posts).toHaveLength(0);

  await userEvent.type(screen.getByLabelText('处理说明'), '返工完成并复检');
  await userEvent.click(screen.getByRole('button', { name: '确定' }));

  await waitFor(() => expect(posts).toHaveLength(1));
  expect(posts[0]).toEqual({ url: '/api/defects/2/close', body: { note: '返工完成并复检' } });
});

it('验证不通过必须填退回原因', async () => {
  seedSession('ENGINEER');
  const posts: { url: string; body: unknown }[] = [];
  mockDefects([
    defectRow({
      defectId: 3,
      itemName: '外观检查',
      defectType: 'BUBBLE',
      defectGrade: 'MINOR',
      defectStatus: 'VERIFIED',
      handleNote: '已返工',
      handlerName: '张工程师',
    }),
  ]);
  server.use(
    http.post('/api/defects/:id/process', async ({ params, request }) => {
      posts.push({ url: `/api/defects/${params.id}/process`, body: await request.json() });
      return HttpResponse.json({ code: 0, message: 'ok', data: null });
    }),
  );
  renderAt(routes, '/defects');

  const row = (await screen.findByText('外观检查')).closest('tr')!;
  await userEvent.click(within(row).getByRole('button', { name: /验证不通过/ }));
  await userEvent.click(screen.getByRole('button', { name: '确定' }));

  expect(await screen.findByText('请填写处理说明')).toBeInTheDocument();
  expect(posts).toHaveLength(0);

  await userEvent.type(screen.getByLabelText('处理说明'), '边缘仍有气泡');
  await userEvent.click(screen.getByRole('button', { name: '确定' }));

  await waitFor(() => expect(posts).toHaveLength(1));
  expect(posts[0]).toEqual({ url: '/api/defects/3/process', body: { note: '边缘仍有气泡' } });
});

it('筛选把等级与状态传给接口', async () => {
  seedSession('ENGINEER');
  const queries: URLSearchParams[] = [];
  mockDefects([], (params) => queries.push(params));
  renderAt(routes, '/defects');
  await waitFor(() => expect(queries.length).toBeGreaterThan(0));

  await userEvent.click(screen.getByRole('combobox', { name: '等级' }));
  await userEvent.click(await screen.findByRole('option', { name: '致命' }));
  await userEvent.click(screen.getByRole('combobox', { name: '状态' }));
  await userEvent.click(await screen.findByRole('option', { name: '待处理' }));

  await waitFor(() => {
    expect(queries.at(-1)?.get('grade')).toBe('CRITICAL');
    expect(queries.at(-1)?.get('status')).toBe('OPEN');
  });
});
