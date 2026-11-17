import { alpha } from '@mui/material/styles';
import { color } from '../../theme/muiTheme';

/**
 * 几何标识：同心校验靶心。
 * 外环 + 中环 + 分格内圆，三道弧线对应「标准 → 判定 → 闭环」。
 * 纯 SVG，随尺寸缩放不糊；颜色只取令牌。
 */
export default function BrandMark({ size = 48 }: { size?: number }) {
  return (
    <svg aria-hidden="true" width={size} height={size} viewBox="0 0 48 48" fill="none" style={{ display: 'block' }}>
      <defs>
        <linearGradient id="mark-ring" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor={alpha(color.accent, 1)} />
          <stop offset="100%" stopColor={alpha(color.accent, 0.72)} />
        </linearGradient>
        <linearGradient id="mark-core" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor={alpha(color.accent, 0.9)} />
          <stop offset="100%" stopColor={alpha(color.accent, 0.5)} />
        </linearGradient>
      </defs>

      {/* 外环：完整一圈，最细 */}
      <circle cx="24" cy="24" r="21" stroke="url(#mark-ring)" strokeWidth="1.5" opacity="0.45" />

      {/* 中环：四段等分弧，缺口朝向四个对角——读作「校验靶环」 */}
      <g stroke="url(#mark-ring)" strokeWidth="2" strokeLinecap="round">
        <path d="M 24 6 A 18 18 0 0 1 36.7 11.3" />
        <path d="M 42 24 A 18 18 0 0 1 36.7 36.7" />
        <path d="M 24 42 A 18 18 0 0 1 11.3 36.7" />
        <path d="M 6 24 A 18 18 0 0 1 11.3 11.3" />
      </g>

      {/* 内圆：实心，中间一条水平切缝，暗示「判定刻度」 */}
      <circle cx="24" cy="24" r="9" fill="url(#mark-core)" />
      <line x1="17.5" y1="24" x2="30.5" y2="24" stroke={color.surface1} strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}
