import { alpha, createTheme } from '@mui/material/styles';
import { zhCN } from '@mui/material/locale';
import { color, fontFamily, radius, shadow, space } from './tokens';

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
            // 参考 uiverse.io/RashadGhzi/smooth-skunk-12：
            // 浅灰底 + 细边框 + 小阴影；hover 边框转蓝、阴影加深；focus 上浮 1px、蓝色外圈
            border: `1px solid ${color.line2}`,
            boxShadow: `0 1px 2px 0 ${alpha(color.fg, 0.05)}`,
            transition: 'border-color 200ms ease, box-shadow 200ms ease, transform 200ms ease',
            '& .MuiOutlinedInput-notchedOutline': { border: 'none' },
            '&:hover': {
              borderColor: alpha(color.accent, 0.55),
              boxShadow: `0 4px 10px -2px ${alpha(color.fg, 0.12)}`,
            },
            '&.Mui-focused': {
              borderColor: color.fgMuted,
              boxShadow: `0 0 0 3px ${alpha(color.accent, 0.22)}`,
              transform: 'translateY(-1px)',
            },
            '&.Mui-error': {
              borderColor: color.danger,
              boxShadow: `0 0 0 3px ${alpha(color.danger, 0.16)}`,
            },
            // 错误态同时聚焦时，外圈让位给 danger，边框保持深色以便识别焦点
            '&.Mui-focused.Mui-error': {
              borderColor: color.danger,
              boxShadow: `0 0 0 3px ${alpha(color.danger, 0.22)}`,
              transform: 'translateY(-1px)',
            },
          },
        },
      },
      MuiTableCell: { styleOverrides: { root: { borderColor: color.line1 } } },
      // 滚动交给 Paper 本体：圆角会裁掉滚动条两端的方形端点，左右看起来对称。
      // 若让全宽、无圆角的 DialogContent 自己滚动，滚动条会贴着右边缘盖住右下圆角，
      // 形成「左圆右尖」的不对称（新增标准弹窗踩过）。
      //
      // 原生滚动条是矩形的，即使父级有圆角也照样贴边画到角上；把轨道设成透明、
      // 滑块改成带透明边框的胶囊（background-clip: content-box 让边框区不显示颜色），
      // 视觉上从边缘内缩，四角才不会出现「左圆右方」。
      MuiDialog: {
        styleOverrides: {
          paper: {
            overflowY: 'auto',
            scrollbarWidth: 'thin',
            scrollbarColor: `${alpha(color.fgMuted, 0.35)} transparent`,
            '&::-webkit-scrollbar': { width: 12 },
            '&::-webkit-scrollbar-track': { backgroundColor: 'transparent' },
            '&::-webkit-scrollbar-thumb': {
              backgroundColor: alpha(color.fgMuted, 0.35),
              borderRadius: radius.full,
              border: '3px solid transparent',
              backgroundClip: 'content-box',
            },
            '&::-webkit-scrollbar-thumb:hover': { backgroundColor: alpha(color.fgMuted, 0.55) },
          },
        },
      },
      MuiDialogContent: {
        styleOverrides: {
          root: { overflowY: 'visible' },
        },
      },
      // 浮层菜单从输入框弹出，圆角必须与输入框一致；否则小框弹大圆角菜单显得脱节
      MuiMenu: {
        styleOverrides: {
          paper: {
            borderRadius: radius.sm,
            border: `1px solid ${color.line1}`,
            boxShadow: `0 8px 24px -8px ${alpha(color.fg, 0.18)}`,
            marginTop: 4,
          },
          list: { padding: 4 },
        },
      },
      MuiMenuItem: {
        styleOverrides: {
          root: { borderRadius: 4, minHeight: 36, fontSize: 14 },
        },
      },
    },
  },
  zhCN,
);

export { color, fontFamily, radius, shadow, space };
