import { createTheme } from '@mui/material/styles';
import { color, fontFamily, radius, space } from './tokens';

export const muiTheme = createTheme({
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
    MuiOutlinedInput: { styleOverrides: { root: { borderRadius: radius.sm, backgroundColor: color.surface1 } } },
    MuiTableCell: { styleOverrides: { root: { borderColor: color.line1 } } },
  },
});

export { color, fontFamily, radius, space };
