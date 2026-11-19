import { render, screen } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import EChart from './EChart';

// jsdom 没有 canvas：用最小替身替换 ECharts。断言的是我们自己的契约——
// 可访问名与生命周期（init / setOption / dispose），不是 ECharts 的行为。
const mocks = vi.hoisted(() => {
  const setOption = vi.fn();
  const resize = vi.fn();
  const dispose = vi.fn();
  return { init: vi.fn(() => ({ setOption, resize, dispose })), use: vi.fn(), setOption, dispose };
});

vi.mock('echarts/core', () => ({ init: mocks.init, use: mocks.use }));

// jsdom 也没有 ResizeObserver；真实浏览器里它负责跟随容器尺寸
class ResizeObserverStub {
  observe = vi.fn();
  unobserve = vi.fn();
  disconnect = vi.fn();
}

it('图表容器带上 role=img 与中文可访问名，卸载时释放实例', () => {
  // 组件在无 canvas 的环境里不建图；测试只关心契约，所以声明一个占位构造函数让路径走通
  vi.stubGlobal('CanvasRenderingContext2D', class {});
  vi.stubGlobal('ResizeObserver', ResizeObserverStub);
  const { unmount } = render(<EChart option={{}} ariaLabel="每日合格率趋势" />);

  expect(screen.getByRole('img', { name: '每日合格率趋势' })).toBeInTheDocument();
  expect(mocks.init).toHaveBeenCalledTimes(1);
  // 第二个参数为 true：切换筛选后整体替换 option，不合并旧 series
  expect(mocks.setOption).toHaveBeenCalledWith({}, true);

  unmount();
  expect(mocks.dispose).toHaveBeenCalledTimes(1);
});
