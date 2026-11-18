import { useState } from 'react';
import {
  Alert,
  Button,
  FormControlLabel,
  Paper,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  Typography,
  Chip,
} from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { ApiError } from '../../api/client';
import { downloadRecordsCsv } from '../../api/export';
import { listRecords } from '../../api/inspection';
import type { RecordQuery } from '../../api/inspection';
import type { InspectionRecordSummary, RecordResult } from '../../api/types';
import { useAuthStore } from '../../stores/authStore';
import { RESULT_LABELS, SCENE_LABELS } from '../../utils/labels';
import { shadow } from '../../theme/muiTheme';
import FormErrorSnackbar from '../../components/FormErrorSnackbar';
import LabeledField from '../../components/LabeledField';
import LabeledSelect from '../../components/LabeledSelect';
import VoidRecordDialog from './VoidRecordDialog';

const RESULT_OPTIONS: { value: RecordResult | ''; label: string }[] = [
  { value: '', label: '全部' },
  ...(Object.keys(RESULT_LABELS) as RecordResult[]).map((result) => ({ value: result, label: RESULT_LABELS[result] })),
];

function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : '网络异常，请稍后重试';
}

/** 实测值：数值型带上单位，文字型直接是结论。 */
function measuredText(record: InspectionRecordSummary): string {
  if (record.measuredValue !== null) {
    return record.stdUnit ? `${record.measuredValue} ${record.stdUnit}` : String(record.measuredValue);
  }
  if (record.textValue === 'PASS' || record.textValue === 'FAIL') return RESULT_LABELS[record.textValue];
  return '—';
}

/**
 * 标准值取记录里的快照列，不是当前标准——标准改过之后，这里显示的
 * 仍然是当时判定所依据的值（BR-02、AC-04）。
 */
function standardText(record: InspectionRecordSummary): string {
  const unit = record.stdUnit ? ` ${record.stdUnit}` : '';
  if (record.stdLower !== null && record.stdUpper !== null) {
    return `${record.stdLower} – ${record.stdUpper}${unit}`;
  }
  if (record.stdLower !== null) return `> ${record.stdLower}${unit}`;
  if (record.stdUpper !== null) return `< ${record.stdUpper}${unit}`;
  if (record.stdTarget !== null && record.stdTolerance !== null) {
    return `${record.stdTarget} ± ${record.stdTolerance}${unit}`;
  }
  return '—';
}

function ResultChip({ result }: { result: RecordResult }) {
  return (
    <Chip
      size="small"
      label={RESULT_LABELS[result]}
      color={result === 'PASS' ? 'success' : 'error'}
      aria-label={`判定结果 ${RESULT_LABELS[result]}`}
    />
  );
}

/**
 * 校验记录查询（FR-09）。与录入页分开：录入是检验员在产线上逐项抄表，
 * 记录查询是按条件回看历史，两者的使用场景、角色和操作节奏都不同，
 * 挤在一页会让录入页的筛选区与录入表相互干扰。
 */
export default function RecordListPage() {
  const role = useAuthStore((state) => state.user?.role);
  const canVoid = role === 'ADMIN';
  const canExport = role === 'ENGINEER';

  const [result, setResult] = useState<RecordResult | ''>('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [includeVoid, setIncludeVoid] = useState(false);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [voidingId, setVoidingId] = useState<number | null>(null);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

  const params: RecordQuery = {
    result: result || undefined,
    from: from || undefined,
    to: to || undefined,
    includeVoid: includeVoid || undefined,
    page: page + 1,
    pageSize,
  };

  const { data, isPending, error } = useQuery({
    queryKey: ['records', params],
    queryFn: () => listRecords(params),
  });

  const total = data?.total ?? 0;
  const rangeStart = total === 0 ? 0 : Math.min(page * pageSize + 1, total);
  const rangeEnd = total === 0 ? 0 : Math.min((page + 1) * pageSize, total);

  // 导出沿用当前筛选，但不带分页与含作废：作废记录不参与统计与导出（BR-08）
  const handleExport = async () => {
    setExportError(null);
    setExporting(true);
    try {
      await downloadRecordsCsv({ result: result || undefined, from: from || undefined, to: to || undefined });
    } catch (err) {
      setExportError(errorMessage(err));
    } finally {
      setExporting(false);
    }
  };

  const columnCount = 9 + (includeVoid ? 1 : 0) + (canVoid ? 1 : 0);

  return (
    <Stack spacing={3}>
      <Typography variant="h3" component="h1">
        校验记录
      </Typography>
      <Typography variant="body2" color="text.secondary">
        共 {total} 条记录 · 当前显示第 {rangeStart}–{rangeEnd} 条
      </Typography>
      <Stack direction="row" spacing={2} useFlexGap sx={{ flexWrap: 'wrap', alignItems: 'flex-start' }}>
        <LabeledSelect
          label="结果"
          value={result}
          options={RESULT_OPTIONS}
          onChange={(value) => {
            setResult(value);
            setPage(0);
          }}
        />
        <LabeledField
          label="开始日期"
          type="date"
          value={from}
          onChange={(value) => {
            setFrom(value);
            setPage(0);
          }}
        />
        <LabeledField
          label="结束日期"
          type="date"
          value={to}
          onChange={(value) => {
            setTo(value);
            setPage(0);
          }}
        />
        <FormControlLabel
          label="含作废"
          sx={{ marginTop: '28px' }}
          control={
            <Switch
              size="small"
              checked={includeVoid}
              onChange={(event) => {
                setIncludeVoid(event.target.checked);
                setPage(0);
              }}
            />
          }
        />
        {canExport && (
          <Button
            variant="contained"
            disabled={exporting}
            sx={{ marginLeft: 'auto', marginTop: '28px' }}
            onClick={handleExport}
          >
            导出 CSV
          </Button>
        )}
      </Stack>
      <TableContainer component={Paper} sx={{ boxShadow: shadow.card }}>
        <Table aria-label="校验记录">
          <TableHead sx={{ bgcolor: 'background.default' }}>
            <TableRow>
              <TableCell>检验时间</TableCell>
              <TableCell>批次 ID</TableCell>
              <TableCell>场景</TableCell>
              <TableCell>检验项</TableCell>
              <TableCell>标准值</TableCell>
              <TableCell>实测值</TableCell>
              <TableCell>判定</TableCell>
              <TableCell>状态</TableCell>
              <TableCell>检验员 ID</TableCell>
              {includeVoid && <TableCell>作废原因</TableCell>}
              {canVoid && <TableCell>操作</TableCell>}
            </TableRow>
          </TableHead>
          <TableBody>
            {isPending && (
              <TableRow>
                <TableCell colSpan={columnCount} align="center">
                  加载中…
                </TableCell>
              </TableRow>
            )}
            {error && (
              <TableRow>
                <TableCell colSpan={columnCount}>
                  <Alert severity="error">{errorMessage(error)}</Alert>
                </TableCell>
              </TableRow>
            )}
            {data?.records.map((record) => (
              <TableRow key={record.id} sx={record.isVoid ? { opacity: 0.6 } : undefined}>
                <TableCell>{record.inspectTime.replace('T', ' ')}</TableCell>
                <TableCell>#{record.batchId}</TableCell>
                <TableCell>{SCENE_LABELS[record.scene]}</TableCell>
                <TableCell>{record.itemName}</TableCell>
                <TableCell>{standardText(record)}</TableCell>
                <TableCell>{measuredText(record)}</TableCell>
                <TableCell>
                  <ResultChip result={record.result} />
                </TableCell>
                <TableCell>{record.isVoid ? <Chip size="small" color="default" label="已作废" /> : null}</TableCell>
                <TableCell>#{record.inspectorId}</TableCell>
                {includeVoid && <TableCell>{record.voidReason ?? '—'}</TableCell>}
                {canVoid && (
                  <TableCell>
                    {!record.isVoid && (
                      <Button size="small" onClick={() => setVoidingId(record.id)}>
                        作废 #{record.id}
                      </Button>
                    )}
                  </TableCell>
                )}
              </TableRow>
            ))}
            {data?.records.length === 0 && (
              <TableRow>
                <TableCell colSpan={columnCount} align="center">
                  没有符合条件的校验记录
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
        <TablePagination
          component="div"
          count={total}
          page={page}
          rowsPerPage={pageSize}
          rowsPerPageOptions={[10, 20, 50]}
          onPageChange={(_, nextPage) => setPage(nextPage)}
          onRowsPerPageChange={(event) => {
            setPageSize(Number(event.target.value));
            setPage(0);
          }}
        />
      </TableContainer>
      {voidingId !== null && (
        <VoidRecordDialog
          open
          recordId={voidingId}
          onClose={() => setVoidingId(null)}
          onDone={() => setVoidingId(null)}
        />
      )}
      <FormErrorSnackbar error={exportError} onClose={() => setExportError(null)} />
    </Stack>
  );
}
