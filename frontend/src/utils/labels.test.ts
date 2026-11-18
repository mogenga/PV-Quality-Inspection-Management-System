import { describe, expect, it } from 'vitest';
import {
  BATCH_STATUS_LABELS,
  DEFECT_GRADE_LABELS,
  DEFECT_TYPE_LABELS,
  JUDGE_TYPE_LABELS,
  PROJECT_STATUS_LABELS,
  RESULT_LABELS,
  ROLE_LABELS,
  SCENE_LABELS,
} from './labels';

describe('ROLE_LABELS', () => {
  it('用界面文案翻译三种角色', () => {
    expect(ROLE_LABELS.ENGINEER).toBe('质量工程师');
    expect(ROLE_LABELS.INSPECTOR).toBe('检验员');
    expect(ROLE_LABELS.ADMIN).toBe('管理员');
  });
});

it('项目与批次状态有中文标签', () => {
  expect(PROJECT_STATUS_LABELS.ACTIVE).toBe('进行中');
  expect(PROJECT_STATUS_LABELS.CLOSED).toBe('已关闭');
  expect(BATCH_STATUS_LABELS.PLANNED).toBe('计划');
  expect(BATCH_STATUS_LABELS.PRODUCING).toBe('生产中');
  expect(BATCH_STATUS_LABELS.COMPLETED).toBe('已完成');
});

it('场景与判定方式有中文标签', () => {
  expect(SCENE_LABELS.IQC).toBe('来料检验');
  expect(SCENE_LABELS.PQC).toBe('过程检验');
  expect(SCENE_LABELS.OQC).toBe('出货检验');
  expect(JUDGE_TYPE_LABELS.STD_TOL).toBe('标准值±公差');
  expect(JUDGE_TYPE_LABELS.PASS_FAIL).toBe('人工判定');
});

it('判定结果与缺陷枚举有中文标签', () => {
  expect(RESULT_LABELS.PASS).toBe('合格');
  expect(RESULT_LABELS.FAIL).toBe('不合格');
  expect(DEFECT_GRADE_LABELS.CRITICAL).toBe('致命');
  expect(DEFECT_GRADE_LABELS.MAJOR).toBe('严重');
  expect(DEFECT_GRADE_LABELS.MINOR).toBe('轻微');
  expect(DEFECT_TYPE_LABELS.CRACK).toBe('隐裂');
  expect(DEFECT_TYPE_LABELS.OTHER).toBe('其他');
});
