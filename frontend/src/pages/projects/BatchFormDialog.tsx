import { useState } from 'react';
import type { FormEvent } from 'react';
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack } from '@mui/material';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createBatch, updateBatch } from '../../api/batches';
import { ApiError } from '../../api/client';
import type { BatchStatus, BatchSummary } from '../../api/types';
import { BATCH_STATUS_LABELS } from '../../utils/labels';
import LabeledField from '../../components/LabeledField';
import LabeledSelect from '../../components/LabeledSelect';

const STATUS_OPTIONS: { value: BatchStatus; label: string }[] = (Object.keys(BATCH_STATUS_LABELS) as BatchStatus[]).map(
  (status) => ({ value: status, label: BATCH_STATUS_LABELS[status] }),
);

// 可选字段留空即清空：后端 updateStrategy=ALWAYS，必须显式送 null 才能真的擦掉旧值
function optional(value: string): string | null {
  const trimmed = value.trim();
  return trimmed === '' ? null : trimmed;
}

/**
 * 新增/编辑批次。新增时 projectId 由所选项目带入；编辑时不发 projectId——
 * 批次不允许换项目，换项目会让检验记录里冗余的 project_id 错位。
 */
export default function BatchFormDialog({
  projectId,
  batch,
  onClose,
}: {
  projectId: number;
  batch: BatchSummary | null;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [batchNo, setBatchNo] = useState(batch?.batchNo ?? '');
  const [plannedQty, setPlannedQty] = useState(String(batch?.plannedQty ?? ''));
  const [startDate, setStartDate] = useState(batch?.startDate ?? '');
  const [endDate, setEndDate] = useState(batch?.endDate ?? '');
  const [status, setStatus] = useState<BatchStatus>(batch?.status ?? 'PLANNED');
  const [remark, setRemark] = useState(batch?.remark ?? '');
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (fields: {
      batchNo: string;
      plannedQty: number;
      startDate: string | null;
      endDate: string | null;
      status: BatchStatus;
      remark: string | null;
    }) => (batch ? updateBatch(batch.id, fields) : createBatch({ projectId, ...fields })),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['batches'] });
      onClose();
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : '网络异常，请稍后重试'),
  });

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    mutation.mutate({
      batchNo: batchNo.trim(),
      plannedQty: Number(plannedQty),
      startDate: optional(startDate),
      endDate: optional(endDate),
      status,
      remark: optional(remark),
    });
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <Box component="form" onSubmit={handleSubmit}>
        <DialogTitle>{batch ? '编辑批次' : '新增批次'}</DialogTitle>
        <DialogContent>
          <Stack spacing={3} sx={{ pt: 1 }}>
            {error && <Alert severity="error">{error}</Alert>}
            <LabeledField label="批号" value={batchNo} onChange={setBatchNo} />
            <LabeledField label="计划数量" type="number" value={plannedQty} onChange={setPlannedQty} />
            <LabeledField label="开始日期" type="date" value={startDate} onChange={setStartDate} />
            <LabeledField label="结束日期" type="date" value={endDate} onChange={setEndDate} />
            <LabeledSelect label="批次状态" value={status} options={STATUS_OPTIONS} onChange={setStatus} />
            <LabeledField label="备注" value={remark} onChange={setRemark} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>取消</Button>
          <Button type="submit" variant="contained" disabled={mutation.isPending}>
            确定
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}
