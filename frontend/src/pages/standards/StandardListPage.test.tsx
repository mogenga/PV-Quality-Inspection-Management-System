import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { expect, it } from 'vitest';
import { routes } from '../../routes';
import { renderAt, seedSession } from '../../test/renderWithProviders';
import { server } from '../../test/server';

it('工程师看到标准列表与新增入口', async () => {
  seedSession('ENGINEER');
  renderAt(routes, '/standards');

  const row = (await screen.findByText('组件功率')).closest('tr')!;
  expect(within(row).getByText('PMAX-01')).toBeInTheDocument();
  expect(within(row).getByText('710 ± 5 W')).toBeInTheDocument();
  expect(within(row).getByText('标准值±公差')).toBeInTheDocument();
  expect(within(row).getByText('来料检验')).toBeInTheDocument();
  expect(within(row).getByText('启用')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '新增标准' })).toBeInTheDocument();
});

it('管理员只读没有编辑与启停按钮', async () => {
  seedSession('ADMIN');
  renderAt(routes, '/standards');

  const row = (await screen.findByText('组件功率')).closest('tr')!;
  expect(within(row).queryByRole('button', { name: /编辑标准/ })).not.toBeInTheDocument();
  expect(within(row).queryByRole('button', { name: /停用标准|启用标准/ })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '新增标准' })).not.toBeInTheDocument();
});

it('没有项目时提示先去创建项目', async () => {
  seedSession('ENGINEER');
  server.use(
    http.get('/api/projects', () =>
      HttpResponse.json({ code: 0, message: 'ok', data: { records: [], total: 0, page: 1, pageSize: 100 } }),
    ),
  );
  renderAt(routes, '/standards');

  expect(await screen.findByText('还没有项目，请先在『项目与批次』中创建项目')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '新增标准' })).not.toBeInTheDocument();
  expect(screen.queryByRole('table')).not.toBeInTheDocument();
});

it('切换项目后按新项目查询', async () => {
  seedSession('ENGINEER');
  const queries: URLSearchParams[] = [];
  server.use(
    http.get('/api/standards', ({ request }) => {
      queries.push(new URL(request.url).searchParams);
      return HttpResponse.json({ code: 0, message: 'ok', data: { records: [], total: 0, page: 1, pageSize: 10 } });
    }),
  );
  renderAt(routes, '/standards');

  await waitFor(() => expect(queries.at(-1)?.get('projectId')).toBe('1'));

  await userEvent.click(screen.getByRole('combobox', { name: '项目' }));
  await userEvent.click(await screen.findByRole('option', { name: '宁夏中卫 50MW' }));

  await waitFor(() => expect(queries.at(-1)?.get('projectId')).toBe('2'));
});

it('新增区间标准的提交体只含上下限', async () => {
  seedSession('ENGINEER');
  const bodies: unknown[] = [];
  server.use(
    http.post('/api/standards', async ({ request }) => {
      bodies.push(await request.json());
      return HttpResponse.json({
        code: 0,
        message: 'ok',
        data: {
          id: 4,
          projectId: 1,
          scene: 'PQC',
          processName: '层压',
          itemCode: 'RANGE-01',
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
          sortOrder: 0,
          remark: null,
          createdAt: '2026-11-18T09:00:00',
        },
      });
    }),
  );
  renderAt(routes, '/standards');

  await userEvent.click(await screen.findByRole('button', { name: '新增标准' }));
  await userEvent.type(screen.getByLabelText('检验项编码'), 'RANGE-01');
  await userEvent.type(screen.getByLabelText('检验项名称'), '边框宽度');
  await userEvent.click(screen.getByRole('combobox', { name: '场景' }));
  await userEvent.click(await screen.findByRole('option', { name: '过程检验' }));
  await userEvent.type(screen.getByLabelText('工序'), '层压');
  // 先按标准值±公差填过数值，再改成区间：旧值不能残留在提交体里
  await userEvent.click(screen.getByRole('combobox', { name: '判定方式' }));
  await userEvent.click(await screen.findByRole('option', { name: '标准值±公差' }));
  await userEvent.type(screen.getByLabelText('标准值'), '800');
  await userEvent.type(screen.getByLabelText('公差'), '10');
  await userEvent.click(screen.getByRole('combobox', { name: '判定方式' }));
  await userEvent.click(await screen.findByRole('option', { name: '区间' }));
  await userEvent.type(screen.getByLabelText('下限'), '3');
  await userEvent.type(screen.getByLabelText('上限'), '3.5');
  await userEvent.type(screen.getByLabelText('单位'), 'mm');
  await userEvent.click(screen.getByRole('button', { name: '确定' }));

  await waitFor(() => expect(bodies).toHaveLength(1));
  // 判定方式只用到上下限：标准值/公差必须显式为 null，不能因为切换前填过就漏出去
  expect(bodies[0]).toEqual({
    projectId: 1,
    scene: 'PQC',
    processName: '层压',
    itemCode: 'RANGE-01',
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
    sortOrder: 0,
    remark: null,
  });
});

it('停用标准发出 PATCH 且只带 isEnabled', async () => {
  seedSession('ENGINEER');
  const patches: unknown[] = [];
  server.use(
    http.patch('/api/standards/:id/enabled', async ({ params, request }) => {
      patches.push({ id: params.id, body: await request.json() });
      return HttpResponse.json({
        code: 0,
        message: 'ok',
        data: {
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
          isEnabled: false,
          sortOrder: 1,
          remark: null,
          createdAt: '2026-11-03T09:00:00',
        },
      });
    }),
  );
  renderAt(routes, '/standards');

  await userEvent.click(await screen.findByRole('button', { name: '停用标准 PMAX-01' }));

  await waitFor(() => expect(patches).toEqual([{ id: '1', body: { isEnabled: false } }]));
});

it('后端拒绝下限不小于上限时在弹窗内显示提示', async () => {
  seedSession('ENGINEER');
  server.use(
    http.put('/api/standards/:id', () =>
      HttpResponse.json({ code: 40007, message: '下限必须小于上限' }, { status: 400 }),
    ),
  );
  renderAt(routes, '/standards');

  await userEvent.click(await screen.findByRole('button', { name: '编辑标准 FRAME-01' }));
  const lowerInput = screen.getByLabelText('下限');
  await userEvent.clear(lowerInput);
  await userEvent.type(lowerInput, '5');
  await userEvent.click(screen.getByRole('button', { name: '确定' }));

  expect(await within(screen.getByRole('dialog')).findByText('下限必须小于上限')).toBeInTheDocument();
});

it('场景筛选把 scene 传给接口', async () => {
  seedSession('ENGINEER');
  const queries: URLSearchParams[] = [];
  server.use(
    http.get('/api/standards', ({ request }) => {
      queries.push(new URL(request.url).searchParams);
      return HttpResponse.json({ code: 0, message: 'ok', data: { records: [], total: 0, page: 1, pageSize: 10 } });
    }),
  );
  renderAt(routes, '/standards');

  await screen.findByRole('combobox', { name: '场景' });
  await userEvent.click(screen.getByRole('combobox', { name: '场景' }));
  await userEvent.click(await screen.findByRole('option', { name: '过程检验' }));

  await waitFor(() => expect(queries.at(-1)?.get('scene')).toBe('PQC'));
});
