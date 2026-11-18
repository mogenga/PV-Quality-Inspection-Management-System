import { ApiError } from './client';
import type { RecordQuery } from './inspection';
import { useAuthStore } from '../stores/authStore';

/** 与后端 Content-Disposition 的 records-YYYYMMDD.csv 同名，连续导出不会互相覆盖。 */
function datedFilename(): string {
  const now = new Date();
  const pad = (value: number) => String(value).padStart(2, '0');
  return `records-${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}.csv`;
}

/**
 * 导出走原生 fetch：apiClient 的响应拦截器统一解包 res.data.data，二进制响应没有这层信封；
 * 而 <a href> 导航带不上 Authorization 头（认证是 Bearer 不是 Cookie）。
 */
export async function downloadRecordsCsv(
  params: Omit<RecordQuery, 'page' | 'pageSize' | 'includeVoid'>,
): Promise<void> {
  const query = new URLSearchParams();
  // 只拼有值的参数，与列表查询同口径
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') query.append(key, String(value));
  }
  const token = useAuthStore.getState().token;
  const res = await fetch(`/api/export/records.csv?${query}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!res.ok) {
    // 与 axios 拦截器同口径：会话失效时清登录态，RequireAuth 声明式重定向
    if (res.status === 401) useAuthStore.getState().clear();
    // 错误响应是 JSON 信封，读出来才能拿到后端中文原因
    const body = (await res.json().catch(() => null)) as { code?: number; message?: string } | null;
    throw new ApiError(body?.code ?? 0, body?.message ?? '导出失败，请稍后重试');
  }
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = datedFilename();
  link.click();
  URL.revokeObjectURL(url);
}
