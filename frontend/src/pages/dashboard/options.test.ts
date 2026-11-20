import { expect, it } from 'vitest';
import { fontFamily } from '../../theme/tokens';
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

it('工序对比轴起点跟随数据：高合格率时退化成签样的 85，低合格率时不裁掉柱子', () => {
  const axisMinOf = (passRate: number) =>
    (
      processOption([{ processName: '层压', total: 10, passed: 9, passRate }]) as unknown as {
        xAxis: { min: number };
      }
    ).xAxis.min;

  // P6 签样：数据都在 90 以上时，0 起点的轴上 91% 与 98% 视觉无差别，故从 85 起
  expect(axisMinOf(92)).toBe(85);
  // 写死 85 会把低于 85% 的工序整个裁掉（截图里六个工序全空就是这么来的）
  expect(axisMinOf(50)).toBe(45);
  expect(axisMinOf(66.7)).toBe(60);
  // 下限兜底：再低也不能出现负的轴起点
  expect(axisMinOf(0)).toBe(0);

  const option = processOption([{ processName: '包装', total: 2, passed: 1, passRate: 50 }]) as unknown as {
    textStyle: { fontFamily: string };
    xAxis: { min: number; max: number };
    yAxis: { type: string };
    series: { type: string; data: { value: number }[] }[];
  };
  expect(option.yAxis.type).toBe('category');
  expect(option.xAxis.max).toBe(100);
  expect(option.series[0].type).toBe('bar');
  expect(option.series[0].data[0].value).toBe(50);
  expect(option.textStyle.fontFamily).toBe(fontFamily);
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

it('批次仪表盘是半圆弧，取选中批次的合格率', () => {
  const option = gaugeOption({ batchId: 1, batchNo: 'B-1', total: 10, passed: 8, passRate: 80 }) as unknown as {
    textStyle: { fontFamily: string };
    series: { startAngle: number; endAngle: number; radius: string; data: { value: number }[] }[];
  };
  expect(option.series[0].data[0].value).toBe(80);
  // P6 签样：180° → 0° 的半圆弧，不是整圆
  expect(option.series[0].startAngle).toBe(180);
  expect(option.series[0].endAngle).toBe(0);
  expect(option.series[0].radius).toBe('92%');
  expect(option.textStyle.fontFamily).toBe(fontFamily);
});

it('每张图的 textStyle.fontFamily 都来自导出的 fontFamily 常量', () => {
  const options = [
    trendOption([{ day: '2026-11-18', total: 2, passed: 1, passRate: 50 }]),
    processOption([{ processName: '层压', total: 2, passed: 1, passRate: 50 }]),
    paretoOption([{ defectType: 'CRACK', count: 1, cumulativePercent: 100 }]),
    gradeOption([{ scene: 'PQC', defectGrade: 'MINOR', count: 1 }]),
    controlOption({
      itemName: '边框宽度',
      unit: 'mm',
      ucl: 3.5,
      lcl: 3,
      cl: 3.25,
      points: [{ inspectTime: '2026-11-18T10:00:00', measuredValue: 3.2 }],
    }),
    workloadOption([{ inspectorId: 3, inspectorName: '李检验员', count: 1 }]),
    durationOption([{ defectGrade: 'MINOR', avgHours: 6.8 }]),
    gaugeOption({ batchId: 1, batchNo: 'B-1', total: 2, passed: 1, passRate: 50 }),
  ];
  for (const option of options) {
    expect((option as { textStyle?: { fontFamily?: string } }).textStyle?.fontFamily).toBe(fontFamily);
  }
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

it('控制图右侧留白够放 UCL/LCL 标签，不再把标签裁成一个字母', () => {
  const option = controlOption({
    itemName: '层压温度',
    unit: '℃',
    ucl: 155,
    lcl: 145,
    cl: 150,
    points: [{ inspectTime: '2026-11-18T10:00:00', measuredValue: 150 }],
  }) as unknown as { grid: { right: number } };
  // markLine 标签画在线右端之外：right 太小会把「UCL」裁成「UC」
  expect(option.grid.right).toBeGreaterThanOrEqual(48);
});
