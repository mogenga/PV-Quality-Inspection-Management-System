import { expect, it } from 'vitest';
import { formatStandardValue } from './standards';

const base = { unit: 'mm', lowerLimit: null, upperLimit: null, targetValue: null, tolerance: null };

it('按判定方式拼出可读的标准值', () => {
  expect(formatStandardValue({ ...base, judgeType: 'GT', lowerLimit: 100, unit: 'MΩ' })).toBe('> 100 MΩ');
  expect(formatStandardValue({ ...base, judgeType: 'LT', upperLimit: 3, unit: '%' })).toBe('< 3 %');
  expect(formatStandardValue({ ...base, judgeType: 'RANGE', lowerLimit: 3, upperLimit: 3.5 })).toBe('3 – 3.5 mm');
  expect(formatStandardValue({ ...base, judgeType: 'STD_TOL', targetValue: 710, tolerance: 5, unit: 'W' })).toBe(
    '710 ± 5 W',
  );
  expect(formatStandardValue({ ...base, judgeType: 'PASS_FAIL', unit: null })).toBe('人工判定');
});

it('去掉尾随零且无单位时不留空格', () => {
  expect(formatStandardValue({ ...base, judgeType: 'GT', lowerLimit: 93.0, unit: '%' })).toBe('> 93 %');
  expect(formatStandardValue({ ...base, judgeType: 'RANGE', lowerLimit: 3, upperLimit: 3.5, unit: null })).toBe(
    '3 – 3.5',
  );
});

it('必需数值缺失时返回占位符', () => {
  expect(formatStandardValue({ ...base, judgeType: 'RANGE', lowerLimit: 3 })).toBe('—');
});
