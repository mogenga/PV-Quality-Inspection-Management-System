export const fontFamily = '"Century Gothic", "Avenir Next", "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif';

export const color = {
  bg: '#F6F7F9',
  surface1: '#FFFFFF',
  surface2: '#F9FAFB',
  surface3: '#F3F4F6',
  surfaceSunken: '#F3F4F6',
  fg: '#1A1A19',
  fgMuted: '#667085',
  accent: '#2563EB',
  accentHover: '#1D4ED8',
  line1: '#E5E7EB',
  line2: '#D1D5DB',
  lineInset: '#EEF1F5',
  danger: '#B91C1C',
  success: '#15803D',
  warning: '#B45309',
  onAccent: '#FFFFFF',
} as const;

export const radius = { sm: 8, md: 12, lg: 20, full: 999 } as const;
export const space = { s1: 4, s2: 8, s3: 12, s4: 16, s5: 24, s6: 32, s7: 48, s8: 64 } as const;

// 两档极轻阴影之一：卡片浮在浅灰页面上的最小落差，不用于浮层
export const shadow = {
  card: '0 1px 2px -1px rgb(0 0 0 / 0.06), 0 2px 8px -2px rgb(0 0 0 / 0.05)',
} as const;
