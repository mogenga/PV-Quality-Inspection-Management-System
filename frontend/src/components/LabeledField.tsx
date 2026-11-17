import { useId } from 'react';
import { Stack, TextField, Typography } from '@mui/material';

/**
 * 带框外标签的文本输入。
 *
 * 不使用 MUI 的浮动 label：本项目的输入框是「浅灰底 + 实线边框」样式，
 * 浮动 label 缩小时依赖边框开槽，无槽可钻就会压住边线；且过渡动画期间
 * label 与输入文字重叠。标签固定在上方也更符合表单阅读顺序。
 */
export default function LabeledField({
  label,
  value,
  onChange,
  type = 'text',
  autoComplete,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  autoComplete?: string;
}) {
  const id = useId();
  return (
    <Stack spacing={1}>
      <Typography component="label" htmlFor={id} variant="body2" sx={{ fontWeight: 550 }}>
        {label}
      </Typography>
      <TextField
        id={id}
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        autoComplete={autoComplete}
        slotProps={{ htmlInput: { 'aria-label': label } }}
      />
    </Stack>
  );
}
