import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, it } from 'vitest';
import { renderAt } from '../test/renderWithProviders';
import { routes } from '../routes';

it('工程师登录后进入项目与批次首页', async () => {
  renderAt(routes, '/login');
  await userEvent.type(screen.getByLabelText('用户名'), 'engineer');
  await userEvent.type(screen.getByLabelText('密码'), 'Engineer@123');
  await userEvent.click(screen.getByRole('button', { name: '登录' }));

  expect(await screen.findByRole('heading', { name: '项目与批次' })).toBeInTheDocument();
});

it('密码错误时提示且停留在登录页', async () => {
  renderAt(routes, '/login');
  await userEvent.type(screen.getByLabelText('用户名'), 'engineer');
  await userEvent.type(screen.getByLabelText('密码'), 'bad-password');
  await userEvent.click(screen.getByRole('button', { name: '登录' }));

  expect(await screen.findByText('用户名或密码错误')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '登录' })).toBeInTheDocument();
});

it('未登录访问受保护页面被重定向到登录页', async () => {
  renderAt(routes, '/users');
  expect(await screen.findByLabelText('用户名')).toBeInTheDocument();
});

it('品牌区展示一句话价值与三条核心特性', () => {
  renderAt(routes, '/login');

  expect(screen.getByText('标准集中配置 · 判定服务端执行 · 异常闭环留痕')).toBeInTheDocument();
  expect(screen.getByText('校验标准可配置，新增检验项不改代码')).toBeInTheDocument();
  expect(screen.getByText('判定由服务端执行，不信任前端结果')).toBeInTheDocument();
  expect(screen.getByText('不合格自动进缺陷闭环，状态严格流转')).toBeInTheDocument();
});
