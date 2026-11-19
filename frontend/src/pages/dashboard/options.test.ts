import { expect, it } from 'vitest';
import {
  controlOption,
  durationOption,
  gaugeOption,
  gradeOption,
  paretoOption,
  processOption,
  trendOption,
  workloadOption,
} from './options';

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

it('帕累托累计线终点为 100', () => {
  const option = paretoOption([
    { defectType: 'CRACK', count: 2, cumulativePercent: 66.7 },
    { defectType: 'BUBBLE', count: 1, cumulativePercent: 100 },
  ]) as unknown as {
    series: { type: string; yAxisIndex?: number; data: (number | { value: number })[] }[];
    yAxis: { max?: number }[];
  };
  // 柱状 series 用 count，折线 series 用 cumulativePercent 且挂右侧 y 轴
  expect(option.series[0].type).toBe('bar');
  expect(option.series[1].type).toBe('line');
  expect(option.series[1].yAxisIndex).toBe(1);
  expect(option.series[1].data.at(-1)).toBe(100);
  expect(option.yAxis[1].max).toBe(100);
});

it('控制图三条控制线取自传入的上下限', () => {
  const option = controlOption({
    itemName: '边框宽度',
    unit: 'mm',
    ucl: 3.5,
    lcl: 3,
    cl: 3.25,
    points: [{ inspectTime: '2026-11-18T10:00:00', measuredValue: 3.2 }],
  }) as unknown as {
    series: { markLine: { data: { yAxis: number }[] } }[];
  };
  const marks = option.series[0].markLine.data;
  expect(marks.map((m) => m.yAxis)).toEqual([3.5, 3.25, 3]); // UCL / CL / LCL
});

it('等级构成是堆叠柱状图且按场景分组', () => {
  const option = gradeOption([
    { scene: 'PQC', defectGrade: 'CRITICAL', count: 1 },
    { scene: 'OQC', defectGrade: 'MINOR', count: 2 },
  ]) as unknown as {
    series: { stack?: string }[];
    xAxis: { data: string[] };
  };
  expect(option.series.every((s) => s.stack === 'grade')).toBe(true);
  expect(option.xAxis.data).toEqual(['过程检验', '出货检验']);
});

it('批次仪表盘取选中批次的合格率', () => {
  const option = gaugeOption({ batchId: 1, batchNo: 'B-1', total: 10, passed: 8, passRate: 80 }) as unknown as {
    series: { data: { value: number }[] }[];
  };
  expect(option.series[0].data[0].value).toBe(80);
});

it('检验员工作量按姓名映射 x 轴', () => {
  const option = workloadOption([
    { inspectorId: 3, inspectorName: '李检验员', count: 12 },
    { inspectorId: 4, inspectorName: '王检验员', count: 7 },
  ]) as unknown as {
    xAxis: { data: string[] };
    series: { type: string; data: number[] }[];
  };
  expect(option.xAxis.data).toEqual(['李检验员', '王检验员']);
  expect(option.series[0].data).toEqual([12, 7]);
});

it('处理时长按等级中文名出柱，avgHours 为 null 的等级不画', () => {
  const option = durationOption([
    { defectGrade: 'CRITICAL', avgHours: 38.5 },
    { defectGrade: 'MAJOR', avgHours: null },
  ]) as unknown as {
    xAxis: { data: string[] };
    series: { data: (number | null)[] }[];
  };
  expect(option.xAxis.data).toEqual(['致命']);
  expect(option.series[0].data).toEqual([38.5]);
});
