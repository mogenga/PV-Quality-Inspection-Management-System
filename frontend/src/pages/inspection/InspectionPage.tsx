import { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { Link as RouterLink } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { listBatches } from '../../api/batches';
import { ApiError } from '../../api/client';
import { createRecord, judge, listTasks } from '../../api/inspection';
import { listProjects } from '../../api/projects';
import type { DefectGrade, DefectType, RecordResult, Scene, StandardTask } from '../../api/types';
import { DEFECT_GRADE_LABELS, DEFECT_TYPE_LABELS, RESULT_LABELS, SCENE_LABELS } from '../../utils/labels';
import { formatStandardValue } from '../../utils/standards';
import { findMissing, missingMessage } from '../../utils/validation';
import { shadow } from '../../theme/muiTheme';
import FormErrorSnackbar from '../../components/FormErrorSnackbar';
import LabeledSelect from '../../components/LabeledSelect';

const STORAGE_KEY = 'pvqims.inspection.filters';

const SCENE_OPTIONS: { value: Scene | ''; label: string }[] = [
  { value: '', label: '全部' },
  ...(Object.keys(SCENE_LABELS) as Scene[]).map((scene) => ({ value: scene, label: SCENE_LABELS[scene] })),
];

const DEFECT_TYPE_OPTIONS: { value: '' | DefectType; label: string }[] = [
  { value: '', label: '请选择' },
  ...(Object.keys(DEFECT_TYPE_LABELS) as DefectType[]).map((type) => ({
    value: type,
    label: DEFECT_TYPE_LABELS[type],
  })),
];

const DEFECT_GRADE_OPTIONS: { value: '' | DefectGrade; label: string }[] = [
  { value: '', label: '请选择' },
  ...(Object.keys(DEFECT_GRADE_LABELS) as DefectGrade[]).map((grade) => ({
    value: grade,
    label: DEFECT_GRADE_LABELS[grade],
  })),
];

function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : '网络异常，请稍后重试';
}

/** 空串与非法数字都返回 null；调用方据此跳过判定或提示「请输入数字」。 */
function parseMeasure(value: string): number | null {
  const trimmed = value.trim();
  if (trimmed === '') return null;
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : null;
}

type StoredFilters = { projectId: number; batchId: number; scene: Scene | '' };

/** 最近一次使用的组合只是便利功能：隐私模式下 localStorage 读写会抛，失败一律静默。 */
function readStoredFilters(): StoredFilters | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<StoredFilters> & { scene?: string };
    if (typeof parsed.projectId !== 'number' || typeof parsed.batchId !== 'number') return null;
    const scene = parsed.scene === 'IQC' || parsed.scene === 'PQC' || parsed.scene === 'OQC' ? parsed.scene : '';
    return { projectId: parsed.projectId, batchId: parsed.batchId, scene };
  } catch {
    return null;
  }
}

function writeStoredFilters(filters: StoredFilters): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(filters));
  } catch {
    // 存不下就算了，下次进页面重新选
  }
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
 * 单个待检项。输入状态放在行内而不是页面：保存成功后整行清空，
 * 重新拉 tasks 只更新「上次实测」。
 */
function TaskRow({
  task,
  batchId,
  onError,
}: {
  task: StandardTask;
  batchId: number;
  onError: (message: string) => void;
}) {
  const queryClient = useQueryClient();
  const isText = task.judgeType === 'PASS_FAIL';
  const [measured, setMeasured] = useState('');
  const [verdict, setVerdict] = useState<'' | RecordResult>('');
  const [judged, setJudged] = useState<RecordResult | null>(null);
  const [defectType, setDefectType] = useState<'' | DefectType>('');
  const [defectGrade, setDefectGrade] = useState<'' | DefectGrade>('');
  const [serialNo, setSerialNo] = useState('');

  const judgeMutation = useMutation({
    mutationFn: judge,
    onSuccess: (data) => setJudged(data.result),
    onError: (error) => {
      setJudged(null);
      onError(errorMessage(error));
    },
  });

  const saveMutation = useMutation({
    mutationFn: createRecord,
    onSuccess: () => {
      setMeasured('');
      setVerdict('');
      setJudged(null);
      setDefectType('');
      setDefectGrade('');
      setSerialNo('');
      queryClient.invalidateQueries({ queryKey: ['records'] });
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
    },
    onError: (error) => onError(errorMessage(error)),
  });

  // 失焦预览判定：输入为空或不是数字时不发请求，清掉旧结论避免留下上一个值的判定
  const previewJudge = () => {
    const value = parseMeasure(measured);
    if (value === null) {
      setJudged(null);
      return;
    }
    judgeMutation.mutate({ standardId: task.standardId, measuredValue: value });
  };

  // PASS_FAIL 的判定就是所选的文字结论，不必请求预览接口
  const chooseVerdict = (value: RecordResult) => {
    setVerdict(value);
    setJudged(value);
  };

  const handleSave = () => {
    const numericValue = isText ? null : parseMeasure(measured);
    // 输入了无法解析为数字的内容（如 `-`）：拦下并明确提示，避免 NaN 或空串当 0 落库
    if (!isText && measured.trim() !== '' && numericValue === null) {
      onError('请输入数字');
      return;
    }
    const missing = findMissing([
      isText
        ? { field: 'textValue', label: '结论', value: verdict }
        : { field: 'measuredValue', label: '实测值', value: measured },
      ...(judged === 'FAIL'
        ? [
            { field: 'defectType', label: '缺陷类型', value: defectType },
            { field: 'defectGrade', label: '缺陷等级', value: defectGrade },
          ]
        : []),
    ]);
    const message = missingMessage(missing);
    if (message !== null) {
      onError(message);
      return;
    }
    saveMutation.mutate({
      batchId,
      standardId: task.standardId,
      measuredValue: numericValue,
      textValue: isText ? verdict : null,
      serialNo: serialNo.trim() === '' ? null : serialNo.trim(),
      ...(judged === 'FAIL' ? { defectType: defectType as DefectType, defectGrade: defectGrade as DefectGrade } : {}),
    });
  };

  return (
    <>
      <TableRow>
        <TableCell>{task.itemName}</TableCell>
        <TableCell>{task.unit ?? '—'}</TableCell>
        <TableCell>{formatStandardValue(task)}</TableCell>
        <TableCell>
          {/* 人工判定没有实测值，只有上次的结论；显示结论本身就是「上次实测」 */}
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            {task.judgeType === 'PASS_FAIL' ? (
              task.lastResult ? (
                <ResultChip result={task.lastResult} />
              ) : (
                '—'
              )
            ) : (
              <>
                <Box component="span">{task.lastMeasuredValue ?? '—'}</Box>
                {task.lastResult && <ResultChip result={task.lastResult} />}
              </>
            )}
          </Stack>
        </TableCell>
        <TableCell>
          {/* 判定与输入合在一格：检验员抄表时眼睛盯着「我填的值对不对」，
              判定贴在同一格里才算即时反馈；单列一列判定离输入太远，也看不出因果关系 */}
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            {isText ? (
              (['PASS', 'FAIL'] as RecordResult[]).map((value) => (
                <Button
                  key={value}
                  size="small"
                  variant={verdict === value ? 'contained' : 'outlined'}
                  color={value === 'PASS' ? 'success' : 'error'}
                  aria-pressed={verdict === value}
                  onClick={() => chooseVerdict(value)}
                >
                  {RESULT_LABELS[value]}
                </Button>
              ))
            ) : (
              <TextField
                size="small"
                type="number"
                value={measured}
                onChange={(event) => {
                  setMeasured(event.target.value);
                  setJudged(null);
                }}
                onBlur={previewJudge}
                slotProps={{ htmlInput: { 'aria-label': `实测值 ${task.itemCode}`, step: 'any' } }}
              />
            )}
            {judged && <ResultChip result={judged} />}
          </Stack>
        </TableCell>
        <TableCell>
          <TextField
            size="small"
            value={serialNo}
            onChange={(event) => setSerialNo(event.target.value)}
            slotProps={{ htmlInput: { 'aria-label': `序列号 ${task.itemCode}` } }}
          />
        </TableCell>
        <TableCell>
          <Button
            size="small"
            variant="contained"
            disabled={saveMutation.isPending || judgeMutation.isPending}
            aria-label={`保存 ${task.itemCode}`}
            onClick={handleSave}
          >
            保存
          </Button>
        </TableCell>
      </TableRow>
      {judged === 'FAIL' && (
        <TableRow>
          <TableCell colSpan={7}>
            <Stack direction="row" spacing={2} useFlexGap sx={{ flexWrap: 'wrap', alignItems: 'flex-start' }}>
              <LabeledSelect
                label="缺陷类型"
                value={defectType}
                options={DEFECT_TYPE_OPTIONS}
                onChange={setDefectType}
              />
              <LabeledSelect
                label="缺陷等级"
                value={defectGrade}
                options={DEFECT_GRADE_OPTIONS}
                onChange={setDefectGrade}
              />
            </Stack>
          </TableCell>
        </TableRow>
      )}
    </>
  );
}

export default function InspectionPage() {
  const [projectId, setProjectId] = useState<number | null>(null);
  const [batchId, setBatchId] = useState<number | null>(null);
  const [scene, setScene] = useState<Scene | ''>('');
  const [processName, setProcessName] = useState('');
  const [pendingBatchId, setPendingBatchId] = useState<number | null>(null);
  const restoreDone = useRef(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  // 项目下拉一次加载前 100 个（接口 pageSize 上限），第 101 个及以后暂不可选
  const projectsQuery = useQuery({
    queryKey: ['projects', { page: 1, pageSize: 100 }],
    queryFn: () => listProjects({ page: 1, pageSize: 100 }),
  });
  const projects = projectsQuery.data?.records ?? [];

  const batchesQuery = useQuery({
    queryKey: ['batches', projectId, { page: 1, pageSize: 100 }],
    queryFn: () => listBatches({ projectId: projectId ?? undefined, page: 1, pageSize: 100 }),
    enabled: projectId !== null,
  });
  const batches = batchesQuery.data?.records ?? [];

  // 恢复最近一次组合（SRS 6.2）：项目列表到手后尝试一次，id 仍在选项里才恢复
  useEffect(() => {
    if (restoreDone.current || projectsQuery.isPending) return;
    restoreDone.current = true;
    const stored = readStoredFilters();
    if (!stored) return;
    if (!projects.some((project) => project.id === stored.projectId)) return;
    setProjectId(stored.projectId);
    setScene(stored.scene);
    setPendingBatchId(stored.batchId);
  }, [projectsQuery.isPending, projects]);

  useEffect(() => {
    if (pendingBatchId === null || !batchesQuery.data) return;
    setBatchId(batchesQuery.data.records.some((batch) => batch.id === pendingBatchId) ? pendingBatchId : null);
    setPendingBatchId(null);
  }, [pendingBatchId, batchesQuery.data]);

  useEffect(() => {
    if (projectId === null || batchId === null) return;
    writeStoredFilters({ projectId, batchId, scene });
  }, [projectId, batchId, scene]);

  const sceneParam = scene || undefined;
  const taskFilter = { projectId, batchId, scene: sceneParam };

  // 全量待检项只用于工序下拉：工序筛选时仍保留它，否则选项会塌缩成已选的那一项
  const allTasksQuery = useQuery({
    queryKey: ['tasks', taskFilter],
    queryFn: () => listTasks({ projectId: projectId!, batchId: batchId!, scene: sceneParam }),
    enabled: projectId !== null && batchId !== null,
  });
  const filteredTasksQuery = useQuery({
    queryKey: ['tasks', { ...taskFilter, processName }],
    queryFn: () => listTasks({ projectId: projectId!, batchId: batchId!, scene: sceneParam, processName }),
    enabled: projectId !== null && batchId !== null && processName !== '',
  });
  const activeTasksQuery = processName === '' ? allTasksQuery : filteredTasksQuery;
  const tasks = activeTasksQuery.data ?? [];

  const processOptions = [
    { value: '', label: '全部' },
    ...Array.from(new Set((allTasksQuery.data ?? []).map((task) => task.processName))).map((name) => ({
      value: name,
      label: name,
    })),
  ];

  const batchSelected = projectId !== null && batchId !== null;

  const projectOptions = [
    { value: '', label: '请选择' },
    ...projects.map((project) => ({ value: String(project.id), label: project.projectName })),
  ];
  const batchOptions = [
    { value: '', label: '请选择' },
    ...batches.map((batch) => ({ value: String(batch.id), label: batch.batchNo })),
  ];

  return (
    <Stack spacing={3}>
      <Typography variant="h3" component="h1">
        校验录入
      </Typography>
      <Typography variant="body2" color="text.secondary">
        录入完成后到
        <RouterLink to="/records" style={{ marginInline: 4 }}>
          校验记录
        </RouterLink>
        查看历史，按结果和日期筛选
      </Typography>
      {projectsQuery.error && <Alert severity="error">{errorMessage(projectsQuery.error)}</Alert>}
      {batchesQuery.error && <Alert severity="error">{errorMessage(batchesQuery.error)}</Alert>}
      {/* useFlexGap：用 gap 而非给子元素加 margin，否则 Stack 子元素选择器会重置掉按钮的 margin-top */}
      <Stack direction="row" spacing={2} useFlexGap sx={{ flexWrap: 'wrap', alignItems: 'flex-start' }}>
        <LabeledSelect
          label="项目"
          value={projectId === null ? '' : String(projectId)}
          options={projectOptions}
          onChange={(value) => {
            // 换项目后旧批次与工序没有意义
            setProjectId(value === '' ? null : Number(value));
            setBatchId(null);
            setProcessName('');
          }}
        />
        <LabeledSelect
          label="批次"
          value={batchId === null ? '' : String(batchId)}
          options={batchOptions}
          disabled={projectId === null}
          onChange={(value) => {
            setBatchId(value === '' ? null : Number(value));
            setProcessName('');
          }}
        />
        <LabeledSelect
          label="场景"
          value={scene}
          options={SCENE_OPTIONS}
          onChange={(value) => {
            setScene(value);
            setProcessName('');
          }}
        />
        <LabeledSelect
          label="工序"
          value={processName}
          options={processOptions}
          disabled={!batchSelected}
          onChange={setProcessName}
        />
      </Stack>
      {!batchSelected ? (
        <Alert severity="info">请选择项目和批次</Alert>
      ) : (
        <TableContainer component={Paper} sx={{ boxShadow: shadow.card }}>
          <Table aria-label="待检项">
            <TableHead sx={{ bgcolor: 'background.default' }}>
              <TableRow>
                <TableCell>检验项</TableCell>
                <TableCell>单位</TableCell>
                <TableCell>标准值</TableCell>
                <TableCell>上次实测</TableCell>
                <TableCell>实测值 / 结论</TableCell>
                <TableCell>序列号</TableCell>
                <TableCell>操作</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {activeTasksQuery.isPending && (
                <TableRow>
                  <TableCell colSpan={7} align="center">
                    加载中…
                  </TableCell>
                </TableRow>
              )}
              {activeTasksQuery.error && (
                <TableRow>
                  <TableCell colSpan={7}>
                    <Alert severity="error">{errorMessage(activeTasksQuery.error)}</Alert>
                  </TableCell>
                </TableRow>
              )}
              {tasks.map((task) => (
                <TaskRow key={task.standardId} task={task} batchId={batchId} onError={setSaveError} />
              ))}
              {!activeTasksQuery.isPending && !activeTasksQuery.error && tasks.length === 0 && (
                <TableRow>
                  <TableCell colSpan={7} align="center">
                    没有符合条件的待检项
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      <FormErrorSnackbar error={saveError} onClose={() => setSaveError(null)} />
    </Stack>
  );
}
