import { describe, expect, it } from 'vitest';
import { ROLE_LABELS } from './labels';

describe('ROLE_LABELS', () => {
  it('用界面文案翻译三种角色', () => {
    expect(ROLE_LABELS.ENGINEER).toBe('质量工程师');
    expect(ROLE_LABELS.INSPECTOR).toBe('检验员');
    expect(ROLE_LABELS.ADMIN).toBe('管理员');
  });
});
