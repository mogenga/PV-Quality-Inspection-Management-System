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

it('聚焦的出错输入框同时保留 danger 内环与 accent 外环', async () => {
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
  const shadow = getComputedStyle(root).boxShadow;
  expect(shadow).toContain(cssColor(color.danger));
  expect(shadow).toContain(alpha(color.accent, 0.22));
});
