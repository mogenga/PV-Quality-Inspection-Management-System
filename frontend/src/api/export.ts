import { ApiError } from './client';
import type { RecordQuery } from './inspection';
import { useAuthStore } from '../stores/authStore';

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
    // 错误响应是 JSON 信封，读出来才能拿到后端中文原因
    const body = (await res.json().catch(() => null)) as { code?: number; message?: string } | null;
    throw new ApiError(body?.code ?? 0, body?.message ?? '导出失败，请稍后重试');
  }
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = 'records.csv';
  link.click();
  URL.revokeObjectURL(url);
}
