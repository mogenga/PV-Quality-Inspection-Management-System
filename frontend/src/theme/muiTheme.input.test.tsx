import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CssBaseline, ThemeProvider } from '@mui/material';
import TextField from '@mui/material/TextField';
import { alpha } from '@mui/material/styles';
import { expect, it } from 'vitest';
import { color, muiTheme } from './muiTheme';

// jsdom 的计算样式把颜色归一化成 rgb()/rgba()，这里用同一条路径解析令牌
function cssColor(value: string): string {
  const probe = document.createElement('span');
  probe.style.color = value;
  document.body.append(probe);
  const resolved = getComputedStyle(probe).color;
  probe.remove();
  return resolved;
}

it('聚焦的出错输入框同时体现错误边框与焦点外圈', async () => {
  render(
    <ThemeProvider theme={muiTheme}>
      <CssBaseline />
      <TextField label="用户名" error helperText="必填" />
    </ThemeProvider>,
  );

  const input = screen.getByLabelText('用户名');
  await userEvent.click(input);
  const root = input.closest('.MuiOutlinedInput-root') as HTMLElement;

  expect(root).toHaveClass('Mui-focused', 'Mui-error');
  const style = getComputedStyle(root);
  // 错误由边框承担，焦点由外圈承担：两者都不能丢
  expect(style.borderColor).toBe(cssColor(color.danger));
  expect(style.boxShadow).toContain(alpha(color.danger, 0.22));
});

it('普通输入框默认有细边框与阴影，聚焦时上浮并获得 accent 外圈', async () => {
  render(
    <ThemeProvider theme={muiTheme}>
      <CssBaseline />
      <TextField label="用户名" />
    </ThemeProvider>,
  );

  const input = screen.getByLabelText('用户名');
  const root = input.closest('.MuiOutlinedInput-root') as HTMLElement;

  const idle = getComputedStyle(root);
  expect(idle.borderColor).toBe(cssColor(color.line2));
  expect(idle.transform).toBe('none');

  await userEvent.click(input);
  const focused = getComputedStyle(root);
  expect(focused.borderColor).toBe(cssColor(color.fgMuted));
  expect(focused.boxShadow).toContain(alpha(color.accent, 0.22));
  // jsdom 不归一化 transform，返回声明值
  expect(focused.transform).toBe('translateY(-1px)');
});
