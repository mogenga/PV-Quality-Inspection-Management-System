import { Alert, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';

/**
 * 危险操作的二次确认。标题即动作（「删除项目」），正文说清后果；
 * 确认按钮固定为「确认删除」，让可访问名与按钮语义一致。
 *
 * error 用来原样展示后端返回的中文原因（如「项目下存在批次或标准，不能删除」），
 * 不在前端另造一套提示文案。
 */
export default function ConfirmDialog({
  open,
  title,
  content,
  onCancel,
  onConfirm,
  pending = false,
  error = null,
}: {
  open: boolean;
  title: string;
  content: string;
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
          确认删除
        </Button>
      </DialogActions>
    </Dialog>
  );
}
