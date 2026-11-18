import { useState } from 'react';
import type { FormEvent } from 'react';
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle } from '@mui/material';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '../../api/client';
import { voidRecord } from '../../api/inspection';
import { findMissing, missingMessage } from '../../utils/validation';
import LabeledField from '../../components/LabeledField';
import FormErrorSnackbar from '../../components/FormErrorSnackbar';

/**
 * 作废校验记录（FR-10）。只作废不物理删除（BR-08），作废原因必填——
 * 事后回看时，没有原因的作废行无法与录入错误、重复录入等情形区分。
 */
export default function VoidRecordDialog({
  open,
  recordId,
  onClose,
  onDone,
}: {
  open: boolean;
  recordId: number;
  onClose: () => void;
  onDone: () => void;
}) {
  const queryClient = useQueryClient();
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [invalid, setInvalid] = useState(false);

  const mutation = useMutation({
    mutationFn: (value: string) => voidRecord(recordId, value),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['records'] });
      onDone();
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : '网络异常，请稍后重试'),
  });

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    const missing = findMissing([{ field: 'reason', label: '作废原因', value: reason }]);
    setInvalid(missing.length > 0);
    const message = missingMessage(missing);
    if (message !== null) {
      setError(message);
      return;
    }
    mutation.mutate(reason.trim());
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <Box component="form" onSubmit={handleSubmit}>
        <DialogTitle>作废记录 #{recordId}</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            <LabeledField label="作废原因" value={reason} onChange={setReason} required error={invalid} />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>取消</Button>
          <Button type="submit" color="error" variant="contained" disabled={mutation.isPending}>
            确认作废
          </Button>
        </DialogActions>
        <FormErrorSnackbar error={error} onClose={() => setError(null)} />
      </Box>
    </Dialog>
  );
}
