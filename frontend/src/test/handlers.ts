import { http, HttpResponse } from 'msw';

export const handlers = [
  http.post('/api/auth/login', async ({ request }) => {
    const body = (await request.json()) as { username: string; password: string };
    if (body.password !== 'Engineer@123') {
      return HttpResponse.json({ code: 40100, message: '用户名或密码错误' }, { status: 401 });
    }
    return HttpResponse.json({
      code: 0,
      message: 'ok',
      data: {
        token: 'fake-jwt',
        user: {
          id: 2,
          username: 'engineer',
          displayName: '张工程师',
          role: 'ENGINEER',
          isActive: true,
          createdAt: '2026-11-01T09:00:00',
        },
      },
    });
  }),
  http.post('/api/auth/logout', () => HttpResponse.json({ code: 0, message: 'ok', data: null })),
  http.get('/api/users', () =>
    HttpResponse.json({
      code: 0,
      message: 'ok',
      data: {
        records: [
          {
            id: 1,
            username: 'admin',
            displayName: '系统管理员',
            role: 'ADMIN',
            isActive: true,
            createdAt: '2026-11-01T09:00:00',
          },
          {
            id: 2,
            username: 'engineer',
            displayName: '张工程师',
            role: 'ENGINEER',
            isActive: true,
            createdAt: '2026-11-01T09:00:00',
          },
        ],
        total: 2,
        page: 1,
        pageSize: 10,
      },
    }),
  ),
];
