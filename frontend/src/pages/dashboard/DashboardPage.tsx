import { useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Box, Paper, Stack, Typography } from '@mui/material';
import type { EChartsOption } from 'echarts';
import { useQuery } from '@tanstack/react-query';
import { ApiError } from '../../api/client';
import { listBatches } from '../../api/batches';
import { getPassRateTrend, getProcessComparison, getSummary } from '../../api/dashboard';
import { listProjects } from '../../api/projects';
import { shadow } from '../../theme/muiTheme';
import EChart from '../../components/EChart';
import LabeledField from '../../components/LabeledField';
import LabeledSelect from '../../components/LabeledSelect';
import { processOption, trendOption } from './options';

function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : '网络异常，请稍后重试';
}

/** 指标卡：标题 + 数值。 */
function MetricCard({ title, value }: { title: string; value: ReactNode }) {
  return (
    <Paper sx={{ boxShadow: shadow.card, p: 3 }}>
      <Stack spacing={1}>
        <Typography variant="body2" color="text.secondary">
          {title}
        </Typography>
        <Typography variant="h2">{value}</Typography>
      </Stack>
    </Paper>
  );
}

/**
 * 图表卡片外壳：标题 + 图。`option === null` 表示后端没有数据，
 * 显示空状态而不是空白坐标轴（SRS 6.3）。Task 5 的其余六张图复用。
 */
function ChartCard({
  title,
  option,
  loading = false,
  emptyText = '暂无数据',
}: {
  title: string;
  option: EChartsOption | null;
  loading?: boolean;
  emptyText?: string;
}) {
  return (
    <Paper sx={{ boxShadow: shadow.card, p: 3 }}>
      <Stack spacing={2}>
        <Typography variant="h3" component="h2">
          {title}
        </Typography>
        {loading ? (
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
 * 质量看板（FR-14、P6）。筛选条件对同一页上的每张图与指标卡联动；
 * 本任务接入 CH-01 趋势与 CH-03 工序对比，其余六张由 Task 5 追加。
 */
export default function DashboardPage() {
  // 下拉持有字符串（LabeledSelect 只吃 string），发请求时才转成数字
  const [projectId, setProjectId] = useState('');
  const [batchId, setBatchId] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');

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

  const summary = summaryQuery.data;
  const trend = trendQuery.data;
  const processRows = processQuery.data;

  // option 必须稳定：EChart 依赖它的身份决定是否整体重设图表
  const trendChart = useMemo(() => (trend && trend.length > 0 ? trendOption(trend) : null), [trend]);
  const processChart = useMemo(
    () => (processRows && processRows.length > 0 ? processOption(processRows) : null),
    [processRows],
  );

  const projectOptions = [
    { value: '', label: '全部项目' },
    ...projects.map((project) => ({ value: String(project.id), label: project.projectName })),
  ];
  const batchOptions = [
    { value: '', label: '全部批次' },
    ...batches.map((batch) => ({ value: String(batch.id), label: batch.batchNo })),
  ];

  return (
    <Stack spacing={3}>
      <Typography variant="h3" component="h1">
        质量看板
      </Typography>
      <Stack direction="row" spacing={2} useFlexGap sx={{ flexWrap: 'wrap', alignItems: 'flex-start' }}>
        <LabeledSelect
          label="项目"
          value={projectId}
          options={projectOptions}
          onChange={(value) => {
            // 换项目后旧批次没有意义
            setProjectId(value);
            setBatchId('');
          }}
        />
        <LabeledSelect
          label="批次"
          value={batchId}
          options={batchOptions}
          disabled={projectId === ''}
          onChange={setBatchId}
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
        <ChartCard title="每日合格率趋势" option={trendChart} loading={trendQuery.isPending} />
        <ChartCard title="工序合格率对比" option={processChart} loading={processQuery.isPending} />
      </Box>
    </Stack>
  );
}
