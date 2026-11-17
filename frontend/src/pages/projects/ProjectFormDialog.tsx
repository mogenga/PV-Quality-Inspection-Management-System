import { useState } from 'react';
import type { FormEvent } from 'react';
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack } from '@mui/material';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createProject, updateProject } from '../../api/projects';
import { ApiError } from '../../api/client';
import type { ProjectStatus, ProjectSummary } from '../../api/types';
import type { ProjectRequest } from '../../api/projects';
import { PROJECT_STATUS_LABELS } from '../../utils/labels';
import LabeledField from '../../components/LabeledField';
import LabeledSelect from '../../components/LabeledSelect';

const STATUS_OPTIONS: { value: ProjectStatus; label: string }[] = (
  Object.keys(PROJECT_STATUS_LABELS) as ProjectStatus[]
).map((status) => ({ value: status, label: PROJECT_STATUS_LABELS[status] }));

// 可选字段留空即清空：后端 updateStrategy=ALWAYS，必须显式送 null 才能真的擦掉旧值
function optional(value: string): string | null {
  const trimmed = value.trim();
  return trimmed === '' ? null : trimmed;
}

/**
 * 新增/编辑项目。编辑时从传入行预填全部字段，提交整行对象——
 * Project 的可空列是 FieldStrategy.ALWAYS，稀疏更新会把未提交的列写成 NULL。
 */
export default function ProjectFormDialog({
  project,
  onClose,
}: {
  project: ProjectSummary | null;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [projectCode, setProjectCode] = useState(project?.projectCode ?? '');
  const [projectName, setProjectName] = useState(project?.projectName ?? '');
  const [productModel, setProductModel] = useState(project?.productModel ?? '');
  const [customerName, setCustomerName] = useState(project?.customerName ?? '');
  const [manufacturerName, setManufacturerName] = useState(project?.manufacturerName ?? '');
  const [location, setLocation] = useState(project?.location ?? '');
  const [status, setStatus] = useState<ProjectStatus>(project?.status ?? 'ACTIVE');
  const [remark, setRemark] = useState(project?.remark ?? '');
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (req: ProjectRequest) => (project ? updateProject(project.id, req) : createProject(req)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects'] });
      onClose();
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : '网络异常，请稍后重试'),
  });

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    mutation.mutate({
      projectCode: projectCode.trim(),
      projectName: projectName.trim(),
      productModel: optional(productModel),
      customerName: optional(customerName),
      manufacturerName: optional(manufacturerName),
      location: optional(location),
      status,
      remark: optional(remark),
    });
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <Box component="form" onSubmit={handleSubmit}>
        <DialogTitle>{project ? '编辑项目' : '新增项目'}</DialogTitle>
        <DialogContent>
          <Stack spacing={3} sx={{ pt: 1 }}>
            {error && <Alert severity="error">{error}</Alert>}
            <LabeledField label="项目编号" value={projectCode} onChange={setProjectCode} />
            <LabeledField label="项目名称" value={projectName} onChange={setProjectName} />
            <LabeledField label="组件型号" value={productModel} onChange={setProductModel} />
            <LabeledField label="客户" value={customerName} onChange={setCustomerName} />
            <LabeledField label="制造商" value={manufacturerName} onChange={setManufacturerName} />
            <LabeledField label="项目地点" value={location} onChange={setLocation} />
            <LabeledSelect label="项目状态" value={status} options={STATUS_OPTIONS} onChange={setStatus} />
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
