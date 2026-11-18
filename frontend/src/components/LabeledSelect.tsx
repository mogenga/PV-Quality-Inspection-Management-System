import { useId } from 'react';
import { MenuItem, Select, Stack, Typography } from '@mui/material';

export type SelectOption<T extends string> = { value: T; label: string };

/**
 * 带框外标签的下拉选择。与 LabeledField 成对使用，保证表单里两种控件同高同宽。
 *
 * - `size="small"` 与 LabeledField 保持一致，避免一行内控件高低不齐
 * - `displayEmpty` + `renderValue` 让「全部」这类空值选项有可见文字，
 *   否则 MUI 在 value 为空时不渲染任何内容，下拉框看起来像没选中
 */
export default function LabeledSelect<T extends string>({
  label,
  value,
  options,
  onChange,
  minWidth = 180,
  disabled = false,
}: {
  label: string;
  value: T;
  options: SelectOption<T>[];
  onChange: (value: T) => void;
  minWidth?: number;
  disabled?: boolean;
}) {
  const id = useId();
  const selected = options.find((option) => option.value === value);
  return (
    <Stack spacing={1} sx={{ minWidth }}>
      <Typography component="label" htmlFor={id} variant="body2" sx={{ fontWeight: 550 }}>
        {label}
      </Typography>
      <Select
        id={id}
        size="small"
        displayEmpty
        value={value}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value as T)}
        inputProps={{ 'aria-label': label }}
        renderValue={() => selected?.label ?? ''}
      >
        {options.map((option) => (
          <MenuItem key={option.value} value={option.value}>
            {option.label}
          </MenuItem>
        ))}
      </Select>
    </Stack>
  );
}
