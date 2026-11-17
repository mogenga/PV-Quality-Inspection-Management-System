import axios from 'axios';
import type { AxiosError } from 'axios';
import { useAuthStore } from '../stores/authStore';

interface ApiErrorBody {
  code?: number;
  message?: string;
}

export class ApiError extends Error {
  readonly code: number;

  constructor(code: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
  }
}

export const apiClient = axios.create({ baseURL: '/api', timeout: 10000 });

apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

apiClient.interceptors.response.use(
  (res) => res.data.data,
  (error: AxiosError<ApiErrorBody>) => {
    const url: string = error.config?.url ?? '';
    // 登录接口的 401 是"账号密码错误"，由表单展示，不当作会话失效
    if (error.response?.status === 401 && !url.includes('/auth/login')) {
      // 只清登录态；RequireAuth 观察到 token 为空后声明式重定向
      useAuthStore.getState().clear();
    }
    return Promise.reject(
      new ApiError(error.response?.data?.code ?? 0, error.response?.data?.message ?? '网络异常，请稍后重试'),
    );
  },
);
