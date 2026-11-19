import { Alert, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';

/**
 * 二次确认弹窗。标题即动作（「删除项目」「初始化演示数据」），正文说清后果。
 *
 * confirmLabel 默认「确认删除」：三个删除调用点都不传它，行为不变；
 * 非删除动作（如演示数据初始化）传入自己的确认文案，避免按钮语义与动作不符。
 *
 * error 用来原样展示后端返回的中文原因（如「项目下存在批次或标准，不能删除」），
 * 不在前端另造一套提示文案。
 */
export default function ConfirmDialog({
  open,
  title,
  content,
  confirmLabel = '确认删除',
  onCancel,
  onConfirm,
  pending = false,
  error = null,
}: {
  open: boolean;
  title: string;
  content: string;
  confirmLabel?: string;
  onCancel: () => void;
  onConfirm: () => void;
  pending?: boolean;
  error?: string | null;
}) {
  return (
    <Dialog open={open} onClose={onCancel} fullWidth maxWidth="xs">
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <DialogContentText>{content}</DialogContentText>
        {error && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {error}
          </Alert>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel}>取消</Button>
        <Button color="error" variant="contained" onClick={onConfirm} disabled={pending}>
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
