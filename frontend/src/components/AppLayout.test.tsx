import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, it } from 'vitest';
import { routes } from '../routes';
import { useAuthStore } from '../stores/authStore';
import { renderAt, seedSession } from '../test/renderWithProviders';
import { color } from '../theme/muiTheme';
import type { Role } from '../utils/labels';

// ListItemButton 渲染为 <a>，断言用 link 而不是 menuitem
const menuCases: [Role, string[], string[]][] = [
  ['INSPECTOR', ['质量看板', '项目与批次', '校验录入', '缺陷处理'], ['校验标准', '用户管理']],
  ['ENGINEER', ['质量看板', '项目与批次', '校验标准', '校验录入', '缺陷处理'], ['用户管理']],
  ['ADMIN', ['质量看板', '项目与批次', '校验标准', '缺陷处理', '用户管理'], ['校验录入']],
];

it.each(menuCases)('%s 只能看到属于自己的菜单项', async (role, visible, hidden) => {
  seedSession(role);
  renderAt(routes, '/dashboard');

  for (const label of visible) {
    expect(await screen.findByRole('link', { name: label })).toBeInTheDocument();
  }
  for (const label of hidden) {
    expect(screen.queryByRole('link', { name: label })).not.toBeInTheDocument();
  }
});

it('检验员直接访问 /users 会被送回自己的首页', async () => {
  seedSession('INSPECTOR');
  renderAt(routes, '/users');

  expect(await screen.findByRole('heading', { name: '校验录入' })).toBeInTheDocument();
});

it('侧栏纯白、页面浅灰，两者不同色且侧栏固定 240px', async () => {
  seedSession('ADMIN');
  renderAt(routes, '/dashboard');

  const link = await screen.findByRole('link', { name: '质量看板' });
  const drawerPaper = link.closest('.MuiDrawer-paper') as HTMLElement;

  expect(drawerPaper).toHaveStyle({ backgroundColor: color.surface1, width: '240px' });
  // CssBaseline 把 body 铺成 palette.background.default
  expect(document.body).toHaveStyle({ backgroundColor: color.bg });
  expect(color.surface1).not.toBe(color.bg);
});

it('顶栏显示姓名与角色，退出登录后回到登录页', async () => {
  seedSession('ENGINEER');
  renderAt(routes, '/dashboard');

  expect(await screen.findByText('测试管理员')).toBeInTheDocument();
  expect(screen.getByText('质量工程师')).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: '退出登录' }));

  expect(await screen.findByLabelText('用户名')).toBeInTheDocument();
  expect(useAuthStore.getState().token).toBeNull();
});
