import { IconButton, InputAdornment } from '@mui/material';
import { Visibility, VisibilityOff } from '@mui/icons-material';

/**
 * 密码框末尾的「显示/隐藏密码」按钮。
 *
 * 按钮位于表单内，必须显式声明 type="button"，否则默认的 submit 会触发提交。
 * aria-label 随状态切换，屏幕阅读器读到的始终是「将要执行的动作」：
 * 密码被遮住时读「显示密码」，明文显示时读「隐藏密码」。
 * 放在 endAdornment 里，DOM 顺序在输入框之后，不抢占输入框的 Tab 顺序。
 */
export default function PasswordVisibilityAdornment({ visible, onToggle }: { visible: boolean; onToggle: () => void }) {
  return (
    <InputAdornment position="end" sx={{ mr: 0.5 }}>
      <IconButton
        type="button"
        size="small"
        edge="end"
        aria-label={visible ? '隐藏密码' : '显示密码'}
        onClick={onToggle}
        sx={{ color: 'text.secondary' }}
      >
        {visible ? <VisibilityOff fontSize="small" /> : <Visibility fontSize="small" />}
      </IconButton>
    </InputAdornment>
  );
}
