import { useState } from 'react';
import {
  Alert,
  Button,
  Chip,
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
import { listProjects } from '../../api/projects';
import { listStandards, setStandardEnabled } from '../../api/standards';
import type { StandardQuery } from '../../api/standards';
import { ApiError } from '../../api/client';
import type { Scene, StandardSummary } from '../../api/types';
import { useAuthStore } from '../../stores/authStore';
import { JUDGE_TYPE_LABELS, SCENE_LABELS } from '../../utils/labels';
import { formatStandardValue } from '../../utils/standards';
import { shadow } from '../../theme/muiTheme';
import LabeledSelect from '../../components/LabeledSelect';
import StandardFormDialog from './StandardFormDialog';

const SCENE_OPTIONS: { value: Scene | ''; label: string }[] = [
  { value: '', label: '全部' },
  ...(Object.keys(SCENE_LABELS) as Scene[]).map((scene) => ({ value: scene, label: SCENE_LABELS[scene] })),
];

const ENABLED_OPTIONS: { value: '' | 'true' | 'false'; label: string }[] = [
  { value: '', label: '全部' },
  { value: 'true', label: '启用' },
  { value: 'false', label: '停用' },
];

function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : '网络异常，请稍后重试';
}

type DialogState = { type: 'create' } | { type: 'edit'; standard: StandardSummary } | null;

export default function StandardListPage() {
  const queryClient = useQueryClient();
  const canWrite = useAuthStore((state) => state.user?.role) === 'ENGINEER';

  // 项目下拉一次加载前 100 个（接口 pageSize 上限），第 101 个及以后暂不可选
  const projectsQuery = useQuery({
    queryKey: ['projects', { page: 1, pageSize: 100 }],
    queryFn: () => listProjects({ page: 1, pageSize: 100 }),
  });
  const projects = projectsQuery.data?.records ?? [];

  const [selectedProjectId, setSelectedProjectId] = useState<number | null>(null);
  const [scene, setScene] = useState<Scene | ''>('');
  const [keyword, setKeyword] = useState('');
  const [enabled, setEnabled] = useState<'' | 'true' | 'false'>('');
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [dialog, setDialog] = useState<DialogState>(null);
  const [toggleError, setToggleError] = useState<string | null>(null);

  // 只有用户还没选过才落到第一条；用户选过之后，重新拉取项目不覆盖他的选择
  const projectId = selectedProjectId ?? projects[0]?.id ?? null;
  // 选中项目是 id 而非快照：项目被编辑成已关闭后，标准区要立即跟着只读
  const projectReadOnly = projects.find((project) => project.id === projectId)?.status === 'CLOSED';

  const params: StandardQuery = {
    projectId: projectId ?? undefined,
    scene: scene || undefined,
    keyword: keyword || undefined,
    enabled: enabled === '' ? undefined : enabled === 'true',
    page: page + 1,
    pageSize,
  };

  const { data, isPending, error } = useQuery({
    queryKey: ['standards', params],
    queryFn: () => listStandards(params),
    enabled: projectId !== null,
  });

  const toggle = useMutation({
    mutationFn: ({ id, isEnabled }: { id: number; isEnabled: boolean }) => setStandardEnabled(id, isEnabled),
    onSuccess: () => {
      setToggleError(null);
      queryClient.invalidateQueries({ queryKey: ['standards'] });
    },
    onError: (err) => setToggleError(errorMessage(err)),
  });

  const title = (
    <Typography variant="h3" component="h1">
      校验标准配置
    </Typography>
  );

  if (projectsQuery.isPending) return <Stack spacing={3}>{title}</Stack>;
  if (projectsQuery.error) {
    return (
      <Stack spacing={3}>
        {title}
        <Alert severity="error">{errorMessage(projectsQuery.error)}</Alert>
      </Stack>
    );
  }
  if (projects.length === 0) {
    return (
      <Stack spacing={3}>
        {title}
        <Alert severity="info">还没有项目，请先在『项目与批次』中创建项目</Alert>
      </Stack>
    );
  }

  const total = data?.total ?? 0;

  const projectOptions = projects.map((project) => ({ value: String(project.id), label: project.projectName }));

  return (
    <Stack spacing={3}>
      {title}
      {toggleError && <Alert severity="error">{toggleError}</Alert>}
      {projectReadOnly && <Alert severity="warning">项目已关闭，标准只读</Alert>}
      {/* useFlexGap：用 gap 而非给子元素加 margin，否则 Stack 子元素选择器会重置掉按钮的 margin-top */}
      <Stack direction="row" spacing={2} useFlexGap sx={{ flexWrap: 'wrap', alignItems: 'flex-start' }}>
        <LabeledSelect
          label="项目"
          value={String(projectId)}
          options={projectOptions}
          onChange={(value) => {
            setSelectedProjectId(Number(value));
            setPage(0);
          }}
        />
        <LabeledSelect
          label="场景"
          value={scene}
          options={SCENE_OPTIONS}
          onChange={(value) => {
            setScene(value);
            setPage(0);
          }}
        />
        <Stack spacing={1} sx={{ minWidth: 180 }}>
          <Typography component="label" htmlFor="standard-keyword" variant="body2" sx={{ fontWeight: 550 }}>
            关键字
          </Typography>
          <TextField
            id="standard-keyword"
            size="small"
            value={keyword}
            onChange={(event) => {
              setKeyword(event.target.value);
              setPage(0);
            }}
          />
        </Stack>
        <LabeledSelect
          label="状态"
          value={enabled}
          options={ENABLED_OPTIONS}
          onChange={(value) => {
            setEnabled(value);
            setPage(0);
          }}
        />
        {canWrite && (
          // 对齐到输入框本体而非标签行：跳过标签高度 + 标签与框的间距（共 28px）
          <Button
            variant="contained"
            disabled={projectReadOnly}
            sx={{ marginLeft: 'auto', marginTop: '28px' }}
            onClick={() => setDialog({ type: 'create' })}
          >
            新增标准
          </Button>
        )}
      </Stack>
      <TableContainer component={Paper} sx={{ boxShadow: shadow.card }}>
        <Table>
          <TableHead sx={{ bgcolor: 'background.default' }}>
            <TableRow>
              <TableCell>检验项编码</TableCell>
              <TableCell>检验项名称</TableCell>
              <TableCell>场景</TableCell>
              <TableCell>工序</TableCell>
              <TableCell>判定方式</TableCell>
              <TableCell>标准值</TableCell>
              <TableCell>排序</TableCell>
              <TableCell>状态</TableCell>
              <TableCell>操作</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isPending && (
              <TableRow>
                <TableCell colSpan={9} align="center">
                  加载中…
                </TableCell>
              </TableRow>
            )}
            {error && (
              <TableRow>
                <TableCell colSpan={9}>
                  <Alert severity="error">{errorMessage(error)}</Alert>
                </TableCell>
              </TableRow>
            )}
            {data?.records.map((record) => (
              <TableRow key={record.id}>
                <TableCell>{record.itemCode}</TableCell>
                <TableCell>{record.itemName}</TableCell>
                <TableCell>{SCENE_LABELS[record.scene]}</TableCell>
                <TableCell>{record.processName}</TableCell>
                <TableCell>{JUDGE_TYPE_LABELS[record.judgeType]}</TableCell>
                <TableCell>{formatStandardValue(record)}</TableCell>
                <TableCell>{record.sortOrder}</TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    label={record.isEnabled ? '启用' : '停用'}
                    color={record.isEnabled ? 'success' : 'default'}
                  />
                </TableCell>
                <TableCell>
                  {canWrite && (
                    <Stack direction="row" spacing={1}>
                      <Button
                        size="small"
                        disabled={projectReadOnly}
                        aria-label={`编辑标准 ${record.itemCode}`}
                        onClick={() => setDialog({ type: 'edit', standard: record })}
                      >
                        编辑
                      </Button>
                      <Button
                        size="small"
                        disabled={projectReadOnly}
                        aria-label={`${record.isEnabled ? '停用' : '启用'}标准 ${record.itemCode}`}
                        onClick={() => toggle.mutate({ id: record.id, isEnabled: !record.isEnabled })}
                      >
                        {record.isEnabled ? '停用' : '启用'}
                      </Button>
                    </Stack>
                  )}
                </TableCell>
              </TableRow>
            ))}
            {data?.records.length === 0 && (
              <TableRow>
                <TableCell colSpan={9} align="center">
                  没有符合条件的标准
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
      {dialog && projectId !== null && (
        <StandardFormDialog
          projectId={projectId}
          standard={dialog.type === 'edit' ? dialog.standard : null}
          onClose={() => setDialog(null)}
        />
      )}
    </Stack>
  );
}
