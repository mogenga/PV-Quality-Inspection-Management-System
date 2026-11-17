import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { expect, it } from 'vitest';
import type { UserSummary } from '../../api/types';
import { routes } from '../../routes';
import { renderAt, seedSession } from '../../test/renderWithProviders';
import { server } from '../../test/server';

const adminRow: UserSummary = {
  id: 1,
  username: 'admin',
  displayName: '系统管理员',
  role: 'ADMIN',
  isActive: true,
  createdAt: '2026-11-01T09:00:00',
};

const newRow: UserSummary = {
  id: 3,
  username: 'newuser',
  displayName: '新同事',
  role: 'INSPECTOR',
  isActive: true,
  createdAt: '2026-11-16T10:00:00',
};

it('渲染接口返回的用户行并显示角色中文', async () => {
  seedSession('ADMIN');
  const { container } = renderAt(routes, '/users');

  const adminRowEl = (await screen.findByText('系统管理员')).closest('tr')!;
  expect(within(adminRowEl).getByText('管理员')).toBeInTheDocument();
  expect(within(adminRowEl).getByText('admin')).toBeInTheDocument();
  expect(within(adminRowEl).getByText('启用')).toBeInTheDocument();
  expect(screen.queryByText(/\$2[aby]\$/)).not.toBeInTheDocument(); // 绝不显示密码哈希
  expect(container.textContent).not.toMatch(/\$2[aby]\$/); // 整个 DOM 文本都不含 BCrypt 哈希
});

it('概览条显示账号总数与当前显示范围', async () => {
  seedSession('ADMIN');
  renderAt(routes, '/users');

  expect(await screen.findByText('共 2 个账号')).toBeInTheDocument();
  expect(screen.getByText('当前显示第 1–2 条')).toBeInTheDocument();
});

it('接口多返回的密码哈希字段也不进入 DOM', async () => {
  seedSession('ADMIN');
  server.use(
    http.get('/api/users', () =>
      HttpResponse.json({
        code: 0,
        message: 'ok',
        data: {
          // 后端契约不该带这个字段；即便带了，页面也只挑白名单列渲染
          records: [{ ...adminRow, passwordHash: '$2a$10$abcdefghijklmnopqrstuv' }],
          total: 1,
          page: 1,
          pageSize: 10,
        },
      }),
    ),
  );
  const { container } = renderAt(routes, '/users');

  await screen.findByText('系统管理员');
  expect(container.textContent).not.toMatch(/\$2[aby]\$/);
});

it('新增用户成功后重新拉取列表', async () => {
  seedSession('ADMIN');
  let created = false;
  server.use(
    http.post('/api/users', () => {
      created = true;
      return HttpResponse.json({ code: 0, message: 'ok', data: newRow });
    }),
    http.get('/api/users', () =>
      HttpResponse.json({
        code: 0,
        message: 'ok',
        data: created
          ? { records: [newRow, adminRow], total: 2, page: 1, pageSize: 10 }
          : { records: [adminRow], total: 1, page: 1, pageSize: 10 },
      }),
    ),
  );
  renderAt(routes, '/users');

  await userEvent.click(await screen.findByRole('button', { name: '新增用户' }));
  await userEvent.type(screen.getByLabelText('登录名'), 'newuser');
  await userEvent.type(screen.getByLabelText('初始密码'), 'Newuser@123');
  await userEvent.type(screen.getByLabelText('显示姓名'), '新同事');
  await userEvent.click(screen.getByRole('button', { name: '确定' }));

  const newRowEl = (await screen.findByText('新同事')).closest('tr')!;
  expect(within(newRowEl).getByText('检验员')).toBeInTheDocument();
});

it('编辑与重置密码按行 id 提交并刷新列表', async () => {
  seedSession('ADMIN');
  const putBodies: unknown[] = [];
  const patchBodies: unknown[] = [];
  let edited = false;
  server.use(
    http.put('/api/users/:id', async ({ params, request }) => {
      putBodies.push({ id: params.id, body: await request.json() });
      edited = true;
      return HttpResponse.json({
        code: 0,
        message: 'ok',
        data: { ...adminRow, displayName: '管理员甲', isActive: false },
      });
    }),
    http.patch('/api/users/:id/password', async ({ params, request }) => {
      patchBodies.push({ id: params.id, body: await request.json() });
      return HttpResponse.json({ code: 0, message: 'ok', data: null });
    }),
    http.get('/api/users', () =>
      HttpResponse.json({
        code: 0,
        message: 'ok',
        data: {
          records: [edited ? { ...adminRow, displayName: '管理员甲', isActive: false } : adminRow],
          total: 1,
          page: 1,
          pageSize: 10,
        },
      }),
    ),
  );
  renderAt(routes, '/users');

  const adminRowEl = (await screen.findByText('系统管理员')).closest('tr')!;
  await userEvent.click(within(adminRowEl).getByRole('button', { name: '编辑' }));
  const displayNameInput = screen.getByLabelText('显示姓名');
  await userEvent.clear(displayNameInput);
  await userEvent.type(displayNameInput, '管理员甲');
  await userEvent.click(screen.getByRole('combobox', { name: '状态' }));
  await userEvent.click(await screen.findByRole('option', { name: '停用' }));
  await userEvent.click(screen.getByRole('button', { name: '确定' }));

  await waitFor(() =>
    expect(putBodies).toEqual([{ id: '1', body: { displayName: '管理员甲', role: 'ADMIN', isActive: false } }]),
  );
  const refreshedRow = (await screen.findByText('管理员甲')).closest('tr')!;
  expect(within(refreshedRow).getByText('停用')).toBeInTheDocument();

  await userEvent.click(within(refreshedRow).getByRole('button', { name: '重置密码' }));
  await userEvent.type(screen.getByLabelText('新密码'), 'Reset@12345');
  await userEvent.click(screen.getByRole('button', { name: '确定' }));

  await waitFor(() => expect(patchBodies).toEqual([{ id: '1', body: { newPassword: 'Reset@12345' } }]));
});

it('筛选与分页会改变请求参数', async () => {
  seedSession('ADMIN');
  const queries: URLSearchParams[] = [];
  server.use(
    http.get('/api/users', ({ request }) => {
      queries.push(new URL(request.url).searchParams);
      return HttpResponse.json({
        code: 0,
        message: 'ok',
        data: { records: [adminRow], total: 30, page: 1, pageSize: 10 },
      });
    }),
  );
  renderAt(routes, '/users');

  await screen.findByText('系统管理员');
  expect(queries.at(-1)?.get('page')).toBe('1');
  expect(queries.at(-1)?.get('pageSize')).toBe('10');

  await userEvent.click(screen.getByRole('button', { name: '下一页' }));
  await waitFor(() => expect(queries.at(-1)?.get('page')).toBe('2'));

  // 改筛选条件回到第一页，避免停在第 2 页看到空列表
  await userEvent.type(screen.getByLabelText('关键字'), '张工');
  await waitFor(() => {
    expect(queries.at(-1)?.get('keyword')).toBe('张工');
    expect(queries.at(-1)?.get('page')).toBe('1');
  });

  await userEvent.click(screen.getByRole('combobox', { name: '角色' }));
  await userEvent.click(await screen.findByRole('option', { name: '质量工程师' }));
  await waitFor(() => expect(queries.at(-1)?.get('role')).toBe('ENGINEER'));

  await userEvent.click(screen.getByRole('combobox', { name: '状态' }));
  await userEvent.click(await screen.findByRole('option', { name: '停用' }));
  await waitFor(() => expect(queries.at(-1)?.get('isActive')).toBe('false'));

  await userEvent.click(screen.getByRole('button', { name: '下一页' }));
  await waitFor(() => expect(queries.at(-1)?.get('page')).toBe('2'));

  await userEvent.click(screen.getByRole('combobox', { name: /每页行数/ }));
  await userEvent.click(await screen.findByRole('option', { name: '20' }));
  await waitFor(() => {
    expect(queries.at(-1)?.get('pageSize')).toBe('20');
    expect(queries.at(-1)?.get('page')).toBe('1'); // 改每页行数后回到第一页
  });
});
