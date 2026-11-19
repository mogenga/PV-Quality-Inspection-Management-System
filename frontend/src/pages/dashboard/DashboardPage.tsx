import { useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import {
  Alert,
  Box,
  Button,
  Paper,
  Snackbar,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import type { EChartsOption } from 'echarts';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '../../api/client';
import { listBatches } from '../../api/batches';
import {
  getBatchGauge,
  getControlChart,
  getDefectDuration,
  getDefectGrade,
  getInspectorWorkload,
  getPareto,
  getPassRateTrend,
  getProcessComparison,
  getSummary,
} from '../../api/dashboard';
import { seedDemoData } from '../../api/demo';
import { listProjects } from '../../api/projects';
import { listStandards } from '../../api/standards';
import { useAuthStore } from '../../stores/authStore';
import { shadow } from '../../theme/muiTheme';
import ConfirmDialog from '../../components/ConfirmDialog';
import EChart from '../../components/EChart';
import LabeledField from '../../components/LabeledField';
import LabeledSelect from '../../components/LabeledSelect';
import {
  controlOption,
  durationOption,
  gaugeOption,
  gradeOption,
  paretoOption,
  processOption,
  trendOption,
  workloadOption,
} from './options';

function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : '网络异常，请稍后重试';
}

/** 指标卡：标题 + 数值。数值不是文档标题，用 p 承载 h2 的字号。 */
function MetricCard({ title, value }: { title: string; value: ReactNode }) {
  return (
    <Paper sx={{ boxShadow: shadow.card, p: 3 }}>
      <Stack spacing={1}>
        <Typography variant="body2" color="text.secondary">
          {title}
        </Typography>
        <Typography variant="h2" component="p">
          {value}
        </Typography>
      </Stack>
    </Paper>
  );
}

/**
 * 图表卡片外壳：标题 + 图。状态优先顺序：错误 → 加载 → 空 → 图。
 *
 * `option === null` 表示后端没有数据，显示空状态而不是空白坐标轴（SRS 6.3）；
 * 请求失败时 `option` 同样为 null，若不先判 `error` 就会把故障显示成「暂无数据」。
 */
function ChartCard({
  title,
  option,
  loading = false,
  error = null,
  emptyText = '暂无数据',
}: {
  title: string;
  option: EChartsOption | null;
  loading?: boolean;
  error?: string | null;
  emptyText?: string;
}) {
  return (
    <Paper sx={{ boxShadow: shadow.card, p: 3 }}>
      <Stack spacing={2}>
        <Typography variant="h3" component="h2">
          {title}
        </Typography>
        {error !== null ? (
          <Alert severity="error">{error}</Alert>
        ) : loading ? (
          <Typography variant="body2" color="text.secondary">
            加载中…
          </Typography>
        ) : option === null ? (
          <Alert severity="info">{emptyText}</Alert>
        ) : (
          <EChart option={option} ariaLabel={title} />
        )}
      </Stack>
    </Paper>
  );
}

/**
 * 质量看板（FR-13、FR-14、P6）：指标卡、8 张图与批次合格率汇总表。
 * 筛选条件对同一页上的每张图与指标卡联动；控制图按检验项（标准）取数，
 * 不参与项目/批次/时间筛选（后端接口只接受 standardId）。
 */
export default function DashboardPage() {
  const queryClient = useQueryClient();
  // FR-16：演示数据初始化仅管理员可用，按钮按角色渲染；真正的拦截在后端接口上
  const isAdmin = useAuthStore((state) => state.user?.role) === 'ADMIN';
  const [seedDialogOpen, setSeedDialogOpen] = useState(false);
  const [seedMessage, setSeedMessage] = useState<string | null>(null);
  // 下拉持有字符串（LabeledSelect 只吃 string），发请求时才转成数字
  const [projectId, setProjectId] = useState('');
  const [batchId, setBatchId] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  // 控制图选中的标准，以及仪表盘选中的批次；均默认为空，区别见下
  const [standardId, setStandardId] = useState('');
  const [gaugeBatchId, setGaugeBatchId] = useState('');

  const seedMutation = useMutation({
    mutationFn: seedDemoData,
    onSuccess: (result) => {
      setSeedDialogOpen(false);
      setSeedMessage(`已生成 ${result.recordCount} 条校验记录`);
      // 八张图与指标卡都查 'dashboard'，一次失效全部重拉
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });

  const filters = {
    projectId: projectId === '' ? undefined : Number(projectId),
    batchId: batchId === '' ? undefined : Number(batchId),
    from: from || undefined,
    to: to || undefined,
  };

  const projectsQuery = useQuery({
    queryKey: ['projects', { page: 1, pageSize: 100 }],
    queryFn: () => listProjects({ page: 1, pageSize: 100 }),
  });
  const projects = projectsQuery.data?.records ?? [];

  const selectedProjectId = filters.projectId;
  const batchesQuery = useQuery({
    queryKey: ['batches', selectedProjectId ?? null, { page: 1, pageSize: 100 }],
    queryFn: () => listBatches({ projectId: selectedProjectId, page: 1, pageSize: 100 }),
    enabled: selectedProjectId !== undefined,
  });
  const batches = batchesQuery.data?.records ?? [];

  // 控制图只画数值型标准：文字型判定方式没有实测值（SRS 6.3）
  const standardsQuery = useQuery({
    queryKey: ['standards', selectedProjectId ?? null, { page: 1, pageSize: 100 }],
    queryFn: () => listStandards({ projectId: selectedProjectId, page: 1, pageSize: 100 }),
    enabled: selectedProjectId !== undefined,
  });
  const numericStandards = (standardsQuery.data?.records ?? []).filter((standard) => standard.valueType === 'NUMERIC');

  const summaryQuery = useQuery({
    queryKey: ['dashboard', 'summary', filters],
    queryFn: () => getSummary(filters),
  });
  const trendQuery = useQuery({
    queryKey: ['dashboard', 'pass-rate-trend', filters],
    queryFn: () => getPassRateTrend(filters),
  });
  const processQuery = useQuery({
    queryKey: ['dashboard', 'process-comparison', filters],
    queryFn: () => getProcessComparison(filters),
  });
  const paretoQuery = useQuery({
    queryKey: ['dashboard', 'pareto', filters],
    queryFn: () => getPareto(filters),
  });
  const gradeQuery = useQuery({
    queryKey: ['dashboard', 'defect-grade', filters],
    queryFn: () => getDefectGrade(filters),
  });
  const workloadQuery = useQuery({
    queryKey: ['dashboard', 'inspector-workload', filters],
    queryFn: () => getInspectorWorkload(filters),
  });
  const durationQuery = useQuery({
    queryKey: ['dashboard', 'defect-duration', filters],
    queryFn: () => getDefectDuration(filters),
  });
  const batchGaugeQuery = useQuery({
    queryKey: ['dashboard', 'batch-gauge', filters],
    queryFn: () => getBatchGauge(filters),
  });
  // 未选检验项时不发请求：接口只接受 standardId，没有默认全量口径
  const controlQuery = useQuery({
    queryKey: ['dashboard', 'control-chart', standardId],
    queryFn: () => getControlChart(Number(standardId)),
    enabled: standardId !== '',
  });

  const summary = summaryQuery.data;
  const trend = trendQuery.data;
  const processRows = processQuery.data;
  const paretoRows = paretoQuery.data;
  const gradeRows = gradeQuery.data;
  const workloadRows = workloadQuery.data;
  const durationRows = durationQuery.data;
  const gaugeRows = batchGaugeQuery.data ?? [];
  const controlChart = controlQuery.data;

  // 仪表盘默认显示第一条批次；选中项在刷新后不存在时回落到第一条
  const selectedGauge = gaugeRows.find((row) => String(row.batchId) === gaugeBatchId) ?? gaugeRows[0];

  // option 必须稳定：EChart 依赖它的身份决定是否整体重设图表
  const trendChart = useMemo(() => (trend && trend.length > 0 ? trendOption(trend) : null), [trend]);
  const processChart = useMemo(
    () => (processRows && processRows.length > 0 ? processOption(processRows) : null),
    [processRows],
  );
  const paretoChart = useMemo(
    () => (paretoRows && paretoRows.length > 0 ? paretoOption(paretoRows) : null),
    [paretoRows],
  );
  const gradeChart = useMemo(() => (gradeRows && gradeRows.length > 0 ? gradeOption(gradeRows) : null), [gradeRows]);
  const controlChartOption = useMemo(
    () => (controlChart && controlChart.points.length > 0 ? controlOption(controlChart) : null),
    [controlChart],
  );
  const workloadChart = useMemo(
    () => (workloadRows && workloadRows.length > 0 ? workloadOption(workloadRows) : null),
    [workloadRows],
  );
  const durationChart = useMemo(
    () => (durationRows && durationRows.length > 0 ? durationOption(durationRows) : null),
    [durationRows],
  );
  const gaugeChart = useMemo(() => (selectedGauge ? gaugeOption(selectedGauge) : null), [selectedGauge]);

  const projectOptions = [
    { value: '', label: '全部项目' },
    ...projects.map((project) => ({ value: String(project.id), label: project.projectName })),
  ];
  const batchOptions = [
    { value: '', label: '全部批次' },
    ...batches.map((batch) => ({ value: String(batch.id), label: batch.batchNo })),
  ];
  const standardOptions = [
    { value: '', label: '请选择检验项' },
    ...numericStandards.map((standard) => ({ value: String(standard.id), label: standard.itemName })),
  ];
  const gaugeOptions = gaugeRows.map((row) => ({ value: String(row.batchId), label: row.batchNo }));

  return (
    <Stack spacing={3}>
      <Stack direction="row" spacing={2} useFlexGap sx={{ flexWrap: 'wrap', alignItems: 'center' }}>
        <Typography variant="h3" component="h1">
          质量看板
        </Typography>
        {isAdmin && (
          <Button
            variant="contained"
            sx={{ marginLeft: 'auto' }}
            onClick={() => {
              seedMutation.reset();
              setSeedDialogOpen(true);
            }}
          >
            初始化演示数据
          </Button>
        )}
      </Stack>
      <Stack direction="row" spacing={2} useFlexGap sx={{ flexWrap: 'wrap', alignItems: 'flex-start' }}>
        <LabeledSelect
          label="项目"
          value={projectId}
          options={projectOptions}
          onChange={(value) => {
            // 换项目后旧批次与旧检验项没有意义
            setProjectId(value);
            setBatchId('');
            setStandardId('');
          }}
        />
        <LabeledSelect
          label="批次"
          value={batchId}
          options={batchOptions}
          disabled={projectId === ''}
          onChange={setBatchId}
        />
        <LabeledSelect
          label="检验项"
          value={standardId}
          options={standardOptions}
          disabled={projectId === ''}
          onChange={setStandardId}
        />
        <LabeledField label="开始日期" type="date" value={from} onChange={setFrom} />
        <LabeledField label="结束日期" type="date" value={to} onChange={setTo} />
      </Stack>
      {summaryQuery.error && <Alert severity="error">{errorMessage(summaryQuery.error)}</Alert>}
      <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', lg: 'repeat(4, 1fr)' } }}>
        <MetricCard title="记录总数" value={summary?.totalRecords ?? '—'} />
        <MetricCard title="合格率" value={summary ? `${summary.passRate}%` : '—'} />
        <MetricCard title="未关闭缺陷" value={summary?.openDefects ?? '—'} />
        {/* 没有已关闭缺陷时后端给 null：显示破折号，不能显示 0 小时 */}
        <MetricCard
          title="平均处理时长"
          value={summary?.avgHandleHours == null ? '—' : `${summary.avgHandleHours} 小时`}
        />
      </Box>
      <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', lg: '1fr 1fr' } }}>
        <ChartCard
          title="每日合格率趋势"
          option={trendChart}
          loading={trendQuery.isPending}
          error={trendQuery.error ? errorMessage(trendQuery.error) : null}
        />
        <ChartCard
          title="工序合格率对比"
          option={processChart}
          loading={processQuery.isPending}
          error={processQuery.error ? errorMessage(processQuery.error) : null}
        />
        <ChartCard
          title="缺陷帕累托"
          option={paretoChart}
          loading={paretoQuery.isPending}
          error={paretoQuery.error ? errorMessage(paretoQuery.error) : null}
        />
        <ChartCard
          title="缺陷等级构成"
          option={gradeChart}
          loading={gradeQuery.isPending}
          error={gradeQuery.error ? errorMessage(gradeQuery.error) : null}
        />
        <ChartCard
          title="参数控制图"
          option={controlChartOption}
          // 未选检验项时查询是 disabled，isPending 恒为 true；不能当成加载中
          loading={standardId !== '' && controlQuery.isPending}
          error={controlQuery.error ? errorMessage(controlQuery.error) : null}
          // 未选检验项不是「没数据」，是「还没选」
          emptyText={standardId === '' ? '请选择检验项' : '暂无数据'}
        />
        <ChartCard
          title="检验员工作量"
          option={workloadChart}
          loading={workloadQuery.isPending}
          error={workloadQuery.error ? errorMessage(workloadQuery.error) : null}
        />
        <ChartCard
          title="缺陷处理时长"
          option={durationChart}
          loading={durationQuery.isPending}
          error={durationQuery.error ? errorMessage(durationQuery.error) : null}
        />
        <ChartCard
          title="批次合格率"
          option={gaugeChart}
          loading={batchGaugeQuery.isPending}
          error={batchGaugeQuery.error ? errorMessage(batchGaugeQuery.error) : null}
        />
      </Box>
      <Paper sx={{ boxShadow: shadow.card, p: 3 }}>
        <Stack spacing={2}>
          <Typography variant="h3" component="h2">
            批次合格率汇总
          </Typography>
          <LabeledSelect
            label="仪表盘批次"
            value={selectedGauge ? String(selectedGauge.batchId) : ''}
            options={gaugeOptions}
            onChange={setGaugeBatchId}
          />
          <TableContainer>
            <Table aria-label="批次合格率汇总">
              <TableHead sx={{ bgcolor: 'background.default' }}>
                <TableRow>
                  <TableCell>批号</TableCell>
                  <TableCell>记录数</TableCell>
                  <TableCell>合格数</TableCell>
                  <TableCell>批次合格率</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {batchGaugeQuery.error && (
                  <TableRow>
                    <TableCell colSpan={4}>
                      <Alert severity="error">{errorMessage(batchGaugeQuery.error)}</Alert>
                    </TableCell>
                  </TableRow>
                )}
                {batchGaugeQuery.isPending && (
                  <TableRow>
                    <TableCell colSpan={4} align="center">
                      加载中…
                    </TableCell>
                  </TableRow>
                )}
                {gaugeRows.map((row) => (
                  <TableRow key={row.batchId}>
                    <TableCell>{row.batchNo}</TableCell>
                    <TableCell>{row.total}</TableCell>
                    <TableCell>{row.passed}</TableCell>
                    <TableCell>{row.passRate}%</TableCell>
                  </TableRow>
                ))}
                {batchGaugeQuery.isSuccess && gaugeRows.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4} align="center">
                      没有批次数据
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </Stack>
      </Paper>
      {isAdmin && (
        <ConfirmDialog
          open={seedDialogOpen}
          title="初始化演示数据"
          content="将创建 2 个项目、6 个批次、360 条校验记录，仅用于演示"
          confirmLabel="开始生成"
          onCancel={() => setSeedDialogOpen(false)}
          onConfirm={() => seedMutation.mutate()}
          pending={seedMutation.isPending}
          error={seedMutation.error ? errorMessage(seedMutation.error) : null}
        />
      )}
      <Snackbar
        open={seedMessage !== null}
        autoHideDuration={5000}
        onClose={() => setSeedMessage(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity="success" variant="filled" onClose={() => setSeedMessage(null)}>
          {seedMessage}
        </Alert>
      </Snackbar>
    </Stack>
  );
}
