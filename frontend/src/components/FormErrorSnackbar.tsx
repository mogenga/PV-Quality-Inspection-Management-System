import { Alert, Snackbar } from '@mui/material';

/**
 * 表单提交失败的浮层提示。
 *
 * 不用「渲染在内容顶部的 Alert」：弹窗内容是可滚动的，用户点「确定」时人在底部，
 * 顶部提示会被滚出视野，看起来像什么都没发生。浮层固定在视口底部，任何滚动位置都可见。
 */
export default function FormErrorSnackbar({ error, onClose }: { error: string | null; onClose: () => void }) {
  return (
    <Snackbar
      open={error !== null}
      autoHideDuration={5000}
      onClose={onClose}
      anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
    >
      <Alert severity="error" variant="filled" onClose={onClose} sx={{ maxWidth: 480 }}>
        {error}
      </Alert>
    </Snackbar>
  );
}
