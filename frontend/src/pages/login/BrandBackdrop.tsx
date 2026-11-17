import { alpha } from '@mui/material/styles';
import { color } from '../../theme/muiTheme';

const VIEW_W = 600;
const VIEW_H = 900;
const SKEW = 72;

// 抽象的组件阵列：越靠下的行越大，读作朝左上消失的成排面板
const rows = Array.from({ length: 6 }, (_, index) => {
  const top = 80 + index * 128;
  const inset = 176 - index * 24;
  const height = 56 + index * 10;
  return {
    id: index,
    outline: [inset + SKEW, top, VIEW_W - inset + SKEW, top, VIEW_W - inset, top + height, inset, top + height].join(
      ' ',
    ),
    mullion: { x1: VIEW_W / 2 + SKEW / 2, y1: top, x2: VIEW_W / 2, y2: top + height },
    accent: index === 1 || index === 4,
    opacity: 0.08 + index * 0.012,
  };
});

export default function BrandBackdrop() {
  return (
    <svg
      aria-hidden="true"
      width="100%"
      height="100%"
      viewBox={`0 0 ${VIEW_W} ${VIEW_H}`}
      preserveAspectRatio="xMidYMid slice"
      style={{ position: 'absolute', inset: 0, zIndex: 0 }}
    >
      <defs>
        <radialGradient id="brand-glow" cx="18%" cy="12%" r="78%">
          <stop offset="0%" stopColor={alpha(color.surface1, 0.1)} />
          <stop offset="100%" stopColor={alpha(color.surface1, 0)} />
        </radialGradient>
      </defs>
      <rect width={VIEW_W} height={VIEW_H} fill="url(#brand-glow)" />
      <g fill="none" strokeLinecap="round">
        {rows.map((row) => (
          <g key={row.id}>
            <polygon
              points={row.outline}
              stroke={row.accent ? alpha(color.accent, 0.25) : alpha(color.surface1, row.opacity)}
              strokeWidth={row.accent ? 1.5 : 1}
            />
            <line
              x1={row.mullion.x1}
              y1={row.mullion.y1}
              x2={row.mullion.x2}
              y2={row.mullion.y2}
              stroke={alpha(color.surface1, row.opacity * 0.6)}
              strokeWidth={1}
            />
          </g>
        ))}
      </g>
    </svg>
  );
}
