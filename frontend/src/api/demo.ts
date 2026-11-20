import { apiClient } from './client';
import type { DemoSeedResult } from './types';

// 样例数据初始化（FR-16）：仅管理员可用，接口在服务端做角色校验
export function seedDemoData(): Promise<DemoSeedResult> {
  return apiClient.post<unknown, DemoSeedResult>('/demo/seed');
}
