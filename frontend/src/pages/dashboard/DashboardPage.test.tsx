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

it('无数据时图表显示「暂无数据」而不是空白坐标轴', async () => {
  seedSession('ADMIN');
  server.use(
    http.get('/api/dashboard/pass-rate-trend', () => ok([])),
    http.get('/api/dashboard/process-comparison', () => ok([])),
  );
  renderAt(routes, '/dashboard');

  expect(await screen.findAllByText('暂无数据')).toHaveLength(2);
  expect(screen.queryByRole('img')).not.toBeInTheDocument();
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
