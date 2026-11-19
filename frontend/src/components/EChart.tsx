import { useEffect, useRef } from 'react';
import { Box } from '@mui/material';
import * as echarts from 'echarts/core';
import type { ECharts, EChartsOption } from 'echarts';
import { BarChart, GaugeChart, LineChart, PieChart } from 'echarts/charts';
import {
  GridComponent,
  LegendComponent,
  MarkLineComponent,
  TitleComponent,
  TooltipComponent,
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

// 按需注册：模块顶层只执行一次。Task 5 的六张图也在同一份列表内。
echarts.use([
  LineChart,
  BarChart,
  PieChart,
  GaugeChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  MarkLineComponent,
  CanvasRenderer,
]);

/**
 * ECharts 的最小 React 封装。只做四件事：init / setOption / resize / dispose。
 *
 * 不装 echarts-for-react（见 Task 4 brief）：它早于 React 19 与 ECharts 6，
 * 而这里只用到它的 API 子集。
 *
 * `option` 必须由调用方 `useMemo` 稳定，否则每次渲染都会整体重设图表。
 */
export default function EChart({
  option,
  height = 320,
  ariaLabel,
}: {
  option: EChartsOption;
  height?: number;
  ariaLabel: string;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<ECharts | null>(null);

  useEffect(() => {
    const container = containerRef.current;
    // jsdom 没有 canvas 上下文（CanvasRenderingContext2D 不存在）：此时只保留
    // 可访问容器、不建实例。真实浏览器一定具备该构造函数，行为不变。
    if (!container || typeof CanvasRenderingContext2D === 'undefined') return;
    const chart = echarts.init(container);
    chartRef.current = chart;
    // 没有 ResizeObserver 的环境里图表照样能用，只是不跟随容器缩放
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(() => chart.resize());
    observer?.observe(container);
    return () => {
      observer?.disconnect();
      chart.dispose();
      chartRef.current = null;
    };
  }, []);

  useEffect(() => {
    // 第二参数 true = 不合并：切换筛选后若合并，旧 series 会残留在图里
    chartRef.current?.setOption(option, true);
  }, [option]);

  return <Box ref={containerRef} role="img" aria-label={ariaLabel} sx={{ width: '100%', height }} />;
}
