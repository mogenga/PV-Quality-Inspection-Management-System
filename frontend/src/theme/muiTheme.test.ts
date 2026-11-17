import { describe, expect, it } from 'vitest';
import { muiTheme, color, fontFamily } from './muiTheme';

describe('MUI 主题吃视觉令牌', () => {
  it('primary 与 accent 同值', () => {
    expect(muiTheme.palette.primary.main).toBe(color.accent);
  });
  it('页面基底与卡片背景分别取 bg 与 surface1', () => {
    expect(muiTheme.palette.background.default).toBe(color.bg);
    expect(muiTheme.palette.background.paper).toBe(color.surface1);
  });
  it('字族以几何无衬线开头并含中文字族', () => {
    expect(fontFamily.startsWith('"Century Gothic"')).toBe(true);
    expect(fontFamily).toContain('PingFang SC');
    expect(fontFamily).toContain('Microsoft YaHei');
  });
  it('圆角来自令牌而非 MUI 默认 4px', () => {
    expect(muiTheme.shape.borderRadius).toBe(12);
  });
});
