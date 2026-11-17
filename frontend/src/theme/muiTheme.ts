import { alpha, createTheme } from '@mui/material/styles';
import { zhCN } from '@mui/material/locale';
import { color, fontFamily, radius, space } from './tokens';

// MUI 9 没有 Ant Design 式的 ConfigProvider，内置文案的本地化靠把 locale 并入主题
export const muiTheme = createTheme(
  {
    palette: {
      mode: 'light',
      background: { default: color.bg, paper: color.surface1 },
      text: { primary: color.fg, secondary: color.fgMuted },
      primary: { main: color.accent, dark: color.accentHover, contrastText: color.onAccent },
      error: { main: color.danger },
      success: { main: color.success },
      warning: { main: color.warning },
      divider: color.line1,
    },
    shape: { borderRadius: radius.md },
    spacing: space.s2,
    typography: {
      fontFamily,
      button: { textTransform: 'none', fontWeight: 550 },
      h1: { fontSize: 32, fontWeight: 700, letterSpacing: '-0.02em' },
      h2: { fontSize: 24, fontWeight: 700, letterSpacing: '-0.01em' },
      h3: { fontSize: 20, fontWeight: 550 },
      body1: { fontSize: 14, lineHeight: 1.5 },
      body2: { fontSize: 13, lineHeight: 1.5 },
      caption: { fontSize: 12 },
    },
    components: {
      MuiPaper: { defaultProps: { elevation: 0 }, styleOverrides: { root: { borderRadius: radius.lg } } },
      MuiButton: { defaultProps: { disableElevation: true }, styleOverrides: { root: { borderRadius: radius.sm } } },
      MuiOutlinedInput: {
        styleOverrides: {
          root: {
            borderRadius: radius.sm,
            backgroundColor: color.surfaceSunken,
            // 合同 §6.2：输入框凹陷底 + 外投影 + inset 内环，不是白底亮线
            boxShadow: `0 1px 2px -1px rgb(0 0 0 / 0.18), inset 0 1px 2px rgb(0 0 0 / 0.10), inset 0 0 0 1px ${color.lineInset}`,
            '& .MuiOutlinedInput-notchedOutline': { borderColor: 'transparent' },
            '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: 'transparent' },
            '&.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: 'transparent' },
            '&.Mui-error .MuiOutlinedInput-notchedOutline': { borderColor: 'transparent' },
            '&.Mui-focused': {
              boxShadow: `inset 0 0 0 1px ${color.accent}, 0 0 0 3px ${alpha(color.accent, 0.22)}`,
            },
            '&.Mui-error': {
              boxShadow: `inset 0 0 0 1px ${color.danger}`,
            },
          },
        },
      },
      MuiTableCell: { styleOverrides: { root: { borderColor: color.line1 } } },
    },
  },
  zhCN,
);

export { color, fontFamily, radius, space };
