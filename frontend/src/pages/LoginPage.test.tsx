import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, it } from 'vitest';
import { renderAt } from '../test/renderWithProviders';
import { routes } from '../routes';

it('密码框可切换显示与隐藏', async () => {
  renderAt(routes, '/login');
  const input = screen.getByLabelText('密码');
  await userEvent.type(input, 'Engineer@123');
  expect(input).toHaveAttribute('type', 'password');

  await userEvent.click(screen.getByLabelText('显示密码'));
  expect(input).toHaveAttribute('type', 'text');
  expect(input).toHaveValue('Engineer@123');

  await userEvent.click(screen.getByLabelText('隐藏密码'));
  expect(input).toHaveAttribute('type', 'password');
  expect(input).toHaveValue('Engineer@123');
});

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

it('展示系统名与一句话价值', () => {
  renderAt(routes, '/login');

  expect(screen.getByRole('heading', { name: '光伏组件校验数字化平台' })).toBeInTheDocument();
  expect(screen.getByText('标准集中配置 · 判定服务端执行 · 异常闭环留痕')).toBeInTheDocument();
  expect(screen.getByText('内部系统 · 仅限授权人员使用')).toBeInTheDocument();
});
