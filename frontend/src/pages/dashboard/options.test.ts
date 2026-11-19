import { expect, it } from 'vitest';
import { processOption, trendOption } from './options';

it('趋势图把日期映射到 x 轴、合格率映射到 y 轴', () => {
  const option = trendOption([
    { day: '2026-11-17', total: 10, passed: 9, passRate: 90 },
    { day: '2026-11-18', total: 4, passed: 3, passRate: 75 },
  ]) as unknown as {
    xAxis: { data: string[] };
    yAxis: { min: number; max: number };
    series: { data: number[] }[];
  };

  expect(option.xAxis.data).toEqual(['2026-11-17', '2026-11-18']);
  expect(option.series[0].data).toEqual([90, 75]);
  expect(option.yAxis.min).toBe(0);
  expect(option.yAxis.max).toBe(100);
});

it('工序对比是横向条形图并显示百分比', () => {
  const option = processOption([{ processName: '层压', total: 10, passed: 8, passRate: 80 }]) as unknown as {
    xAxis: { max: number };
    yAxis: { type: string };
    series: { type: string; data: { value: number }[] }[];
  };

  expect(option.yAxis.type).toBe('category');
  expect(option.xAxis.max).toBe(100);
  expect(option.series[0].type).toBe('bar');
  expect(option.series[0].data[0].value).toBe(80);
});
