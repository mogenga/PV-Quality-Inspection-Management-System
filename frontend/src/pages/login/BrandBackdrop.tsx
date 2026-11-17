import { alpha } from '@mui/material/styles';
import { color } from '../../theme/muiTheme';

const VIEW_W = 600;
const VIEW_H = 900;
// 消失点在画布上方：竖缝全部从这里放射，透视由整幅场景决定而非逐行偏移
const VP = { x: VIEW_W * 0.5, y: -140 };
const mullionX = (theta: number, y: number) => VP.x + (y - VP.y) * Math.tan(theta);
// 线条越近（y 越大）越实
const strokeAt = (y: number) => alpha(color.surface1, 0.05 + (0.09 * y) / VIEW_H);

// 竖缝：-42°~+42° 对称 11 条，只有两条对称的用强调色，其余按远近取值
const MULLIONS = Array.from({ length: 11 }, (_, id) => ({
  id,
  theta: ((-42 + id * 8.4) * Math.PI) / 180,
  accent: id === 2 || id === 8,
}));

// 行界：指数间距——越靠近消失点越挤，越靠近观众越疏
const ROWS = Array.from({ length: 9 }, (_, n) => VIEW_H * ((n + 1) / 9) ** 1.55);
// 每个行带内一条短线，暗示单块面板的面，不填充
const FACE_PAIRS = [
  [4, 6],
  [3, 7],
  [2, 8],
];

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
        {/* 竖缝远场淡（0.05）、近处实（0.14） */}
        <linearGradient id="brand-mullion" x1="0" y1="0" x2="0" y2={VIEW_H} gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor={alpha(color.surface1, 0.05)} />
          <stop offset="100%" stopColor={alpha(color.surface1, 0.14)} />
        </linearGradient>
        {/* 顶部 25% 淡出：藏起汇聚点，让图案读作延伸出画框 */}
        <linearGradient id="brand-top-fade" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color.surface1} stopOpacity={0} />
          <stop offset="25%" stopColor={color.surface1} stopOpacity={1} />
        </linearGradient>
        <mask id="brand-top-mask">
          <rect width={VIEW_W} height={VIEW_H} fill="url(#brand-top-fade)" />
        </mask>
      </defs>
      <rect width={VIEW_W} height={VIEW_H} fill="url(#brand-glow)" />
      <g mask="url(#brand-top-mask)" fill="none" strokeLinecap="round">
        {MULLIONS.map(({ id, theta, accent }) => (
          <line
            key={id}
            x1={mullionX(theta, 0)}
            y1={0}
            x2={mullionX(theta, VIEW_H)}
            y2={VIEW_H}
            stroke={accent ? alpha(color.accent, 0.25) : 'url(#brand-mullion)'}
            strokeWidth={accent ? 1.5 : 1}
          />
        ))}
        {ROWS.map((y) => (
          <line key={y} x1={0} y1={y} x2={VIEW_W} y2={y} stroke={strokeAt(y)} strokeWidth={1} />
        ))}
        {ROWS.slice(1).map((y, index) => {
          const midY = (ROWS[index] + y) / 2;
          const [left, right] = FACE_PAIRS[index % FACE_PAIRS.length];
          return (
            <line
              key={`face-${y}`}
              x1={mullionX(MULLIONS[left].theta, midY)}
              y1={midY}
              x2={mullionX(MULLIONS[right].theta, midY)}
              y2={midY}
              stroke={strokeAt(midY)}
              strokeWidth={1}
            />
          );
        })}
      </g>
    </svg>
  );
}
