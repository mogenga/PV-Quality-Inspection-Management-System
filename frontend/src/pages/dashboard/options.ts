import type { EChartsOption } from 'echarts';
import type { ProcessComparison, TrendPoint } from '../../api/types';
import { color } from '../../theme/muiTheme';

// 配色只取令牌：主系列 accent、网格线 line1、文字 fgMuted（brief Task 4）
const AXIS_LABEL = { color: color.fgMuted, fontSize: 12 } as const;
const GRID_LINE = { lineStyle: { color: color.line1 } } as const;
const AXIS_LINE = { lineStyle: { color: color.line1 } } as const;

/** CH-01 每日合格率趋势：日期 → x 轴，合格率 → y 轴（0–100）。 */
export function trendOption(points: TrendPoint[]): EChartsOption {
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 24, top: 24, bottom: 32 },
    xAxis: {
      type: 'category',
      data: points.map((point) => point.day),
      axisLabel: AXIS_LABEL,
      axisLine: AXIS_LINE,
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLabel: { ...AXIS_LABEL, formatter: '{value}%' },
      splitLine: GRID_LINE,
    },
    series: [
      {
        name: '合格率',
        type: 'line',
        data: points.map((point) => point.passRate),
        smooth: true,
        symbolSize: 6,
        itemStyle: { color: color.accent },
        lineStyle: { color: color.accent, width: 2 },
      },
    ],
  };
}

/** CH-03 工序合格率对比：横向条形图，各条右侧标注百分比。 */
export function processOption(rows: ProcessComparison[]): EChartsOption {
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 96, right: 56, top: 16, bottom: 32 },
    xAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLabel: { ...AXIS_LABEL, formatter: '{value}%' },
      splitLine: GRID_LINE,
    },
    yAxis: {
      type: 'category',
      data: rows.map((row) => row.processName),
      axisLabel: AXIS_LABEL,
      axisLine: AXIS_LINE,
      axisTick: { show: false },
    },
    series: [
      {
        name: '合格率',
        type: 'bar',
        data: rows.map((row) => ({ value: row.passRate })),
        itemStyle: { color: color.accent },
        label: { show: true, position: 'right', formatter: '{c}%', color: color.fgMuted },
      },
    ],
  };
}
