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
