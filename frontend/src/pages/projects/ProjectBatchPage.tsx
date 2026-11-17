import { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  Divider,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { deleteBatch, listBatches } from '../../api/batches';
import { deleteProject, listProjects } from '../../api/projects';
import type { ProjectQuery } from '../../api/projects';
import { ApiError } from '../../api/client';
import type { BatchStatus, BatchSummary, ProjectStatus, ProjectSummary } from '../../api/types';
import { useAuthStore } from '../../stores/authStore';
import { BATCH_STATUS_LABELS, PROJECT_STATUS_LABELS } from '../../utils/labels';
import { shadow } from '../../theme/muiTheme';
import LabeledSelect from '../../components/LabeledSelect';
import ConfirmDialog from '../../components/ConfirmDialog';
import ProjectFormDialog from './ProjectFormDialog';
import BatchFormDialog from './BatchFormDialog';

const PROJECT_STATUS_OPTIONS: { value: ProjectStatus | ''; label: string }[] = [
  { value: '', label: '全部' },
  ...(Object.keys(PROJECT_STATUS_LABELS) as ProjectStatus[]).map((status) => ({
    value: status,
    label: PROJECT_STATUS_LABELS[status],
  })),
];

const BATCH_STATUS_OPTIONS: { value: BatchStatus | ''; label: string }[] = [
  { value: '', label: '全部' },
  ...(Object.keys(BATCH_STATUS_LABELS) as BatchStatus[]).map((status) => ({
    value: status,
    label: BATCH_STATUS_LABELS[status],
  })),
];

function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : '网络异常，请稍后重试';
}

type DialogState =
  | { type: 'create-project' }
  | { type: 'edit-project'; project: ProjectSummary }
  | { type: 'delete-project'; project: ProjectSummary }
  | { type: 'create-batch' }
  | { type: 'edit-batch'; batch: BatchSummary }
  | { type: 'delete-batch'; batch: BatchSummary }
  | null;

export default function ProjectBatchPage() {
  const queryClient = useQueryClient();
  const canWrite = useAuthStore((state) => state.user?.role) === 'ENGINEER';

  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<ProjectStatus | ''>('');
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [selectedProjectId, setSelectedProjectId] = useState<number | null>(null);
  const [batchStatus, setBatchStatus] = useState<BatchStatus | ''>('');
  const [batchPage, setBatchPage] = useState(0);
  const [batchPageSize, setBatchPageSize] = useState(10);
  const [dialog, setDialog] = useState<DialogState>(null);

  const params: ProjectQuery = {
    keyword: keyword || undefined,
    status: status || undefined,
    page: page + 1,
    pageSize,
  };

  const { data, isPending, error } = useQuery({
    queryKey: ['projects', params],
    queryFn: () => listProjects(params),
  });

  // 选中项目是 id 而非快照：项目被编辑成已关闭后，批次区要立即跟着只读；
  // 项目被删除后行消失，选中态自然落空
  const selectedProject = data?.records.find((record) => record.id === selectedProjectId) ?? null;
  const batchReadOnly = selectedProject?.status === 'CLOSED';

  const batchParams = {
    projectId: selectedProject?.id,
    status: batchStatus || undefined,
    page: batchPage + 1,
    pageSize: batchPageSize,
  };

  const {
    data: batchData,
    isPending: batchPending,
    error: batchError,
  } = useQuery({
    queryKey: ['batches', selectedProject?.id, batchParams],
    queryFn: () => listBatches(batchParams),
    enabled: selectedProject !== null,
  });

  const projectDelete = useMutation({
    mutationFn: deleteProject,
    onSuccess: () => {
      // 删掉非首页唯一一行后页码会越界：退回上一页，避免空表与越界页码并存
      setPage((p) => (data?.records.length === 1 && p > 0 ? p - 1 : p));
      queryClient.invalidateQueries({ queryKey: ['projects'] });
      setDialog(null);
    },
  });

  const batchDelete = useMutation({
    mutationFn: deleteBatch,
    onSuccess: () => {
      setBatchPage((p) => (batchData?.records.length === 1 && p > 0 ? p - 1 : p));
      queryClient.invalidateQueries({ queryKey: ['batches'] });
      setDialog(null);
    },
  });

  const total = data?.total ?? 0;
  // 空结果或编辑后收缩到更小的末页时，起点不能越过总数；空列表显示 0–0
  const rangeStart = total === 0 ? 0 : Math.min(page * pageSize + 1, total);
  const rangeEnd = total === 0 ? 0 : Math.min((page + 1) * pageSize, total);

  const openBatches = (project: ProjectSummary) => {
    setSelectedProjectId(project.id);
    // 换项目后旧筛选与页码没有意义
    setBatchStatus('');
    setBatchPage(0);
  };

  const openDeleteProject = (project: ProjectSummary) => {
    projectDelete.reset();
    setDialog({ type: 'delete-project', project });
  };

  const openDeleteBatch = (batch: BatchSummary) => {
    batchDelete.reset();
    setDialog({ type: 'delete-batch', batch });
  };

  const confirmDeleteProject = () => {
    if (dialog?.type !== 'delete-project') return;
    const target = dialog.project;
    projectDelete.mutate(target.id, {
      onSuccess: () => {
        if (target.id === selectedProjectId) setSelectedProjectId(null);
      },
    });
  };

  return (
    <Stack spacing={3}>
      <Typography variant="h3" component="h1">
        项目与批次
      </Typography>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', mb: 2 }}>
        <Typography variant="body2" color="text.secondary">
          共 {total} 个项目
        </Typography>
        <Typography variant="caption" color="text.secondary">
          当前显示第 {rangeStart}–{rangeEnd} 条
        </Typography>
      </Box>
      {/* useFlexGap：用 gap 而非给子元素加 margin，否则 Stack 子元素选择器会重置掉按钮的 margin-top */}
      <Stack direction="row" spacing={2} useFlexGap sx={{ flexWrap: 'wrap', alignItems: 'flex-start' }}>
        <Stack spacing={1} sx={{ minWidth: 180 }}>
          <Typography component="label" htmlFor="project-keyword" variant="body2" sx={{ fontWeight: 550 }}>
            关键字
          </Typography>
          <TextField
            id="project-keyword"
            size="small"
            value={keyword}
            onChange={(event) => {
              setKeyword(event.target.value);
              setPage(0);
            }}
          />
        </Stack>
        <LabeledSelect
          label="项目状态"
          value={status}
          options={PROJECT_STATUS_OPTIONS}
          onChange={(value) => {
            setStatus(value);
            setPage(0);
          }}
        />
        {canWrite && (
          // 对齐到输入框本体而非标签行：跳过标签高度 + 标签与框的间距（共 28px）
          <Button
            variant="contained"
            sx={{ marginLeft: 'auto', marginTop: '28px' }}
            onClick={() => setDialog({ type: 'create-project' })}
          >
            新增项目
          </Button>
        )}
      </Stack>
      <TableContainer component={Paper} sx={{ boxShadow: shadow.card }}>
        <Table>
          <TableHead sx={{ bgcolor: 'background.default' }}>
            <TableRow>
              <TableCell>项目编号</TableCell>
              <TableCell>项目名称</TableCell>
              <TableCell>组件型号</TableCell>
              <TableCell>客户</TableCell>
              <TableCell>状态</TableCell>
              <TableCell>创建时间</TableCell>
              <TableCell>操作</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isPending && (
              <TableRow>
                <TableCell colSpan={7} align="center">
                  加载中…
                </TableCell>
              </TableRow>
            )}
            {error && (
              <TableRow>
                <TableCell colSpan={7}>
                  <Alert severity="error">{errorMessage(error)}</Alert>
                </TableCell>
              </TableRow>
            )}
            {data?.records.map((record) => (
              <TableRow key={record.id}>
                <TableCell>{record.projectCode}</TableCell>
                <TableCell>{record.projectName}</TableCell>
                <TableCell>{record.productModel ?? '—'}</TableCell>
                <TableCell>{record.customerName ?? '—'}</TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    label={PROJECT_STATUS_LABELS[record.status]}
                    color={record.status === 'ACTIVE' ? 'success' : 'default'}
                  />
                </TableCell>
                <TableCell>{record.createdAt.replace('T', ' ')}</TableCell>
                <TableCell>
                  <Stack direction="row" spacing={1}>
                    <Button
                      size="small"
                      aria-label={`查看批次 ${record.projectCode}`}
                      onClick={() => openBatches(record)}
                    >
                      查看批次
                    </Button>
                    {canWrite && (
                      <>
                        <Button
                          size="small"
                          aria-label={`编辑项目 ${record.projectCode}`}
                          onClick={() => setDialog({ type: 'edit-project', project: record })}
                        >
                          编辑
                        </Button>
                        <Button
                          size="small"
                          aria-label={`删除项目 ${record.projectCode}`}
                          onClick={() => openDeleteProject(record)}
                        >
                          删除
                        </Button>
                      </>
                    )}
                  </Stack>
                </TableCell>
              </TableRow>
            ))}
            {data?.records.length === 0 && (
              <TableRow>
                <TableCell colSpan={7} align="center">
                  没有符合条件的项目
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
      {selectedProject && (
        <>
          <Divider />
          <Typography variant="h2" component="h2">
            批次 · {selectedProject.projectName}
          </Typography>
          {batchReadOnly && <Alert severity="warning">项目已关闭，批次只读</Alert>}
          <Stack direction="row" spacing={2} useFlexGap sx={{ flexWrap: 'wrap', alignItems: 'flex-start' }}>
            <LabeledSelect
              label="批次状态"
              value={batchStatus}
              options={BATCH_STATUS_OPTIONS}
              onChange={(value) => {
                setBatchStatus(value);
                setBatchPage(0);
              }}
            />
            {canWrite && (
              <Button
                variant="contained"
                disabled={batchReadOnly}
                sx={{ marginLeft: 'auto', marginTop: '28px' }}
                onClick={() => setDialog({ type: 'create-batch' })}
              >
                新增批次
              </Button>
            )}
          </Stack>
          <TableContainer component={Paper} sx={{ boxShadow: shadow.card }}>
            <Table>
              <TableHead sx={{ bgcolor: 'background.default' }}>
                <TableRow>
                  <TableCell>批号</TableCell>
                  <TableCell>计划数量</TableCell>
                  <TableCell>开始日期</TableCell>
                  <TableCell>结束日期</TableCell>
                  <TableCell>状态</TableCell>
                  <TableCell>操作</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {batchPending && (
                  <TableRow>
                    <TableCell colSpan={6} align="center">
                      加载中…
                    </TableCell>
                  </TableRow>
                )}
                {batchError && (
                  <TableRow>
                    <TableCell colSpan={6}>
                      <Alert severity="error">{errorMessage(batchError)}</Alert>
                    </TableCell>
                  </TableRow>
                )}
                {batchData?.records.map((record) => (
                  <TableRow key={record.id}>
                    <TableCell>{record.batchNo}</TableCell>
                    <TableCell>{record.plannedQty}</TableCell>
                    <TableCell>{record.startDate ?? '—'}</TableCell>
                    <TableCell>{record.endDate ?? '—'}</TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        label={BATCH_STATUS_LABELS[record.status]}
                        color={record.status === 'PRODUCING' ? 'success' : 'default'}
                      />
                    </TableCell>
                    <TableCell>
                      {canWrite && (
                        <Stack direction="row" spacing={1}>
                          <Button
                            size="small"
                            disabled={batchReadOnly}
                            aria-label={`编辑批次 ${record.batchNo}`}
                            onClick={() => setDialog({ type: 'edit-batch', batch: record })}
                          >
                            编辑
                          </Button>
                          <Button
                            size="small"
                            disabled={batchReadOnly}
                            aria-label={`删除批次 ${record.batchNo}`}
                            onClick={() => openDeleteBatch(record)}
                          >
                            删除
                          </Button>
                        </Stack>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
                {batchData?.records.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={6} align="center">
                      没有符合条件的批次
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
            <TablePagination
              component="div"
              count={batchData?.total ?? 0}
              page={batchPage}
              rowsPerPage={batchPageSize}
              rowsPerPageOptions={[10, 20, 50]}
              onPageChange={(_, nextPage) => setBatchPage(nextPage)}
              onRowsPerPageChange={(event) => {
                setBatchPageSize(Number(event.target.value));
                setBatchPage(0);
              }}
            />
          </TableContainer>
        </>
      )}
      {(dialog?.type === 'create-project' || dialog?.type === 'edit-project') && (
        <ProjectFormDialog
          project={dialog.type === 'edit-project' ? dialog.project : null}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog?.type === 'delete-project' && (
        <ConfirmDialog
          open
          title="删除项目"
          content={`确定删除项目「${dialog.project.projectName}」？有批次或标准的项目不能删除。`}
          onCancel={() => setDialog(null)}
          onConfirm={confirmDeleteProject}
          pending={projectDelete.isPending}
          error={projectDelete.error ? errorMessage(projectDelete.error) : null}
        />
      )}
      {(dialog?.type === 'create-batch' || dialog?.type === 'edit-batch') && selectedProject && (
        <BatchFormDialog
          projectId={selectedProject.id}
          batch={dialog.type === 'edit-batch' ? dialog.batch : null}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog?.type === 'delete-batch' && (
        <ConfirmDialog
          open
          title="删除批次"
          content={`确定删除批次「${dialog.batch.batchNo}」？有检验记录的批次不能删除。`}
          onCancel={() => setDialog(null)}
          onConfirm={() => batchDelete.mutate(dialog.batch.id)}
          pending={batchDelete.isPending}
          error={batchDelete.error ? errorMessage(batchDelete.error) : null}
        />
      )}
    </Stack>
  );
}
