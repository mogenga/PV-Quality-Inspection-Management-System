import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { expect, it } from 'vitest';
import { routes } from '../../routes';
import { renderAt, seedSession } from '../../test/renderWithProviders';
import { server } from '../../test/server';

const STORAGE_KEY = 'pvqims.inspection.filters';

const emptyPage = { code: 0, message: 'ok', data: { records: [], total: 0, page: 1, pageSize: 10 } };

// 与 handlers.ts 的默认待检项同形：覆盖 /tasks 只为了数请求次数，行内容保持一致
const defaultTasks = [
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

/** 判定预览接口没有默认处理器：不 mock 就说明页面根本不该调它。 */
function mockJudge(result: 'PASS' | 'FAIL', bodies: unknown[] = []) {
  server.use(
    http.post('/api/inspection/judge', async ({ request }) => {
      bodies.push(await request.json());
      return HttpResponse.json({ code: 0, message: 'ok', data: { result } });
    }),
  );
}

async function chooseProjectAndBatch() {
  await userEvent.click(await screen.findByRole('combobox', { name: '项目' }));
  await userEvent.click(await screen.findByRole('option', { name: '青海格尔木 100MW' }));
  await userEvent.click(screen.getByRole('combobox', { name: '批次' }));
  await userEvent.click(await screen.findByRole('option', { name: 'B-2026-001' }));
}

async function findTaskRow(itemName: string): Promise<HTMLElement> {
  const table = await screen.findByRole('table', { name: '待检项' });
  return (await within(table).findByText(itemName)).closest('tr') as HTMLElement;
}

it('未选批次时提示先选择项目和批次', async () => {
  seedSession('INSPECTOR');
  renderAt(routes, '/inspection');

  expect(await screen.findByText('请选择项目和批次')).toBeInTheDocument();
  expect(screen.queryByRole('table', { name: '待检项' })).not.toBeInTheDocument();
});

it('选择项目与批次后列出待检项与上次实测值', async () => {
  seedSession('INSPECTOR');
  renderAt(routes, '/inspection');
  await chooseProjectAndBatch();

  const row = await findTaskRow('边框宽度');
  expect(within(row).getByText('3 – 3.5 mm')).toBeInTheDocument();
  expect(within(row).getByText('3.2')).toBeInTheDocument();
  expect(within(row).getByText('合格')).toBeInTheDocument();
  // 浏览器对 type=number 的默认 step 是 1，3.25 这类小数会被原生校验拦下
  expect(within(row).getByLabelText('实测值 FRAME-01')).toHaveAttribute('step', 'any');
});

it('数值输入失焦后显示服务端判定结果', async () => {
  seedSession('INSPECTOR');
  const judgeBodies: unknown[] = [];
  mockJudge('FAIL', judgeBodies);
  renderAt(routes, '/inspection');
  await chooseProjectAndBatch();

  const row = await findTaskRow('边框宽度');
  await userEvent.type(within(row).getByLabelText('实测值 FRAME-01'), '3.9');
  await userEvent.tab();

  expect(await within(row).findByLabelText('判定结果 不合格')).toHaveTextContent('不合格');
  expect(judgeBodies).toEqual([{ standardId: 2, measuredValue: 3.9 }]);
});

it('判定不合格时展开缺陷类型与等级', async () => {
  seedSession('INSPECTOR');
  mockJudge('FAIL');
  renderAt(routes, '/inspection');
  await chooseProjectAndBatch();

  const row = await findTaskRow('边框宽度');
  // 判定合格时没有缺陷区，避免用户以为缺陷信息是必填的
  expect(screen.queryByRole('combobox', { name: '缺陷类型' })).not.toBeInTheDocument();
  await userEvent.type(within(row).getByLabelText('实测值 FRAME-01'), '3.9');
  await userEvent.tab();

  expect(await screen.findByRole('combobox', { name: '缺陷类型' })).toBeInTheDocument();
  expect(screen.getByRole('combobox', { name: '缺陷等级' })).toBeInTheDocument();
});

it('缺陷信息为空时保存被拦下并提示', async () => {
  seedSession('INSPECTOR');
  mockJudge('FAIL');
  const recordBodies: unknown[] = [];
  server.use(
    http.post('/api/inspection/records', async ({ request }) => {
      recordBodies.push(await request.json());
      return HttpResponse.json({ code: 0, message: 'ok', data: {} });
    }),
  );
  renderAt(routes, '/inspection');
  await chooseProjectAndBatch();

  const row = await findTaskRow('边框宽度');
  await userEvent.type(within(row).getByLabelText('实测值 FRAME-01'), '3.9');
  await userEvent.tab();
  await screen.findByRole('combobox', { name: '缺陷类型' });
  await userEvent.click(within(row).getByRole('button', { name: '保存 FRAME-01' }));

  expect(await screen.findByText('请填写缺陷类型、缺陷等级')).toBeInTheDocument();
  expect(recordBodies).toHaveLength(0);
});

it('保存成功发出完整请求体并刷新记录', async () => {
  seedSession('INSPECTOR');
  mockJudge('FAIL');
  const recordBodies: unknown[] = [];
  const recordQueries: URLSearchParams[] = [];
  const taskQueries: URLSearchParams[] = [];
  server.use(
    http.get('/api/inspection/tasks', ({ request }) => {
      taskQueries.push(new URL(request.url).searchParams);
      return HttpResponse.json({ code: 0, message: 'ok', data: defaultTasks });
    }),
    http.post('/api/inspection/records', async ({ request }) => {
      recordBodies.push(await request.json());
      return HttpResponse.json({
        code: 0,
        message: 'ok',
        data: {
          id: 102,
          projectId: 1,
          batchId: 11,
          standardId: 2,
          scene: 'PQC',
          processName: '层压',
          itemName: '边框宽度',
          serialNo: null,
          inspectorId: 3,
          inspectTime: '2026-11-18T10:00:00',
          measuredValue: 3.9,
          textValue: null,
          result: 'FAIL',
          stdLower: 3,
          stdUpper: 3.5,
          stdTarget: null,
          stdTolerance: null,
          stdUnit: 'mm',
          isVoid: false,
          remark: null,
          createdAt: '2026-11-18T10:00:00',
        },
      });
    }),
    http.get('/api/inspection/records', ({ request }) => {
      recordQueries.push(new URL(request.url).searchParams);
      return HttpResponse.json(emptyPage);
    }),
  );
  renderAt(routes, '/inspection');
  await chooseProjectAndBatch();
  await waitFor(() => expect(recordQueries.length).toBeGreaterThan(0));
  const before = recordQueries.length;
  const beforeTasks = taskQueries.length;

  const row = await findTaskRow('边框宽度');
  await userEvent.type(within(row).getByLabelText('实测值 FRAME-01'), '3.9');
  await userEvent.tab();
  await screen.findByRole('combobox', { name: '缺陷类型' });
  await userEvent.click(screen.getByRole('combobox', { name: '缺陷类型' }));
  await userEvent.click(await screen.findByRole('option', { name: '隐裂' }));
  await userEvent.click(screen.getByRole('combobox', { name: '缺陷等级' }));
  await userEvent.click(await screen.findByRole('option', { name: '严重' }));
  await userEvent.click(within(row).getByRole('button', { name: '保存 FRAME-01' }));

  await waitFor(() => expect(recordBodies).toHaveLength(1));
  expect(recordBodies[0]).toEqual({
    batchId: 11,
    standardId: 2,
    measuredValue: 3.9,
    textValue: null,
    serialNo: null,
    defectType: 'CRACK',
    defectGrade: 'MAJOR',
  });
  // 保存成功后记录区重新查询，新记录才会出现
  await waitFor(() => expect(recordQueries.length).toBeGreaterThan(before));
  // 待检项也要重拉，「上次实测」才会更新
  await waitFor(() => expect(taskQueries.length).toBeGreaterThan(beforeTasks));
});

it('文字型检验项用合格与不合格按钮选择', async () => {
  seedSession('INSPECTOR');
  const judgeBodies: unknown[] = [];
  mockJudge('PASS', judgeBodies);
  renderAt(routes, '/inspection');
  await chooseProjectAndBatch();

  const row = await findTaskRow('外观检查');
  await userEvent.click(within(row).getByRole('button', { name: '不合格' }));

  expect(within(row).getByLabelText('判定结果 不合格')).toHaveTextContent('不合格');
  // PASS_FAIL 的判定就是所选值，不需要请求判定接口
  expect(judgeBodies).toHaveLength(0);
});

it('记录区按结果筛选重新查询', async () => {
  seedSession('INSPECTOR');
  const queries: URLSearchParams[] = [];
  server.use(
    http.get('/api/inspection/records', ({ request }) => {
      queries.push(new URL(request.url).searchParams);
      return HttpResponse.json(emptyPage);
    }),
  );
  renderAt(routes, '/inspection');
  await waitFor(() => expect(queries.length).toBeGreaterThan(0));

  await userEvent.click(screen.getByRole('combobox', { name: '结果' }));
  await userEvent.click(await screen.findByRole('option', { name: '不合格' }));

  await waitFor(() => expect(queries.at(-1)?.get('result')).toBe('FAIL'));
});

it('工序下拉由待检项去重得到，选中后按工序重新拉任务', async () => {
  seedSession('INSPECTOR');
  const taskQueries: URLSearchParams[] = [];
  server.use(
    http.get('/api/inspection/tasks', ({ request }) => {
      const params = new URL(request.url).searchParams;
      taskQueries.push(params);
      const rows =
        params.get('processName') === '包装'
          ? [
              {
                standardId: 4,
                scene: 'OQC',
                processName: '包装',
                itemCode: 'PACK-01',
                itemName: '包装外观',
                unit: null,
                valueType: 'TEXT',
                judgeType: 'PASS_FAIL',
                lowerLimit: null,
                upperLimit: null,
                targetValue: null,
                tolerance: null,
                sortOrder: 4,
                lastMeasuredValue: null,
                lastResult: null,
                lastInspectTime: null,
              },
            ]
          : [
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
                standardId: 4,
                scene: 'OQC',
                processName: '包装',
                itemCode: 'PACK-01',
                itemName: '包装外观',
                unit: null,
                valueType: 'TEXT',
                judgeType: 'PASS_FAIL',
                lowerLimit: null,
                upperLimit: null,
                targetValue: null,
                tolerance: null,
                sortOrder: 4,
                lastMeasuredValue: null,
                lastResult: null,
                lastInspectTime: null,
              },
            ];
      return HttpResponse.json({ code: 0, message: 'ok', data: rows });
    }),
  );
  renderAt(routes, '/inspection');
  await chooseProjectAndBatch();
  await waitFor(() => expect(taskQueries.at(-1)?.get('processName')).toBeNull());

  await userEvent.click(screen.getByRole('combobox', { name: '工序' }));
  // 选项来自待检项，去重后每个工序只出现一次
  expect(await screen.findByRole('option', { name: '层压' })).toBeInTheDocument();
  await userEvent.click(screen.getByRole('option', { name: '包装' }));

  await waitFor(() => expect(taskQueries.at(-1)?.get('processName')).toBe('包装'));
  expect(await screen.findByText('包装外观')).toBeInTheDocument();
});

it('页面按最近一次使用的项目批次恢复选择', async () => {
  seedSession('INSPECTOR');
  const taskQueries: URLSearchParams[] = [];
  server.use(
    http.get('/api/inspection/tasks', ({ request }) => {
      taskQueries.push(new URL(request.url).searchParams);
      return HttpResponse.json({ code: 0, message: 'ok', data: [] });
    }),
  );
  localStorage.setItem(STORAGE_KEY, JSON.stringify({ projectId: 1, batchId: 11, scene: 'PQC' }));
  renderAt(routes, '/inspection');

  await waitFor(() => expect(taskQueries.at(-1)?.get('batchId')).toBe('11'));
  const params = taskQueries.at(-1)!;
  expect(params.get('projectId')).toBe('1');
  expect(params.get('scene')).toBe('PQC');
});

it('判定请求未返回时保存禁用，避免用旧判定落库', async () => {
  seedSession('INSPECTOR');
  let releaseJudge!: () => void;
  const judgePending = new Promise<void>((resolve) => {
    releaseJudge = resolve;
  });
  server.use(
    http.post('/api/inspection/judge', async () => {
      await judgePending;
      return HttpResponse.json({ code: 0, message: 'ok', data: { result: 'FAIL' } });
    }),
  );
  renderAt(routes, '/inspection');
  await chooseProjectAndBatch();

  const row = await findTaskRow('边框宽度');
  const saveButton = within(row).getByRole('button', { name: '保存 FRAME-01' });
  expect(saveButton).toBeEnabled();

  await userEvent.type(within(row).getByLabelText('实测值 FRAME-01'), '3.9');
  await userEvent.tab();

  // 判定还在路上时保存被禁用：否则两个快速失焦的响应可能乱序，保存会用先返回的旧判定
  await waitFor(() => expect(saveButton).toBeDisabled());

  releaseJudge();
  await waitFor(() => expect(saveButton).toBeEnabled());
  expect(await within(row).findByLabelText('判定结果 不合格')).toBeInTheDocument();
});
