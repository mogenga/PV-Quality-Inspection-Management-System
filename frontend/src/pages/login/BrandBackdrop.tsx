import { alpha } from '@mui/material/styles';
import { color } from '../../theme/muiTheme';

const VIEW_W = 1440;
const VIEW_H = 900;
// 消失点在画布上方偏右：全幅透视，不再是左半边的装饰
const VP = { x: VIEW_W * 0.62, y: -180 };
const mullionX = (theta: number, y: number) => VP.x + (y - VP.y) * Math.tan(theta);
// 线条强度：远处（上）几乎不可见，近处（下）清晰
const strokeAt = (y: number) => 0.09 + (0.2 * y) / VIEW_H;

// 竖缝：覆盖整个可见范围，-30°~+30° 共 15 条
const MULLIONS = Array.from({ length: 15 }, (_, id) => ({
  id,
  theta: ((-30 + id * 4.29) * Math.PI) / 180,
  accent: id === 4 || id === 10,
}));

// 行界：指数间距，近疏远密
const ROWS = Array.from({ length: 11 }, (_, n) => VIEW_H * 0.22 + VIEW_H * 0.78 * ((n + 1) / 11) ** 1.6);

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
        {/* 左上冷光：给页面一个光源方向。半径放大到 130% 避免圆形边界 */}
        <radialGradient id="login-glow" cx="22%" cy="0%" r="130%">
          <stop offset="0%" stopColor={alpha(color.accent, 0.2)} />
          <stop offset="35%" stopColor={alpha(color.accent, 0.07)} />
          <stop offset="70%" stopColor={alpha(color.accent, 0.015)} />
          <stop offset="100%" stopColor={alpha(color.accent, 0)} />
        </radialGradient>
        {/* 竖向淡出：顶部柔和过渡，底部不压暗 */}
        <linearGradient id="login-fade" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color.bg} stopOpacity={0.75} />
          <stop offset="30%" stopColor={color.bg} stopOpacity={0.2} />
          <stop offset="100%" stopColor={color.bg} stopOpacity={0} />
        </linearGradient>
        {/* 中心柔光：卡片背后提亮，保证表单可读。半径放大、边缘完全透明 */}
        <radialGradient id="login-halo" cx="50%" cy="50%" r="46%">
          <stop offset="0%" stopColor={color.surface1} stopOpacity={0.95} />
          <stop offset="55%" stopColor={color.surface1} stopOpacity={0.72} />
          <stop offset="85%" stopColor={color.surface1} stopOpacity={0.28} />
          <stop offset="100%" stopColor={color.surface1} stopOpacity={0} />
        </radialGradient>
      </defs>

      <rect width={VIEW_W} height={VIEW_H} fill={color.bg} />
      <rect width={VIEW_W} height={VIEW_H} fill="url(#login-glow)" />

      {/* 透视阵列 */}
      <g fill="none" strokeLinecap="round">
        {MULLIONS.map(({ id, theta, accent }) => (
          <line
            key={id}
            x1={mullionX(theta, 0)}
            y1={0}
            x2={mullionX(theta, VIEW_H)}
            y2={VIEW_H}
            stroke={accent ? alpha(color.accent, 0.38) : alpha(color.fg, strokeAt(VIEW_H) * 0.75)}
            strokeWidth={accent ? 1.6 : 1}
          />
        ))}
        {ROWS.map((y) => (
          <line key={y} x1={0} y1={y} x2={VIEW_W} y2={y} stroke={alpha(color.fg, strokeAt(y) * 0.65)} strokeWidth={1} />
        ))}
      </g>

      <rect width={VIEW_W} height={VIEW_H} fill="url(#login-fade)" />
      <rect width={VIEW_W} height={VIEW_H} fill="url(#login-halo)" />
    </svg>
  );
}
