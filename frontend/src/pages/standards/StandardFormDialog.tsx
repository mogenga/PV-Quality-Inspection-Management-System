import { useState } from 'react';
import type { FormEvent } from 'react';
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack } from '@mui/material';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createStandard, updateStandard } from '../../api/standards';
import type { UpdateStandardRequest } from '../../api/standards';
import { ApiError } from '../../api/client';
import type { JudgeType, Scene, StandardSummary } from '../../api/types';
import { JUDGE_TYPE_LABELS, SCENE_LABELS } from '../../utils/labels';
import { findMissing, missingMessage } from '../../utils/validation';
import FormErrorSnackbar from '../../components/FormErrorSnackbar';
import LabeledField from '../../components/LabeledField';
import LabeledSelect from '../../components/LabeledSelect';

const SCENE_OPTIONS: { value: Scene; label: string }[] = (Object.keys(SCENE_LABELS) as Scene[]).map((scene) => ({
  value: scene,
  label: SCENE_LABELS[scene],
}));

const JUDGE_TYPE_OPTIONS: { value: JudgeType; label: string }[] = (Object.keys(JUDGE_TYPE_LABELS) as JudgeType[]).map(
  (judgeType) => ({ value: judgeType, label: JUDGE_TYPE_LABELS[judgeType] }),
);

const ENABLED_OPTIONS: { value: 'true' | 'false'; label: string }[] = [
  { value: 'true', label: '启用' },
  { value: 'false', label: '停用' },
];

// 可选字段留空即清空：后端 updateStrategy=ALWAYS，必须显式送 null 才能真的擦掉旧值
function optional(value: string): string | null {
  const trimmed = value.trim();
  return trimmed === '' ? null : trimmed;
}

function numberOrNull(value: string): number | null {
  return value.trim() === '' ? null : Number(value);
}

// 值类型不单独给控件：人工判定录的是文字结论，其余判定都比数值
function valueTypeFor(judgeType: JudgeType) {
  return judgeType === 'PASS_FAIL' ? 'TEXT' : 'NUMERIC';
}

/** 判定方式决定哪几个数值列必填；返回 [字段名, 标签] 供错误提示拼中文。 */
function requiredNumerics(judgeType: JudgeType): [string, string][] {
  switch (judgeType) {
    case 'GT':
      return [['lowerLimit', '下限']];
    case 'LT':
      return [['upperLimit', '上限']];
    case 'RANGE':
      return [
        ['lowerLimit', '下限'],
        ['upperLimit', '上限'],
      ];
    case 'STD_TOL':
      return [
        ['targetValue', '标准值'],
        ['tolerance', '公差'],
      ];
    default:
      return [];
  }
}

/**
 * 新增/编辑校验标准。编辑时不发 projectId——标准不允许换项目，
 * 检验记录里冗余存了 project_id，换项目会让历史数据错位。
 *
 * 数值字段按判定方式条件渲染，提交时也按判定方式显式归零：
 * 用户先填了上限再改成「大于」，请求体里不能残留上限。
 */
export default function StandardFormDialog({
  projectId,
  standard,
  onClose,
}: {
  projectId: number;
  standard: StandardSummary | null;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [itemCode, setItemCode] = useState(standard?.itemCode ?? '');
  const [itemName, setItemName] = useState(standard?.itemName ?? '');
  const [scene, setScene] = useState<Scene>(standard?.scene ?? 'IQC');
  const [processName, setProcessName] = useState(standard?.processName ?? '');
  const [judgeType, setJudgeType] = useState<JudgeType>(standard?.judgeType ?? 'GT');
  const [lowerLimit, setLowerLimit] = useState(standard?.lowerLimit?.toString() ?? '');
  const [upperLimit, setUpperLimit] = useState(standard?.upperLimit?.toString() ?? '');
  const [targetValue, setTargetValue] = useState(standard?.targetValue?.toString() ?? '');
  const [tolerance, setTolerance] = useState(standard?.tolerance?.toString() ?? '');
  const [unit, setUnit] = useState(standard?.unit ?? '');
  const [samplingNote, setSamplingNote] = useState(standard?.samplingNote ?? '');
  const [sortOrder, setSortOrder] = useState(String(standard?.sortOrder ?? 0));
  const [enabled, setEnabled] = useState<'true' | 'false'>(standard?.isEnabled === false ? 'false' : 'true');
  const [remark, setRemark] = useState(standard?.remark ?? '');
  const [error, setError] = useState<string | null>(null);
  // 前端先判必填：把「哪个字段没填」说清楚，并用错误边框标在对应输入框上
  const [invalid, setInvalid] = useState<string[]>([]);

  const mutation = useMutation({
    mutationFn: (fields: UpdateStandardRequest) =>
      standard ? updateStandard(standard.id, fields) : createStandard({ projectId, ...fields }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['standards'] });
      onClose();
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : '网络异常，请稍后重试'),
  });

  const isInvalid = (field: string) => invalid.includes(field);

  /** 必填项为空时返回中文提示；全部有值时返回 null。 */
  const firstMissingField = (): string | null => {
    const numericState: Record<string, string> = {
      lowerLimit,
      upperLimit,
      targetValue,
      tolerance,
    };
    const missing = findMissing([
      { field: 'itemCode', label: '检验项编码', value: itemCode },
      { field: 'itemName', label: '检验项名称', value: itemName },
      { field: 'processName', label: '工序', value: processName },
      ...requiredNumerics(judgeType).map(([field, label]) => ({ field, label, value: numericState[field] })),
    ]);
    setInvalid(missing.map((item) => item.field));
    return missingMessage(missing);
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    const missing = firstMissingField();
    if (missing) {
      setError(missing);
      return;
    }
    const usesLower = judgeType === 'GT' || judgeType === 'RANGE';
    const usesUpper = judgeType === 'LT' || judgeType === 'RANGE';
    const usesTarget = judgeType === 'STD_TOL';
    mutation.mutate({
      scene,
      processName: processName.trim(),
      itemCode: itemCode.trim(),
      itemName: itemName.trim(),
      unit: optional(unit),
      valueType: valueTypeFor(judgeType),
      judgeType,
      lowerLimit: usesLower ? numberOrNull(lowerLimit) : null,
      upperLimit: usesUpper ? numberOrNull(upperLimit) : null,
      targetValue: usesTarget ? numberOrNull(targetValue) : null,
      tolerance: usesTarget ? numberOrNull(tolerance) : null,
      samplingNote: optional(samplingNote),
      isEnabled: enabled === 'true',
      sortOrder: sortOrder.trim() === '' ? 0 : Number(sortOrder),
      remark: optional(remark),
    });
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <Box component="form" onSubmit={handleSubmit}>
        <DialogTitle>{standard ? '编辑标准' : '新增标准'}</DialogTitle>
        <DialogContent>
          <Stack spacing={3} sx={{ pt: 1 }}>
            <LabeledField
              label="检验项编码"
              value={itemCode}
              onChange={setItemCode}
              required
              error={isInvalid('itemCode')}
            />
            <LabeledField
              label="检验项名称"
              value={itemName}
              onChange={setItemName}
              required
              error={isInvalid('itemName')}
            />
            <LabeledSelect label="场景" value={scene} options={SCENE_OPTIONS} onChange={setScene} />
            <LabeledField
              label="工序"
              value={processName}
              onChange={setProcessName}
              required
              error={isInvalid('processName')}
            />
            <LabeledSelect label="判定方式" value={judgeType} options={JUDGE_TYPE_OPTIONS} onChange={setJudgeType} />
            {(judgeType === 'GT' || judgeType === 'RANGE') && (
              <LabeledField
                label="下限"
                type="number"
                step="any"
                value={lowerLimit}
                onChange={setLowerLimit}
                required
                error={isInvalid('lowerLimit')}
              />
            )}
            {(judgeType === 'LT' || judgeType === 'RANGE') && (
              <LabeledField
                label="上限"
                type="number"
                step="any"
                value={upperLimit}
                onChange={setUpperLimit}
                required
                error={isInvalid('upperLimit')}
              />
            )}
            {judgeType === 'STD_TOL' && (
              <>
                <LabeledField
                  label="标准值"
                  type="number"
                  step="any"
                  value={targetValue}
                  onChange={setTargetValue}
                  required
                  error={isInvalid('targetValue')}
                />
                <LabeledField
                  label="公差"
                  type="number"
                  step="any"
                  value={tolerance}
                  onChange={setTolerance}
                  required
                  error={isInvalid('tolerance')}
                />
              </>
            )}
            <LabeledField label="单位" value={unit} onChange={setUnit} />
            <LabeledField label="抽样说明" value={samplingNote} onChange={setSamplingNote} />
            <LabeledField label="排序" type="number" value={sortOrder} onChange={setSortOrder} />
            <LabeledSelect label="启用状态" value={enabled} options={ENABLED_OPTIONS} onChange={setEnabled} />
            <LabeledField label="备注" value={remark} onChange={setRemark} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>取消</Button>
          <Button type="submit" variant="contained" disabled={mutation.isPending}>
            确定
          </Button>
        </DialogActions>
        {/* 浮动在视口底部：点「确定」时人在页面下方，提示不能渲染在可滚动内容的顶部 */}
        {/* 浮动在视口底部：点「确定」时人在页面下方，提示不能渲染在可滚动内容的顶部 */}
        <FormErrorSnackbar error={error} onClose={() => setError(null)} />
      </Box>
    </Dialog>
  );
}
