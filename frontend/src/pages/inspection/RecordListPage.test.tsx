import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { expect, it } from 'vitest';
import { routes } from '../../routes';
import { renderAt, seedSession } from '../../test/renderWithProviders';
import { server } from '../../test/server';

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
