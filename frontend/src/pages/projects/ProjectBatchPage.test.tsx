import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { expect, it } from 'vitest';
import { routes } from '../../routes';
import { renderAt, seedSession } from '../../test/renderWithProviders';
import { server } from '../../test/server';

it('工程师看到项目列表与写操作入口', async () => {
  seedSession('ENGINEER');
  renderAt(routes, '/projects');

  const row = (await screen.findByText('青海格尔木 100MW')).closest('tr')!;
  expect(within(row).getByText('PV-2026-001')).toBeInTheDocument();
  expect(within(row).getByText('进行中')).toBeInTheDocument();
  expect(within(row).getByText('国电投')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '新增项目' })).toBeInTheDocument();
  expect(screen.getByText('共 2 个项目')).toBeInTheDocument();
});

it('检验员只读：没有增删改，但仍可查看批次', async () => {
  seedSession('INSPECTOR');
  renderAt(routes, '/projects');

  const row = (await screen.findByText('青海格尔木 100MW')).closest('tr')!;
  expect(screen.queryByRole('button', { name: '新增项目' })).not.toBeInTheDocument();
  expect(within(row).queryByRole('button', { name: /编辑项目/ })).not.toBeInTheDocument();
  expect(within(row).queryByRole('button', { name: /删除项目/ })).not.toBeInTheDocument();
  expect(within(row).getByRole('button', { name: '查看批次 PV-2026-001' })).toBeInTheDocument();
});

it('点查看批次后按该项目加载批次', async () => {
  seedSession('ENGINEER');
  const queries: URLSearchParams[] = [];
  server.use(
    http.get('/api/batches', ({ request }) => {
      queries.push(new URL(request.url).searchParams);
      return HttpResponse.json({
        code: 0,
        message: 'ok',
        data: {
          records: [
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
          ],
          total: 1,
          page: 1,
          pageSize: 10,
        },
      });
    }),
  );
  renderAt(routes, '/projects');

  await userEvent.click(await screen.findByRole('button', { name: '查看批次 PV-2026-001' }));

  expect(await screen.findByText('B-2026-001')).toBeInTheDocument();
  expect(await screen.findByRole('heading', { name: '批次 · 青海格尔木 100MW' })).toBeInTheDocument();
  expect(queries.at(-1)?.get('projectId')).toBe('1');
});

it('新增项目提交所需字段并刷新列表', async () => {
  seedSession('ENGINEER');
  const bodies: unknown[] = [];
  server.use(
    http.post('/api/projects', async ({ request }) => {
      bodies.push(await request.json());
      return HttpResponse.json({
        code: 0,
        message: 'ok',
        data: {
          id: 3,
          projectCode: 'PV-2026-003',
          projectName: '内蒙古库布齐 200MW',
          productModel: null,
          customerName: null,
          manufacturerName: null,
          location: null,
          status: 'ACTIVE',
          remark: null,
          createdAt: '2026-11-17T09:00:00',
        },
      });
    }),
  );
  renderAt(routes, '/projects');

  await userEvent.click(await screen.findByRole('button', { name: '新增项目' }));
  await userEvent.type(screen.getByLabelText('项目编号'), 'PV-2026-003');
  await userEvent.type(screen.getByLabelText('项目名称'), '内蒙古库布齐 200MW');
  await userEvent.click(screen.getByRole('button', { name: '确定' }));

  await waitFor(() => expect(bodies).toHaveLength(1));
  expect(bodies[0]).toMatchObject({
    projectCode: 'PV-2026-003',
    projectName: '内蒙古库布齐 200MW',
    status: 'ACTIVE',
  });
});

it('关闭项目的批次区只读', async () => {
  seedSession('ENGINEER');
  server.use(
    http.get('/api/batches', () =>
      HttpResponse.json({ code: 0, message: 'ok', data: { records: [], total: 0, page: 1, pageSize: 10 } }),
    ),
  );
  renderAt(routes, '/projects');

  await userEvent.click(await screen.findByRole('button', { name: '查看批次 PV-2026-002' }));

  expect(await screen.findByText('项目已关闭，批次只读')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '新增批次' })).toBeDisabled();
});

it('新增批次携带所属项目', async () => {
  seedSession('ENGINEER');
  const bodies: unknown[] = [];
  server.use(
    http.post('/api/batches', async ({ request }) => {
      bodies.push(await request.json());
      return HttpResponse.json({
        code: 0,
        message: 'ok',
        data: {
          id: 12,
          projectId: 1,
          batchNo: 'B-2026-002',
          plannedQty: 800,
          startDate: null,
          endDate: null,
          status: 'PLANNED',
          remark: null,
          createdAt: '2026-11-17T09:00:00',
        },
      });
    }),
  );
  renderAt(routes, '/projects');

  await userEvent.click(await screen.findByRole('button', { name: '查看批次 PV-2026-001' }));
  await userEvent.click(await screen.findByRole('button', { name: '新增批次' }));
  await userEvent.type(screen.getByLabelText('批号'), 'B-2026-002');
  const qty = screen.getByLabelText('计划数量');
  await userEvent.clear(qty);
  await userEvent.type(qty, '800');
  await userEvent.click(screen.getByRole('button', { name: '确定' }));

  await waitFor(() => expect(bodies).toHaveLength(1));
  expect(bodies[0]).toMatchObject({ projectId: 1, batchNo: 'B-2026-002', plannedQty: 800, status: 'PLANNED' });
});

it('删除项目需二次确认', async () => {
  seedSession('ENGINEER');
  const deleted: string[] = [];
  server.use(
    http.delete('/api/projects/:id', ({ params }) => {
      deleted.push(String(params.id));
      return HttpResponse.json({ code: 0, message: 'ok', data: null });
    }),
  );
  renderAt(routes, '/projects');

  await userEvent.click(await screen.findByRole('button', { name: '删除项目 PV-2026-002' }));
  expect(await screen.findByRole('heading', { name: '删除项目' })).toBeInTheDocument();
  expect(deleted).toHaveLength(0); // 未确认前不发请求

  await userEvent.click(screen.getByRole('button', { name: '确认删除' }));
  await waitFor(() => expect(deleted).toEqual(['2']));
});
