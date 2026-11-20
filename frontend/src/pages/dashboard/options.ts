import type { EChartsOption } from 'echarts';
import type {
  BatchPassRate,
  ControlChart,
  DefectDuration,
  DefectGrade,
  GradeSceneCount,
  InspectorWorkload,
  ParetoItem,
  ProcessComparison,
  Scene,
  TrendPoint,
} from '../../api/types';
import { color, fontFamily } from '../../theme/muiTheme';
import { DEFECT_GRADE_LABELS, DEFECT_TYPE_LABELS, SCENE_LABELS } from '../../utils/labels';

// 配色只取令牌：主系列 accent、网格线 line1、文字 fgMuted（brief Task 4）
const AXIS_LABEL = { color: color.fgMuted, fontSize: 12 } as const;
const GRID_LINE = { lineStyle: { color: color.line1 } } as const;
const AXIS_LINE = { lineStyle: { color: color.line1 } } as const;
const LEGEND = { textStyle: { color: color.fgMuted } } as const;
// 字体与 MUI 主题同源（P6 签样要求 textStyle.fontFamily 用导出的 fontFamily）
const TEXT_STYLE = { fontFamily } as const;

// 等级顺序固定：某个等级没数据也要出现（值 0），否则图例在三张图之间来回变
const GRADES: DefectGrade[] = ['CRITICAL', 'MAJOR', 'MINOR'];
const GRADE_COLORS: Record<DefectGrade, string> = {
  CRITICAL: color.danger,
  MAJOR: color.warning,
  MINOR: color.fgMuted,
};

/** CH-01 每日合格率趋势：日期 → x 轴，合格率 → y 轴（0–100）。 */
export function trendOption(points: TrendPoint[]): EChartsOption {
  return {
    textStyle: TEXT_STYLE,
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
        // 每日合格率是离散测量值，平滑曲线会暗示不存在的中间值
        smooth: false,
        symbolSize: 6,
        itemStyle: { color: color.accent },
        lineStyle: { color: color.accent, width: 2 },
      },
    ],
  };
}

/**
 * CH-03 工序合格率对比：横向条形图，各条右侧标注百分比。
 *
 * 轴起点不用 0（P6 签样：「91% 与 98% 视觉无差别」），但也**不能写死 85**——
 * 写死时任何低于 85% 的工序柱子会被轴直接裁掉，看上去像没数据。
 * 改为按最低合格率向下取 5% 的整数倍再留 5% 余量，下限 0：
 * 数据都在 90 以上时退化成签样的 85 起点，数据偏低时柱子仍然可见。
 */
export function processOption(rows: ProcessComparison[]): EChartsOption {
  const lowest = rows.length === 0 ? 0 : Math.min(...rows.map((row) => row.passRate));
  const axisMin = Math.max(0, Math.floor(lowest / 5) * 5 - 5);
  return {
    textStyle: TEXT_STYLE,
    tooltip: { trigger: 'axis' },
    grid: { left: 96, right: 56, top: 16, bottom: 32 },
    xAxis: {
      type: 'value',
      min: axisMin,
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

/**
 * CH-02 缺陷帕累托：柱为出现次数（左轴），折线为累计百分比（右轴 0–100）。
 * 后端保证累计线末项恰好 100，前端不再累加（AC-09）。
 */
export function paretoOption(items: ParetoItem[]): EChartsOption {
  return {
    textStyle: TEXT_STYLE,
    tooltip: { trigger: 'axis' },
    legend: { ...LEGEND, top: 0 },
    grid: { left: 48, right: 48, top: 40, bottom: 48 },
    xAxis: {
      type: 'category',
      data: items.map((item) => DEFECT_TYPE_LABELS[item.defectType]),
      axisLabel: AXIS_LABEL,
      axisLine: AXIS_LINE,
      axisTick: { show: false },
    },
    yAxis: [
      {
        type: 'value',
        name: '缺陷数',
        nameTextStyle: AXIS_LABEL,
        axisLabel: AXIS_LABEL,
        splitLine: GRID_LINE,
      },
      {
        type: 'value',
        min: 0,
        max: 100,
        axisLabel: { ...AXIS_LABEL, formatter: '{value}%' },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: '缺陷数',
        type: 'bar',
        data: items.map((item) => item.count),
        itemStyle: { color: color.accent },
      },
      {
        name: '累计占比',
        type: 'line',
        yAxisIndex: 1,
        data: items.map((item) => item.cumulativePercent),
        symbolSize: 6,
        itemStyle: { color: color.warning },
        lineStyle: { color: color.warning, width: 2 },
      },
    ],
  };
}

/**
 * CH-05 缺陷等级构成：按场景分组的堆叠柱状图。x 轴只列有数据的场景，
 * 按 IQC → PQC → OQC 固定顺序，后端返回顺序变了图也不会跟着变。
 */
export function gradeOption(rows: GradeSceneCount[]): EChartsOption {
  const scenes = (Object.keys(SCENE_LABELS) as Scene[]).filter((scene) => rows.some((row) => row.scene === scene));
  return {
    textStyle: TEXT_STYLE,
    tooltip: { trigger: 'axis' },
    legend: { ...LEGEND, top: 0 },
    grid: { left: 48, right: 24, top: 40, bottom: 32 },
    xAxis: {
      type: 'category',
      data: scenes.map((scene) => SCENE_LABELS[scene]),
      axisLabel: AXIS_LABEL,
      axisLine: AXIS_LINE,
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      axisLabel: AXIS_LABEL,
      splitLine: GRID_LINE,
    },
    series: GRADES.map((grade) => ({
      name: DEFECT_GRADE_LABELS[grade],
      type: 'bar',
      stack: 'grade',
      data: scenes.map((scene) => rows.find((row) => row.scene === scene && row.defectGrade === grade)?.count ?? 0),
      itemStyle: { color: GRADE_COLORS[grade] },
    })),
  };
}

/**
 * CH-04 参数控制图：实测值折线 + UCL/CL/LCL 三条线。控制限取自标准的上下限，
 * 不另行计算（SRS 6.3、AC-09）；单侧判定方式缺失的那一侧不画线。
 */
export function controlOption(chart: ControlChart): EChartsOption {
  const marks = [
    { name: 'UCL', yAxis: chart.ucl },
    { name: 'CL', yAxis: chart.cl },
    { name: 'LCL', yAxis: chart.lcl },
  ].filter((mark): mark is { name: string; yAxis: number } => mark.yAxis !== null);
  return {
    textStyle: TEXT_STYLE,
    tooltip: { trigger: 'axis' },
    // right 留够 UCL/LCL 标签的宽度：markLine 标签默认画在线的右端之外，
    // 24px 会把「UCL」裁成「UC」（截图上实际发生过）
    grid: { left: 56, right: 64, top: 24, bottom: 32 },
    xAxis: {
      type: 'category',
      data: chart.points.map((point) => point.inspectTime.replace('T', ' ')),
      axisLabel: AXIS_LABEL,
      axisLine: AXIS_LINE,
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      name: chart.unit ?? '',
      nameTextStyle: AXIS_LABEL,
      axisLabel: AXIS_LABEL,
      splitLine: GRID_LINE,
      // 实测值贴近控制限时，刻度须覆盖控制线，否则线画在可视区外
      min: (value: { min: number }) => Math.min(value.min, ...marks.map((mark) => mark.yAxis)),
      max: (value: { max: number }) => Math.max(value.max, ...marks.map((mark) => mark.yAxis)),
    },
    series: [
      {
        name: '实测值',
        type: 'line',
        data: chart.points.map((point) => point.measuredValue),
        symbolSize: 4,
        itemStyle: { color: color.accent },
        lineStyle: { color: color.accent, width: 2 },
        markLine: {
          symbol: 'none',
          data: marks.map((mark) => ({
            name: mark.name,
            yAxis: mark.yAxis,
            lineStyle: { color: mark.name === 'CL' ? color.fgMuted : color.danger, type: 'dashed' },
            label: { formatter: mark.name, color: color.fgMuted },
          })),
        },
      },
    ],
  };
}

/** CH-07 检验员工作量：x 轴为姓名，y 轴为记录数。 */
export function workloadOption(rows: InspectorWorkload[]): EChartsOption {
  return {
    textStyle: TEXT_STYLE,
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 24, top: 16, bottom: 32 },
    xAxis: {
      type: 'category',
      data: rows.map((row) => row.inspectorName),
      axisLabel: AXIS_LABEL,
      axisLine: AXIS_LINE,
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      axisLabel: AXIS_LABEL,
      splitLine: GRID_LINE,
    },
    series: [
      {
        name: '记录数',
        type: 'bar',
        data: rows.map((row) => row.count),
        itemStyle: { color: color.accent },
      },
    ],
  };
}

/**
 * CH-08 缺陷处理时长：x 轴等级中文名，y 轴平均小时数。
 * avgHours 为 null 的等级整根柱不画——后端已保证不返回 null，这里只是防御。
 */
export function durationOption(rows: DefectDuration[]): EChartsOption {
  const shown = rows.filter((row) => row.avgHours !== null);
  return {
    textStyle: TEXT_STYLE,
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 24, top: 16, bottom: 32 },
    xAxis: {
      type: 'category',
      data: shown.map((row) => DEFECT_GRADE_LABELS[row.defectGrade]),
      axisLabel: AXIS_LABEL,
      axisLine: AXIS_LINE,
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      name: '小时',
      nameTextStyle: AXIS_LABEL,
      axisLabel: AXIS_LABEL,
      splitLine: GRID_LINE,
    },
    series: [
      {
        name: '平均处理时长',
        type: 'bar',
        data: shown.map((row) => row.avgHours),
        itemStyle: { color: color.warning },
      },
    ],
  };
}

/**
 * CH-06 批次合格率：单个批次的半圆弧仪表盘，满值 100%（P6 签样）。
 */
export function gaugeOption(row: BatchPassRate): EChartsOption {
  return {
    textStyle: TEXT_STYLE,
    series: [
      {
        type: 'gauge',
        startAngle: 180,
        endAngle: 0,
        radius: '92%',
        min: 0,
        max: 100,
        detail: { formatter: '{value}%', color: color.fg },
        axisLabel: { color: color.fgMuted },
        axisLine: { lineStyle: { color: [[1, color.line2]] } },
        progress: { show: true, itemStyle: { color: color.accent } },
        data: [{ value: row.passRate, name: row.batchNo }],
        title: { color: color.fgMuted },
      },
    ],
  };
}
