import { useState } from 'react';
import type { FormEvent } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  Typography,
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '../../api/client';
import { closeDefect, listDefects, processDefect, verifyDefect } from '../../api/defects';
import type { DefectQuery } from '../../api/defects';
import type { DefectGrade, DefectStatus } from '../../api/types';
import { useAuthStore } from '../../stores/authStore';
import { DEFECT_GRADE_LABELS, DEFECT_STATUS_LABELS, DEFECT_TYPE_LABELS, SCENE_LABELS } from '../../utils/labels';
import { findMissing, missingMessage } from '../../utils/validation';
import { shadow } from '../../theme/muiTheme';
import FormErrorSnackbar from '../../components/FormErrorSnackbar';
import LabeledField from '../../components/LabeledField';
import LabeledSelect from '../../components/LabeledSelect';

const GRADE_OPTIONS: { value: DefectGrade | ''; label: string }[] = [
  { value: '', label: '全部' },
  ...(Object.keys(DEFECT_GRADE_LABELS) as DefectGrade[]).map((grade) => ({
    value: grade,
    label: DEFECT_GRADE_LABELS[grade],
  })),
];

const STATUS_OPTIONS: { value: DefectStatus | ''; label: string }[] = [
  { value: '', label: '全部' },
  ...(Object.keys(DEFECT_STATUS_LABELS) as DefectStatus[]).map((status) => ({
    value: status,
    label: DEFECT_STATUS_LABELS[status],
  })),
];

const GRADE_COLORS: Record<DefectGrade, 'error' | 'warning' | 'default'> = {
  CRITICAL: 'error',
  MAJOR: 'warning',
  MINOR: 'default',
};

function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : '网络异常，请稍后重试';
}

/**
 * 一次流转动作。
 *
 * `needsDialog: false` 的动作点一下就生效：认领只是把责任人写成当前工程师，
 * 没有任何信息需要录入，弹一个空表单只会让人以为非填不可。
 */
type DefectAction = {
  title: string;
  needsDialog: boolean;
  noteRequired: boolean;
  run: (id: number, note?: string) => Promise<void>;
};

/**
 * 行内动作按状态渲染。按钮文案即目标状态：VERIFIED 行上的「验证通过」本身就是
 * 关闭那一步，不再给独立的「关闭」按钮——否则工程师会去 OPEN 行上找关闭按钮。
 */
function actionsFor(status: DefectStatus): DefectAction[] {
  switch (status) {
    case 'OPEN':
      return [{ title: '认领', needsDialog: false, noteRequired: false, run: processDefect }];
    case 'PROCESSING':
      return [{ title: '提交整改', needsDialog: true, noteRequired: false, run: verifyDefect }];
    case 'VERIFIED':
      return [
        { title: '验证通过', needsDialog: true, noteRequired: true, run: closeDefect },
        { title: '验证不通过', needsDialog: true, noteRequired: true, run: processDefect },
      ];
    case 'CLOSED':
      return [];
    default:
      // 后端枚举若新增状态，未知取值不渲染按钮，而不是返回 undefined 让 .map 抛错
      return [];
  }
}

function DefectActionDialog({
  defectId,
  itemName,
  batchNo,
  action,
  onClose,
}: {
  defectId: number;
  itemName: string;
  batchNo: string;
  action: DefectAction;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [note, setNote] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [invalid, setInvalid] = useState(false);

  const mutation = useMutation({
    // 不填说明时传 undefined，接口层统一送 {"note":null}：后端不会拿空串覆盖上一环节的说明
    mutationFn: (value: string) => action.run(defectId, value.trim() === '' ? undefined : value.trim()),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['defects'] });
      onClose();
    },
    onError: (err) => setError(errorMessage(err)),
  });

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    const missing = findMissing(action.noteRequired ? [{ field: 'note', label: '处理说明', value: note }] : []);
    const message = missingMessage(missing);
    setInvalid(missing.length > 0);
    if (message !== null) {
      setError(message);
      return;
    }
    mutation.mutate(note);
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <Box component="form" onSubmit={handleSubmit}>
        <DialogTitle>
          {action.title} · {itemName} / {batchNo}
        </DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            <LabeledField
              label={action.noteRequired ? '处理说明' : '处理说明（选填）'}
              value={note}
              onChange={setNote}
              required={action.noteRequired}
              error={invalid}
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>取消</Button>
          <Button type="submit" variant="contained" disabled={mutation.isPending}>
            确定
          </Button>
        </DialogActions>
        <FormErrorSnackbar error={error} onClose={() => setError(null)} />
      </Box>
    </Dialog>
  );
}

/**
 * 缺陷处理（FR-11、FR-12）。列表对全部角色开放，流转仅工程师可操作：
 * 前端只控制按钮可见性，后端已按角色强制（SRS 4.8）。
 */
export default function DefectListPage() {
  const canWrite = useAuthStore((state) => state.user?.role) === 'ENGINEER';

  const [grade, setGrade] = useState<DefectGrade | ''>('');
  const [status, setStatus] = useState<DefectStatus | ''>('');
  const [processName, setProcessName] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [pending, setPending] = useState<{
    defectId: number;
    itemName: string;
    batchNo: string;
    action: DefectAction;
  } | null>(null);
  const [directError, setDirectError] = useState<string | null>(null);
  const queryClient = useQueryClient();

  // 无需说明的动作（认领）直接提交：不弹窗，失败走页级提示条
  const directMutation = useMutation({
    mutationFn: ({ defectId, action }: { defectId: number; action: DefectAction }) => action.run(defectId),
    onSuccess: () => {
      setDirectError(null);
      queryClient.invalidateQueries({ queryKey: ['defects'] });
    },
    onError: (err) => setDirectError(errorMessage(err)),
  });

  const filters = {
    grade: grade || undefined,
    status: status || undefined,
    from: from || undefined,
    to: to || undefined,
  };
  const params: DefectQuery = {
    ...filters,
    processName: processName || undefined,
    page: page + 1,
    pageSize,
  };

  const { data, isPending, error } = useQuery({
    queryKey: ['defects', params],
    queryFn: () => listDefects(params),
  });

  // 工序选项来自第二次查询：保留等级/状态/日期，但不带 processName。
  // 若取自当前页，选中某工序后结果全被筛成该工序，下拉会塌缩成「全部 + 已选」，
  // 分页时甚至选不到不在当前页的工序。
  const optionsParams: DefectQuery = { ...filters, page: 1, pageSize: 100 };
  const { data: optionsData } = useQuery({
    queryKey: ['defects', 'process-options', optionsParams],
    queryFn: () => listDefects(optionsParams),
  });

  // 已选工序即使不在选项结果里也要补进去——筛选到空结果时下拉会显示空白，看起来像没选。
  const processNames = Array.from(new Set((optionsData?.records ?? []).map((defect) => defect.processName)));
  if (processName !== '' && !processNames.includes(processName)) processNames.push(processName);
  const processOptions = [{ value: '', label: '全部' }, ...processNames.map((name) => ({ value: name, label: name }))];

  const total = data?.total ?? 0;
  const rangeStart = total === 0 ? 0 : Math.min(page * pageSize + 1, total);
  const rangeEnd = total === 0 ? 0 : Math.min((page + 1) * pageSize, total);
  // 操作列仅工程师可见，表头与空态 colSpan 一并跟着走
  const columnCount = 12 + (canWrite ? 1 : 0);

  return (
    <Stack spacing={3}>
      <Typography variant="h3" component="h1">
        缺陷处理
      </Typography>
      <Typography variant="body2" color="text.secondary">
        共 {total} 条缺陷 · 当前显示第 {rangeStart}–{rangeEnd} 条
      </Typography>
      <Stack direction="row" spacing={2} useFlexGap sx={{ flexWrap: 'wrap', alignItems: 'flex-start' }}>
        <LabeledSelect
          label="等级"
          value={grade}
          options={GRADE_OPTIONS}
          onChange={(value) => {
            setGrade(value);
            setPage(0);
          }}
        />
        <LabeledSelect
          label="状态"
          value={status}
          options={STATUS_OPTIONS}
          onChange={(value) => {
            setStatus(value);
            setPage(0);
          }}
        />
        <LabeledSelect
          label="工序"
          value={processName}
          options={processOptions}
          onChange={(value) => {
            setProcessName(value);
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
      </Stack>
      <TableContainer component={Paper} sx={{ boxShadow: shadow.card }}>
        <Table aria-label="缺陷列表">
          <TableHead sx={{ bgcolor: 'background.default' }}>
            <TableRow>
              <TableCell>项目</TableCell>
              <TableCell>批号</TableCell>
              <TableCell>场景</TableCell>
              <TableCell>工序</TableCell>
              <TableCell>检验项</TableCell>
              <TableCell>缺陷类型</TableCell>
              <TableCell>等级</TableCell>
              <TableCell>状态</TableCell>
              <TableCell>检验时间</TableCell>
              <TableCell>检验员</TableCell>
              <TableCell>处理说明</TableCell>
              <TableCell>处理人</TableCell>
              {canWrite && <TableCell>操作</TableCell>}
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
            {data?.records.map((defect) => (
              <TableRow key={defect.defectId}>
                <TableCell>{defect.projectName}</TableCell>
                <TableCell>{defect.batchNo}</TableCell>
                <TableCell>{SCENE_LABELS[defect.scene]}</TableCell>
                <TableCell>{defect.processName}</TableCell>
                <TableCell>{defect.itemName}</TableCell>
                <TableCell>{DEFECT_TYPE_LABELS[defect.defectType]}</TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    label={DEFECT_GRADE_LABELS[defect.defectGrade]}
                    color={GRADE_COLORS[defect.defectGrade]}
                  />
                </TableCell>
                <TableCell>{DEFECT_STATUS_LABELS[defect.defectStatus]}</TableCell>
                <TableCell>{defect.inspectTime.replace('T', ' ')}</TableCell>
                <TableCell>{defect.inspectorName}</TableCell>
                <TableCell>{defect.handleNote ?? '—'}</TableCell>
                <TableCell>{defect.handlerName ?? '—'}</TableCell>
                {canWrite && (
                  <TableCell>
                    <Stack direction="row" spacing={1}>
                      {actionsFor(defect.defectStatus).map((action) => (
                        <Button
                          key={action.title}
                          size="small"
                          aria-label={`${action.title}缺陷 ${defect.defectId}`}
                          disabled={directMutation.isPending}
                          onClick={() =>
                            action.needsDialog
                              ? setPending({
                                  defectId: defect.defectId,
                                  itemName: defect.itemName,
                                  batchNo: defect.batchNo,
                                  action,
                                })
                              : directMutation.mutate({ defectId: defect.defectId, action })
                          }
                        >
                          {action.title}
                        </Button>
                      ))}
                    </Stack>
                  </TableCell>
                )}
              </TableRow>
            ))}
            {data?.records.length === 0 && (
              <TableRow>
                <TableCell colSpan={columnCount} align="center">
                  没有符合条件的缺陷
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
      {pending && (
        <DefectActionDialog
          defectId={pending.defectId}
          itemName={pending.itemName}
          batchNo={pending.batchNo}
          action={pending.action}
          onClose={() => setPending(null)}
        />
      )}
      <FormErrorSnackbar error={directError} onClose={() => setDirectError(null)} />
    </Stack>
  );
}
